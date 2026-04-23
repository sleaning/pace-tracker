package com.pacetrack.ui.home

import com.pacetrack.data.model.User
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pacetrack.data.model.Run
import com.pacetrack.data.model.repository.RunRepository
import com.pacetrack.data.model.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeFeedItem(
    val run: Run,
    val athlete: User?
)

/**
 * ViewModel for the Home feed page.
 * It joins the current user's following list with the run repository so the
 * UI can render a social feed without talking to Firebase directly.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val runRepository: RunRepository
) : ViewModel() {
    companion object {
        private const val FEED_REFRESH_COOLDOWN_MS = 60_000L
        private const val FEED_TOTAL_LIMIT = 50
        private const val SELF_FEED_LIMIT = 20L
    }

    private val _feedItems = MutableStateFlow<List<HomeFeedItem>>(emptyList())
    val feedItems: StateFlow<List<HomeFeedItem>> = _feedItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private var lastLoadAttemptAtMs: Long = 0L
    private var pendingFeedRefresh = true

    init {
        observeFollowingChanges()
        loadFeed(force = true)
    }

    /**
     * Loads activity for the signed-in user and the accounts they follow.
     * Recent self activity is always included, while followed athletes'
     * activity stays public-only through the repository/service layer.
     */
    fun loadFeed(force: Boolean = false) {
        if (_isLoading.value) return

        val now = System.currentTimeMillis()
        val cooldownElapsed = now - lastLoadAttemptAtMs >= FEED_REFRESH_COOLDOWN_MS
        if (!force && !pendingFeedRefresh && !cooldownElapsed) return

        lastLoadAttemptAtMs = now
        pendingFeedRefresh = false
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser()
                if (user == null) {
                    _feedItems.value = emptyList()
                    _errorMessage.value = "User not authenticated"
                    pendingFeedRefresh = true
                    return@launch
                }

                val selfRunsResult = runCatching {
                    runRepository.getRecentRuns(user.id, SELF_FEED_LIMIT)
                }
                val followingRunsResult: Result<List<Run>> = if (user.following.isNotEmpty()) {
                    runCatching { runRepository.getFollowingRuns(user.following) }
                } else {
                    Result.success(emptyList())
                }

                val mergedRuns = (selfRunsResult.getOrDefault(emptyList()) + followingRunsResult.getOrDefault(emptyList()))
                    .sortedByDescending { it.startTime }
                    .distinctBy { it.id }
                    .take(FEED_TOTAL_LIMIT)

                val athletesById = loadAthletesById(currentUser = user, runs = mergedRuns)
                _feedItems.value = mergedRuns.map { run ->
                    HomeFeedItem(
                        run = run,
                        athlete = athletesById[run.userId]
                    )
                }
                _errorMessage.value = when {
                    selfRunsResult.isFailure && followingRunsResult.isFailure ->
                        buildFeedErrorMessage(selfRunsResult.exceptionOrNull(), followingRunsResult.exceptionOrNull())
                    followingRunsResult.isFailure && mergedRuns.isNotEmpty() ->
                        "Showing your runs while friend activity reloads."
                    selfRunsResult.isFailure && mergedRuns.isNotEmpty() ->
                        "Showing friend activity while your runs reload."
                    else -> null
                }
                pendingFeedRefresh = selfRunsResult.isFailure || followingRunsResult.isFailure
            } catch (e: Exception) {
                _feedItems.value = emptyList()
                _errorMessage.value = e.message ?: "Failed to load activity feed"
                pendingFeedRefresh = true
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Refreshes the feed when the screen comes back into view, but only if the
     * last fetch attempt is stale or social graph changes marked it dirty.
     */
    fun refreshIfNeeded() {
        loadFeed()
    }

    private fun observeFollowingChanges() {
        viewModelScope.launch {
            userRepository.followingVersion
                .drop(1)
                .collect {
                    pendingFeedRefresh = true
                }
        }
    }

    private fun buildFeedErrorMessage(
        selfError: Throwable?,
        followingError: Throwable?
    ): String {
        val message = followingError?.message ?: selfError?.message
        return message ?: "Failed to load activity feed"
    }

    private suspend fun loadAthletesById(
        currentUser: User,
        runs: List<Run>
    ): Map<String, User?> = coroutineScope {
        runs.map { it.userId }
            .distinct()
            .filter { it.isNotBlank() }
            .associateWith { userId ->
                async {
                    when (userId) {
                        currentUser.id -> currentUser
                        else -> runCatching { userRepository.getUser(userId) }.getOrNull()
                    }
                }
            }
            .mapValues { (_, athleteDeferred) -> athleteDeferred.await() }
    }
}

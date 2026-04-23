package com.pacetrack.data.model.repository

import com.google.firebase.auth.FirebaseAuth
import com.pacetrack.data.model.User
import com.pacetrack.data.model.remote.FirestoreService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for user profiles and follow relationships.
 * It combines authenticated user context with Firestore operations so the
 * rest of the app can ask social questions without handling auth plumbing.
 */
@Singleton
class UserRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val auth: FirebaseAuth
) {
    private val currentUserId get() = auth.currentUser?.uid ?: ""
    private val _followingVersion = MutableStateFlow(0L)
    val followingVersion: StateFlow<Long> = _followingVersion.asStateFlow()
    
    suspend fun createUser(user: User) {
        firestoreService.saveUser(user)
    }

    suspend fun getUser(userId: String): User? = firestoreService.getUser(userId)
    
    /**
     * Returns the signed-in user's profile document when available.
     * Auth can be temporarily missing during startup or sign-out, so the
     * helper safely returns null instead of issuing an invalid query.
     */
    suspend fun getCurrentUser(): User? = if (currentUserId.isNotEmpty()) {
        firestoreService.getUser(currentUserId)
    } else {
        null
    }

    /**
     * Searches users by name but removes the current user from results.
     * That filter keeps the Profile page focused on social discovery instead
     * of letting people follow or unfollow themselves.
     */
    suspend fun searchUsers(query: String): List<User> =
        firestoreService.searchUsers(query).filter { it.id != currentUserId }

    /**
     * Adds a target user to the signed-in user's following list.
     * The repository quietly no-ops if auth is missing because follow actions
     * only make sense for an authenticated profile.
     */
    suspend fun followUser(targetUserId: String) {
        if (currentUserId.isNotEmpty()) {
            firestoreService.followUser(currentUserId, targetUserId)
            _followingVersion.value = System.currentTimeMillis()
        }
    }

    /**
     * Removes a target user from the signed-in user's following list.
     * Firestore handles the array mutation atomically so the app does not
     * need to read-modify-write the full following list itself.
     */
    suspend fun unfollowUser(targetUserId: String) {
        if (currentUserId.isNotEmpty()) {
            firestoreService.unfollowUser(currentUserId, targetUserId)
            _followingVersion.value = System.currentTimeMillis()
        }
    }

    /**
     * Checks whether the current user is already following a target user.
     * This powers button labeling on the profile search results.
     */
    suspend fun isFollowing(targetUserId: String): Boolean {
        val user = getCurrentUser() ?: return false
        return targetUserId in user.following
    }
}

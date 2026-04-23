package com.pacetrack.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pacetrack.data.model.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/*
 * Shared authentication state holder for the app's auth flow.
 * It validates form input, translates repository results into UI-friendly
 * state, and gives all auth screens one consistent source of truth.
 */
/**
 * Represents the different states of the auth flow.
 * Used by SignInScreen, SignUpScreen, and SplashScreen to react to
 * loading, error, and success states without managing raw Firebase calls.
 */
sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

/**
 * Single ViewModel shared by SignIn, SignUp, and Splash screens.
 * It owns auth form state transitions so the screens can stay declarative,
 * and it survives rotation while Firebase calls are in flight.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Used by SplashScreen to decide Home vs SignIn. */
    fun isSignedIn(): Boolean = repository.isSignedIn()

    /**
     * Attempts a Firebase sign-in after basic client-side validation.
     * The repository throws raw failures, and this layer converts them into
     * states that Compose screens can render without parsing exceptions.
     */
    fun signIn(email: String, password: String) {
        if (!validate(email, password)) return

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                repository.signIn(email.trim(), password)
                _uiState.value = AuthUiState.Success
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(humanize(e))
            }
        }
    }

    /**
     * Creates a new account and its paired user profile record.
     * Display name is validated here because it belongs to the app-level
     * profile, while email/password handling stays aligned with sign-in.
     */
    fun signUp(email: String, password: String, displayName: String) {
        if (!validate(email, password)) return
        if (displayName.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter a display name")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                repository.signUp(email.trim(), password, displayName.trim())
                _uiState.value = AuthUiState.Success
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(humanize(e))
            }
        }
    }

    fun signOut() {
        repository.signOut()
        _uiState.value = AuthUiState.Idle
    }

    /** Resets state back to Idle. Called after UI consumes a Success/Error. */
    fun clearState() {
        _uiState.value = AuthUiState.Idle
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Blocks obvious invalid submissions before a network round-trip.
     * This keeps Firebase traffic down and gives the user instant feedback
     * for common form mistakes like malformed emails or short passwords.
     */
    private fun validate(email: String, password: String): Boolean {
        if (email.isBlank() || !email.contains("@")) {
            _uiState.value = AuthUiState.Error("Please enter a valid email")
            return false
        }
        if (password.length < 6) {
            _uiState.value = AuthUiState.Error("Password must be at least 6 characters")
            return false
        }
        return true
    }

    /** Converts raw Firebase exceptions into friendly messages for the UI. */
    private fun humanize(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("password is invalid", ignoreCase = true) ||
                    msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                    msg.contains("invalid credentials", ignoreCase = true) ->
                "Incorrect email or password"
            msg.contains("no user record", ignoreCase = true) ->
                "No account found for this email"
            msg.contains("email address is already", ignoreCase = true) ->
                "An account with this email already exists"
            msg.contains("network", ignoreCase = true) ->
                "Network error — check your connection"
            msg.contains("badly formatted", ignoreCase = true) ->
                "Please enter a valid email"
            else -> msg.ifBlank { "Something went wrong. Please try again." }
        }
    }
}

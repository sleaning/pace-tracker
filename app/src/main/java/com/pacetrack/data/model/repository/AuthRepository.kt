package com.pacetrack.data.model.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.pacetrack.data.model.User
import com.pacetrack.data.model.withNormalizedSearchName
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuthRepository
 *
 * Wraps FirebaseAuth and the users/{uid} Firestore document.
 * AuthViewModel depends on this — never on FirebaseAuth directly —
 * keeping the ViewModel testable and architecture consistent (MVVM + Repository).
 *
 * All network calls are suspend functions and throw on failure;
 * AuthViewModel catches and surfaces the message to the UI.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    /** Currently signed-in user, or null if signed out. */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /** True if a user is currently signed in. Used by SplashScreen routing. */
    fun isSignedIn(): Boolean = auth.currentUser != null

    /**
     * Signs a user in with email + password.
     * Throws FirebaseAuthException on bad credentials, network error, etc.
     */
    suspend fun signIn(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user ?: error("Sign in succeeded but user is null")
    }

    /**
     * Creates a new auth account AND the matching Firestore user document.
     * If the Firestore write fails, the auth account is rolled back so the
     * user can retry cleanly without a "half-created" state.
     */
    suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val firebaseUser = result.user ?: error("Sign up succeeded but user is null")

        val userDoc = User(
            id = firebaseUser.uid,
            displayName = displayName,
            username = email.substringBefore("@"), // simple default; editable later
            profilePhotoUrl = "",
            following = emptyList()
        ).withNormalizedSearchName()

        try {
            firestore.collection("users")
                .document(firebaseUser.uid)
                .set(userDoc)
                .await()
        } catch (e: Exception) {
            // Roll back so we don't leave a zombie auth account
            runCatching { firebaseUser.delete().await() }
            throw e
        }

        return firebaseUser
    }

    /** Signs the current user out. Safe to call when already signed out. */
    fun signOut() {
        auth.signOut()
    }
}

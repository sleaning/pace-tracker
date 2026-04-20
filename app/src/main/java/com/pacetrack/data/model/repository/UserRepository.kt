package com.pacetrack.data.model.repository

import com.google.firebase.auth.FirebaseAuth
import com.pacetrack.data.model.User
import com.pacetrack.data.model.remote.FirestoreService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val auth: FirebaseAuth
) {
    private val currentUserId get() = auth.currentUser?.uid ?: ""
    
    suspend fun createUser(user: User) {
        firestoreService.saveUser(user)
    }

    suspend fun getUser(userId: String): User? = firestoreService.getUser(userId)
    
    suspend fun getCurrentUser(): User? = if (currentUserId.isNotEmpty()) {
        firestoreService.getUser(currentUserId)
    } else {
        null
    }

    suspend fun searchUsers(query: String): List<User> =
        firestoreService.searchUsers(query).filter { it.id != currentUserId }

    suspend fun followUser(targetUserId: String) {
        if (currentUserId.isNotEmpty()) {
            firestoreService.followUser(currentUserId, targetUserId)
        }
    }

    suspend fun unfollowUser(targetUserId: String) {
        if (currentUserId.isNotEmpty()) {
            firestoreService.unfollowUser(currentUserId, targetUserId)
        }
    }

    suspend fun isFollowing(targetUserId: String): Boolean {
        val user = getCurrentUser() ?: return false
        return targetUserId in user.following
    }
}

package com.pacetrack.data.model.repository

import com.google.firebase.firestore.FieldValue
import com.pacetrack.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun createUser(user: User) {
        firestore.collection("users").document(user.id).set(user).await()
    }

    suspend fun getUser(userId: String): User? {
        return firestore.collection("users").document(userId)
            .get().await().toObject(User::class.java)
    }

    suspend fun followUser(currentUserId: String, targetUserId: String) {
        firestore.collection("users").document(currentUserId)
            .update("following", FieldValue.arrayUnion(targetUserId)).await()
    }

    suspend fun unfollowUser(currentUserId: String, targetUserId: String) {
        firestore.collection("users").document(currentUserId)
            .update("following", FieldValue.arrayRemove(targetUserId)).await()
    }
}
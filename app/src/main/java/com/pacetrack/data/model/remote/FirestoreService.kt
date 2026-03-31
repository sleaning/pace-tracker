package com.pacetrack.data.model.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.pacetrack.data.model.Run
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirestoreService @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun saveRun(run: Run): String {
        val doc = firestore.collection("runs").document()
        doc.set(run.copy(id = doc.id)).await()
        return doc.id
    }

    suspend fun getRuns(userId: String): List<Run> {
        return firestore.collection("runs")
            .whereEqualTo("userId", userId)
            .get().await()
            .toObjects(Run::class.java)
    }

    suspend fun getFollowingRuns(followingIds: List<String>): List<Run> {
        if (followingIds.isEmpty()) return emptyList()
        return firestore.collection("runs")
            .whereIn("userId", followingIds)
            .whereEqualTo("isPublic", true)
            .get().await()
            .toObjects(Run::class.java)
    }
}
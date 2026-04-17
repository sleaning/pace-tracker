package com.pacetrack.data.model.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pacetrack.data.model.Photo
import com.pacetrack.data.model.RoutePoint
import com.pacetrack.data.model.Run
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirestoreService @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun saveRun(run: Run): String {
        // If the caller already set an id (e.g. because photos need to reference
        // the runId before the run is saved), use it. Otherwise generate one.
        val docId = run.id.ifBlank {
            firestore.collection("runs").document().id
        }
        firestore.collection("runs")
            .document(docId)
            .set(run.copy(id = docId))
            .await()
        return docId
    }

    suspend fun getRuns(userId: String): List<Run> {
        return firestore.collection("runs")
            .whereEqualTo("userId", userId)
            .orderBy("startTime", Query.Direction.DESCENDING)
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

    suspend fun getRunById(runId: String): Run? {
        return firestore.collection("runs")
            .document(runId)
            .get().await()
            .toObject(Run::class.java)
    }

    suspend fun getRoutePoints(runId: String): List<RoutePoint> {
        return firestore.collection("runs")
            .document(runId)
            .collection("points")
            .orderBy("timestamp")
            .get().await()
            .toObjects(RoutePoint::class.java)
    }

    suspend fun getPhotosForRun(runId: String): List<Photo> {
        return firestore.collection("photos")
            .whereEqualTo("runId", runId)
            .orderBy("timestamp")
            .get().await()
            .toObjects(Photo::class.java)
    }
}
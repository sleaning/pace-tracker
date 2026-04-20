package com.pacetrack.data.model.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pacetrack.data.model.Photo
import com.pacetrack.data.model.RoutePoint
import com.pacetrack.data.model.Run
import com.pacetrack.data.model.User
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreService @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun saveRun(run: Run): String {
        val docId = run.id.ifBlank {
            firestore.collection("runs").document().id
        }
        firestore.collection("runs")
            .document(docId)
            .set(run.copy(id = docId))
            .await()
        return docId
    }

    suspend fun saveUser(user: User) {
        firestore.collection("users")
            .document(user.id)
            .set(user)
            .await()
    }

    suspend fun getRuns(userId: String): List<Run> {
        return firestore.collection("runs")
            .whereEqualTo("userId", userId)
            .orderBy("startTime", Query.Direction.DESCENDING)
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

    suspend fun getUser(userId: String): User? =
        firestore.collection("users").document(userId)
            .get().await().toObject(User::class.java)

    suspend fun searchUsers(query: String): List<User> =
        firestore.collection("users")
            .whereGreaterThanOrEqualTo("displayName", query)
            .whereLessThanOrEqualTo("displayName", query + "\uF7FF")
            .limit(20)
            .get().await()
            .toObjects(User::class.java)

    suspend fun followUser(currentUserId: String, targetUserId: String) {
        firestore.collection("users").document(currentUserId)
            .update("following", FieldValue.arrayUnion(targetUserId)).await()
    }

    suspend fun unfollowUser(currentUserId: String, targetUserId: String) {
        firestore.collection("users").document(currentUserId)
            .update("following", FieldValue.arrayRemove(targetUserId)).await()
    }

    suspend fun getFollowingRuns(followingIds: List<String>): List<Run> {
        if (followingIds.isEmpty()) return emptyList()
        return followingIds.chunked(30).flatMap { chunk ->
            firestore.collection("runs")
                .whereIn("userId", chunk)
                .whereEqualTo("isPublic", true)
                .orderBy("startTime", Query.Direction.DESCENDING)
                .limit(50)
                .get().await()
                .toObjects(Run::class.java)
        }.sortedByDescending { it.startTime }
    }
}

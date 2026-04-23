package com.pacetrack.data.model.remote

import com.google.firebase.firestore.FieldPath
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

/**
 * Low-level Firestore gateway for PaceTrack documents.
 * Repositories call this service to read and write runs, users, and photos
 * without duplicating collection names or query details throughout the app.
 */
@Singleton
class FirestoreService @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private companion object {
        const val FEED_QUERY_CHUNK_SIZE = 30
        const val FEED_BATCH_QUERY_LIMIT = 50L
        const val FEED_FALLBACK_PER_USER_LIMIT = 10L
        const val FEED_TOTAL_LIMIT = 50
    }

    /**
     * Saves a run using its existing id when present or a generated id when not.
     * Returning the final id lets callers attach related data such as photos
     * even when the document key was created inside this service.
     */
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

    /**
     * Fetches recent runs for the signed-in user's personal history tab.
     * Ordering by start time descending means the newest activities appear
     * first without any extra sorting in the UI layer.
     */
    suspend fun getRuns(userId: String): List<Run> {
        return firestore.collection("runs")
            .whereEqualTo("userId", userId)
            .orderBy("startTime", Query.Direction.DESCENDING)
            .get().await()
            .toObjects(Run::class.java)
    }

    suspend fun getRecentRuns(userId: String, limit: Long): List<Run> {
        return firestore.collection("runs")
            .whereEqualTo("userId", userId)
            .orderBy("startTime", Query.Direction.DESCENDING)
            .limit(limit)
            .get().await()
            .toObjects(Run::class.java)
    }

    suspend fun getRecentPublicRuns(limit: Long): List<Run> {
        return firestore.collection("runs")
            .whereEqualTo("public", true)
            .orderBy("startTime", Query.Direction.DESCENDING)
            .limit(limit)
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

    /**
     * Loads photo documents tied to a specific run id.
     * Results are timestamp-sorted client side so they display in the order
     * they were captured during the activity.
     */
    suspend fun getPhotosForRun(runId: String): List<Photo> {
        return firestore.collection("photos")
            .whereEqualTo("runId", runId)
            .get().await()
            .toObjects(Photo::class.java)
            .sortedBy { it.timestamp }
    }

    suspend fun getPhotosByIds(photoIds: List<String>): List<Photo> {
        if (photoIds.isEmpty()) return emptyList()

        return photoIds.chunked(30).flatMap { chunk ->
            firestore.collection("photos")
                .whereIn(FieldPath.documentId(), chunk)
                .get().await()
                .toObjects(Photo::class.java)
        }.sortedBy { it.timestamp }
    }

    suspend fun getUser(userId: String): User? =
        firestore.collection("users").document(userId)
            .get().await().toObject(User::class.java)

    /**
     * Searches users by display name prefix.
     * The upper-bound sentinel creates a starts-with query pattern within the
     * limits of Firestore range querying.
     */
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

    /**
     * Loads public runs for the ids the current user follows.
     * Firestore limits `whereIn` queries, so ids are chunked. Some projects
     * also require a composite index for the batched social query, so this
     * method falls back to small per-user recent queries that reuse the same
     * query shape as the working History screen.
     */
    suspend fun getFollowingRuns(followingIds: List<String>): List<Run> {
        val distinctIds = followingIds.distinct().filter { it.isNotBlank() }
        if (distinctIds.isEmpty()) return emptyList()

        val runs = runCatching {
            distinctIds.chunked(FEED_QUERY_CHUNK_SIZE).flatMap { chunk ->
                firestore.collection("runs")
                    .whereIn("userId", chunk)
                    .whereEqualTo("public", true)
                    .orderBy("startTime", Query.Direction.DESCENDING)
                    .limit(FEED_BATCH_QUERY_LIMIT)
                    .get().await()
                    .toObjects(Run::class.java)
            }
        }.getOrElse {
            distinctIds.flatMap { userId ->
                getRecentRuns(userId, FEED_FALLBACK_PER_USER_LIMIT)
            }
        }

        return runs
            .filter { it.isPublic }
            .sortedByDescending { it.startTime }
            .distinctBy { it.id }
            .take(FEED_TOTAL_LIMIT)
    }
}

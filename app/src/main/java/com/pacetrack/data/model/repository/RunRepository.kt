package com.pacetrack.data.model.repository


import com.pacetrack.data.model.Run
import com.pacetrack.data.model.remote.FirestoreService
import javax.inject.Inject

/**
 * Repository facade for run-related data operations.
 * It keeps view models unaware of the underlying Firestore service so data
 * access can evolve without forcing UI layers to change with it.
 */
class RunRepository @Inject constructor(
    private val firestoreService: FirestoreService
) {
    // These methods stay intentionally thin so callers get a focused run API
    // while Firestore-specific query details remain in FirestoreService.
    suspend fun saveRun(run: Run) = firestoreService.saveRun(run)
    suspend fun getRuns(userId: String) = firestoreService.getRuns(userId)
    suspend fun getFollowingRuns(followingIds: List<String>) =
        firestoreService.getFollowingRuns(followingIds)
    suspend fun getRunById(runId: String) = firestoreService.getRunById(runId)
    suspend fun getRoutePoints(runId: String) = firestoreService.getRoutePoints(runId)
    suspend fun getPhotosForRun(runId: String) = firestoreService.getPhotosForRun(runId)
    suspend fun getPhotosByIds(photoIds: List<String>) = firestoreService.getPhotosByIds(photoIds)
}

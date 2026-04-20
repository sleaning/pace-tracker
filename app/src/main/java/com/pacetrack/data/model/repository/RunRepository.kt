package com.pacetrack.data.model.repository


import com.pacetrack.data.model.Run
import com.pacetrack.data.model.remote.FirestoreService
import javax.inject.Inject

class RunRepository @Inject constructor(
    private val firestoreService: FirestoreService
) {
    suspend fun saveRun(run: Run) = firestoreService.saveRun(run)
    suspend fun getRuns(userId: String) = firestoreService.getRuns(userId)
    suspend fun getFollowingRuns(followingIds: List<String>) =
        firestoreService.getFollowingRuns(followingIds)
    suspend fun getRunById(runId: String) = firestoreService.getRunById(runId)
    suspend fun getRoutePoints(runId: String) = firestoreService.getRoutePoints(runId)
    suspend fun getPhotosForRun(runId: String) = firestoreService.getPhotosForRun(runId)

}
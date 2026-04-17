package com.pacetrack.data.model.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.pacetrack.data.model.Photo
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PhotoRepository
 *
 * Handles the full photo lifecycle:
 *  1. Upload the local image file (Uri) to Firebase Storage.
 *  2. Get back a public download URL.
 *  3. Create the Photo Firestore doc with that URL, location, and runId.
 *
 * Storage path: photos/{userId}/{photoId}
 * Firestore path: photos/{photoId}
 */
@Singleton
class PhotoRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {

    /**
     * Uploads one image and creates its Firestore document.
     * Returns the photo ID so it can be attached to the Run's photoIds list.
     *
     * If the Storage upload succeeds but Firestore fails, the uploaded file
     * is deleted to avoid orphan storage charges.
     */
    suspend fun uploadPhoto(
        context: Context,
        localUri: Uri,
        runId: String,
        userId: String,
        latitude: Double,
        longitude: Double
    ): String {
        val photoId = UUID.randomUUID().toString()
        val storageRef = storage.reference.child("photos/$userId/$photoId")

        // 1. Upload bytes to Storage
        storageRef.putFile(localUri).await()

        // 2. Grab the public download URL
        val downloadUrl = storageRef.downloadUrl.await().toString()

        // 3. Create the Firestore Photo doc
        val photo = Photo(
            id = photoId,
            runId = runId,
            userId = userId,
            imageUrl = downloadUrl,
            latitude = latitude,
            longitude = longitude,
            timestamp = System.currentTimeMillis()
        )

        try {
            firestore.collection("photos").document(photoId).set(photo).await()
        } catch (e: Exception) {
            // Rollback: delete the orphaned storage file
            runCatching { storageRef.delete().await() }
            throw e
        }

        return photoId
    }
}
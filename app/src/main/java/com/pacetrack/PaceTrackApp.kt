package com.pacetrack

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point for global app setup.
 * Hilt uses this class to create the dependency graph, and the app also
 * enables Firestore persistence here so cached data survives restarts.
 */
@HiltAndroidApp
class PaceTrackApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Local persistence keeps recently loaded runs and profiles available
        // even when connectivity is poor or the app restarts mid-session.
        FirebaseFirestore.getInstance().firestoreSettings = firestoreSettings {
            isPersistenceEnabled = true
        }
    }
}

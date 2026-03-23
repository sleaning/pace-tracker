package com.pacetrack

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PaceTrackApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseFirestore.getInstance().firestoreSettings = firestoreSettings {
            isPersistenceEnabled = true
        }
    }
}
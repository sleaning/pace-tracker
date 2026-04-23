package com.pacetrack.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.pacetrack.data.model.remote.FirestoreService
import com.pacetrack.data.model.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that wires Firebase services into the app.
 * Centralizing these providers keeps creation logic out of screens and view
 * models while ensuring shared instances are reused across the process.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    // FirebaseAuth is shared app-wide so auth state stays consistent.
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    // Firestore backs runs, photos, and user profile documents.
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    // Storage is used for full-size run photos referenced by Photo docs.
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    // Wrapping Firestore access in a service keeps collection/query details
    // in one place instead of scattering them across repositories.
    fun provideFirestoreService(firestore: FirebaseFirestore): FirestoreService = 
        FirestoreService(firestore)

    @Provides
    @Singleton
    // AuthRepository is injected anywhere auth operations are needed so UI
    // code never has to depend on FirebaseAuth directly.
    fun provideAuthRepository(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore
    ): AuthRepository = AuthRepository(auth, firestore)
}

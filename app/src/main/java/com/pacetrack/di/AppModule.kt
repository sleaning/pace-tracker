package com.pacetrack.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.pacetrack.data.model.remote.FirestoreService
import com.pacetrack.data.model.repository.AuthRepository
import com.pacetrack.data.model.repository.PhotoRepository
import com.pacetrack.data.model.repository.RunRepository
import com.pacetrack.data.model.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideFirestoreService(firestore: FirebaseFirestore) = FirestoreService(firestore)

    @Provides
    @Singleton
    fun provideRunRepository(service: FirestoreService) = RunRepository(service)

    @Provides
    @Singleton
    fun provideUserRepository(firestore: FirebaseFirestore) = UserRepository(firestore)

    @Provides
    @Singleton
    fun provideAuthRepository(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore
    ) = AuthRepository(auth, firestore)

    @Provides
    @Singleton
    fun providePhotoRepository(
        firestore: FirebaseFirestore,
        storage: FirebaseStorage
    ) = PhotoRepository(firestore, storage)
}
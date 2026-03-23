package com.pacetrack.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.google.firebase.auth.FirebaseAuth

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToSignIn: () -> Unit
) {
    val currentUser = FirebaseAuth.getInstance().currentUser

    LaunchedEffect(Unit) {
        if (currentUser != null) {
            onNavigateToHome()
        } else {
            onNavigateToSignIn()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "PaceTrack",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}
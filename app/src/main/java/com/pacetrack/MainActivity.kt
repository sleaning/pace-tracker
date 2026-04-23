package com.pacetrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pacetrack.ui.navigation.AppNavGraph
import com.pacetrack.ui.navigation.BottomNavBar
import com.pacetrack.ui.navigation.Screen
import com.pacetrack.ui.theme.PaceTrackTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry activity for the PaceTrack app.
 * It creates the Compose host, owns the shared NavController, and decides
 * when the bottom navigation should disappear for full-screen flows.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaceTrackTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Auth, live tracking, and detail screens need the full height,
                // so the persistent bottom navigation is suppressed there.
                val hideBottomNav = currentRoute in listOf(
                    Screen.Splash.route,
                    Screen.SignIn.route,
                    Screen.SignUp.route,
                    Screen.ActiveTracking.route,
                    Screen.PostRunSummary.route,
                    Screen.RouteDetail.route,
                )

                Scaffold(
                    bottomBar = {
                        if (!hideBottomNav) {
                            BottomNavBar(navController = navController)
                        }
                    }
                ) { innerPadding ->
                    AppNavGraph(navController = navController)
                }
            }
        }
    }
}

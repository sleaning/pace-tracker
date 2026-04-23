package com.pacetrack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pacetrack.data.model.ActivityType
import com.pacetrack.ui.auth.SignInScreen
import com.pacetrack.ui.auth.SignUpScreen
import com.pacetrack.ui.auth.SplashScreen
import com.pacetrack.ui.history.HistoryScreen
import com.pacetrack.ui.history.RouteDetailScreen
import com.pacetrack.ui.home.HomeScreen
import com.pacetrack.ui.profile.ProfileScreen
import com.pacetrack.ui.tracking.ActiveTrackingScreen
import com.pacetrack.ui.tracking.PostRunSummaryScreen
import com.pacetrack.ui.tracking.PreRunScreen
import com.pacetrack.ui.tracking.TrackingViewModel

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToSignIn = {
                    navController.navigate(Screen.SignIn.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.SignIn.route) {
            SignInScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.SignIn.route) { inclusive = true }
                    }
                },
                onNavigateToSignUp = { navController.navigate(Screen.SignUp.route) }
            )
        }

        composable(Screen.SignUp.route) {
            SignUpScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.SignIn.route) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(onRunClick = { runId ->
                navController.navigate(Screen.RouteDetail.buildRoute(runId))
            })
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateToRouteDetail = { runId ->
                    navController.navigate(Screen.RouteDetail.buildRoute(runId))
                }
            )
        }

        composable(Screen.PreRun.route) {
            PreRunScreen(
                onStartRun = { navController.navigate(Screen.ActiveTracking.route) }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onSignOut = {
                    navController.navigate(Screen.SignIn.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Shared Tracking Graph Logic
        composable(Screen.ActiveTracking.route) { backStackEntry ->
            // Use the parent backStackEntry to scope the ViewModel if needed, 
            // but for simplicity in this flow, we'll just use the default hiltViewModel()
            // and ensure the Service holds the truth.
            ActiveTrackingScreen(
                onRunFinished = { type ->
                    navController.navigate(Screen.PostRunSummary.buildRoute(type.name)) {
                        popUpTo(Screen.PreRun.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.PostRunSummary.route,
            arguments = listOf(navArgument(Screen.PostRunSummary.ARG) { type = NavType.StringType })
        ) { backStackEntry ->
            val typeStr = backStackEntry.arguments?.getString(Screen.PostRunSummary.ARG)
            val activityType = try {
                ActivityType.valueOf(typeStr ?: "RUN")
            } catch (e: Exception) {
                ActivityType.RUN
            }

            PostRunSummaryScreen(
                activityType = activityType,
                onSaved = {
                    navController.navigate(Screen.History.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onDiscarded = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.RouteDetail.route,
            arguments = listOf(navArgument(Screen.RouteDetail.ARG) { type = NavType.StringType })
        ) { backStackEntry ->
            val runId = backStackEntry.arguments?.getString(Screen.RouteDetail.ARG) ?: return@composable
            RouteDetailScreen(
                runId = runId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

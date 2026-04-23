package com.pacetrack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

private const val RUN_REFRESH_SIGNAL = "run_refresh_signal"

/**
 * Central navigation map for every top-level PaceTrack screen.
 * This graph wires route strings to Composables and keeps screen-to-screen
 * transitions in one place so the rest of the UI stays focused on content.
 */
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

        composable(Screen.Home.route) { backStackEntry ->
            val refreshSignal by backStackEntry.savedStateHandle
                .getStateFlow<Long?>(RUN_REFRESH_SIGNAL, null)
                .collectAsState()

            // Route detail is opened from both the social feed and the user's
            // own history, so the navigation target is centralized here.
            HomeScreen(
                onRunClick = { runId ->
                    navController.navigate(Screen.RouteDetail.buildRoute(runId))
                },
                refreshSignal = refreshSignal,
                onRefreshConsumed = {
                    backStackEntry.savedStateHandle[RUN_REFRESH_SIGNAL] = null
                }
            )
        }

        composable(Screen.History.route) { backStackEntry ->
            val refreshSignal by backStackEntry.savedStateHandle
                .getStateFlow<Long?>(RUN_REFRESH_SIGNAL, null)
                .collectAsState()

            HistoryScreen(
                onNavigateToRouteDetail = { runId ->
                    navController.navigate(Screen.RouteDetail.buildRoute(runId))
                },
                refreshSignal = refreshSignal,
                onRefreshConsumed = {
                    backStackEntry.savedStateHandle[RUN_REFRESH_SIGNAL] = null
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

        composable(Screen.ActiveTracking.route) { backStackEntry ->
            // The service owns the live tracking state, so this screen can use
            // the default Hilt ViewModel without losing the active session.
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
            // The activity type is passed through navigation so the summary
            // page can keep the correct copy and labels after the run stops.
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
            // Route detail cannot render without a run id, so the graph exits
            // early if the navigation argument is unexpectedly missing.
            val runId = backStackEntry.arguments?.getString(Screen.RouteDetail.ARG) ?: return@composable
            RouteDetailScreen(
                runId = runId,
                onNavigateBack = { navController.popBackStack() },
                onRunUpdated = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(RUN_REFRESH_SIGNAL, System.currentTimeMillis())
                }
            )
        }
    }
}

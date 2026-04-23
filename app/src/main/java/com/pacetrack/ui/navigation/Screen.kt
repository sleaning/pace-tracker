package com.pacetrack.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Route definitions shared across navigation, tabs, and deep links.
 * Keeping these route strings in one sealed hierarchy prevents mismatches
 * between where screens are declared and how callers navigate to them.
 */
sealed class Screen(val route: String) {

    // Auth
    object Splash : Screen("splash")
    object SignIn : Screen("sign_in")
    object SignUp : Screen("sign_up")

    // Bottom nav tabs
    object Home : Screen("home")
    object History : Screen("history")
    object PreRun : Screen("pre_run")
    object Profile : Screen("profile")

    // Detail screens
    object ActiveTracking : Screen("active_tracking")
    
    object PostRunSummary : Screen("post_run_summary/{activityType}") {
        const val ARG = "activityType"

        // Callers use this helper so they never have to hand-build the
        // parameterized route string expected by the NavHost.
        fun buildRoute(type: String) = "post_run_summary/$type"
    }

    object RouteDetail : Screen("route_detail/{runId}") {
        const val ARG = "runId"

        // Route detail is always keyed by a saved run id, so the helper keeps
        // navigation call sites compact and consistent.
        fun buildRoute(runId: String) = "route_detail/$runId"
    }

    companion object {
        val bottomNavItems: List<BottomNavItem> = listOf(
            BottomNavItem(Home,    "Home",    Icons.Filled.Home),
            BottomNavItem(History, "History", Icons.Filled.History),
            BottomNavItem(PreRun,  "Start",   Icons.Filled.DirectionsRun),
            BottomNavItem(Profile, "Profile", Icons.Filled.Person),
        )
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

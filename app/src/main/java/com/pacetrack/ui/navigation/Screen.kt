package com.pacetrack.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

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
    object PostRunSummary : Screen("post_run_summary")
    object RouteDetail : Screen("route_detail/{runId}") {
        const val ARG = "runId"
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
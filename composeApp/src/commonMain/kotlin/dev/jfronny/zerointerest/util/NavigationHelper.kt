package dev.jfronny.zerointerest.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

class NavigationHelper(val navController: NavController) {
    val currentBackStackEntry: NavBackStackEntry?
        @Composable get() {
            val entry by navController.currentBackStackEntryAsState()
            return entry
        }

    fun navigateTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigate(route: Any) {
        navController.navigate(route)
    }

    fun popBackStack() {
        navController.popBackStack()
    }
}

fun NavBackStackEntry?.has(destination: Any): Boolean {
    if (this == null) return false
    return this.destination.hierarchy.any { it.hasRoute(route = destination::class) }
}


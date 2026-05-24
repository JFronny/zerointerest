package dev.jfronny.zerointerest.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.jfronny.zerointerest.Destination

class NavigationHelper(
    val main: NavHostController
) {
    fun navigate(route: Destination) {
        main.navigate(route)
    }

    fun popMainBackStack() {
        main.popBackStack()
    }

    class Room(val main: NavigationHelper, val room: NavHostController) {
        fun navigateTab(route: Destination.Room.RoomDestination) {
            room.navigate(route) {
                popUpTo(room.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        @Composable
        fun roomIs(): ((route: Destination.Room.RoomDestination) -> Boolean) {
            val entry by room.currentBackStackEntryAsState()
            return { route -> entry?.has(route) == true }
        }
    }
}

private fun NavBackStackEntry?.has(destination: Any): Boolean {
    if (this == null) return false
    return this.destination.hierarchy.any { it.hasRoute(route = destination::class) }
}

@Composable
fun rememberNavigationHelper() : NavigationHelper {
    val mainNavController = rememberNavController()
    return remember(mainNavController) {
        NavigationHelper(mainNavController)
    }
}

@Composable
fun NavigationHelper.room() : NavigationHelper.Room {
    val roomNavController = rememberNavController()
    return remember(this, roomNavController) {
        NavigationHelper.Room(this, roomNavController)
    }
}

package dev.jfronny.zerointerest.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.jfronny.zerointerest.Destination

class Navigator(
    val state: NavigationState,
) {
    val stack get() = state.rootBackStack

    fun navigate(route: Destination) {
        state.rootBackStack.add(route)
    }

    fun goBack() {
        state.rootBackStack.removeLastOrNull()
    }

    class Room(val main: Navigator) {
        val stack get() = main.state.roomBackStack

        fun navigateTab(route: Destination.Room.RoomDestination) {
            main.state.roomBackStack.clear()
            main.state.roomBackStack.add(route)
        }

        @Composable
        fun roomIs(): ((route: Destination.Room.RoomDestination) -> Boolean) {
            return { route -> main.state.roomBackStack.last() == route }
        }
    }
}

@Composable
fun rememberNavigator(startRoute: Destination = Destination.LoadingScreen): Navigator {
    val mainNavController = rememberNavigationState(startRoute = startRoute)
    return remember(mainNavController) {
        Navigator(mainNavController)
    }
}

@Composable
fun Navigator.room(): Navigator.Room = remember(this) {
    Navigator.Room(this)
}

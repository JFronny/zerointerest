package dev.jfronny.zerointerest.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.jfronny.zerointerest.Destination
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer

@OptIn(ExperimentalSerializationApi::class)
private val serializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclassesOfSealed<Destination>()
        subclassesOfSealed<Destination.Room.RoomDestination>()
    }
}

private val config = SavedStateConfiguration {
    serializersModule = dev.jfronny.zerointerest.util.serializersModule
}

/**
 * Create a navigation state that persists config changes and process death.
 */
@Composable
fun rememberNavigationState(
    startRoute: Destination,
): NavigationState {
    val topLevelRoute = rememberSerializable(
        startRoute,
        serializer = MutableStateSerializer<Destination>(),
    ) {
        mutableStateOf(startRoute)
    }

    val rootBackStack = rememberNavBackStack(config, startRoute)
    val roomBackStack = rememberNavBackStack<Destination.Room.RoomDestination>(config, Destination.Room.RoomDestination.Balance)

    return remember(startRoute, rootBackStack, roomBackStack) {
        NavigationState(
            startRoute = startRoute,
            rootBackStack = rootBackStack,
            roomBackStack = roomBackStack,
        )
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Composable
private inline fun <reified T : NavKey> rememberNavBackStack(
    configuration: SavedStateConfiguration,
    vararg elements: T,
): NavBackStack<T> {
    require(configuration.serializersModule != SavedStateConfiguration.DEFAULT.serializersModule) {
        "You must pass a `SavedStateConfiguration.serializersModule` configured to handle " +
            "`NavKey` open polymorphism. Define it with: `polymorphic(NavKey::class) { ... }`"
    }
    return rememberSerializable(
        configuration = configuration,
        serializer = NavBackStackSerializer(serializersModule.serializer(T::class, listOf(), false) as KSerializer<T>),
    ) {
        NavBackStack(*elements)
    }
}

/**
 * State holder for navigation state.
 *
 * @param startRoute - the start route. The user will exit the app through this route.
 */
class NavigationState(
    val startRoute: Destination,
    val rootBackStack: NavBackStack<Destination>,
    val roomBackStack: NavBackStack<Destination.Room.RoomDestination>,
)

/**
 * Convert NavigationState into NavEntries.
 */
@Composable
fun <T : NavKey> NavBackStack<T>.toEntries(
    entryProvider: (T) -> NavEntry<T>,
): SnapshotStateList<NavEntry<T>> {
    val decorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<T>(),
        rememberViewModelStoreNavEntryDecorator(),
    )
    return rememberDecoratedNavEntries(
        backStack = this,
        entryDecorators = decorators,
        entryProvider = entryProvider,
    ).toMutableStateList()
}

package dev.jfronny.zerointerest.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.jfronny.zerointerest.Destination
import dev.jfronny.zerointerest.MatrixClientService
import dev.jfronny.zerointerest.ui.theme.AppTheme
import dev.jfronny.zerointerest.util.RoomIdNavType
import io.github.oshai.kotlinlogging.KotlinLogging
import net.folivo.trixnity.core.model.RoomId
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import kotlin.reflect.typeOf

const val appName = "zerointerest"
val log = KotlinLogging.logger {}

@Composable
@Preview
fun App() = AppTheme {
    val navController = rememberNavController()
    val service = koinInject<MatrixClientService>()

    NavHost(
        navController = navController,
        startDestination = if (!service.loggedIn) Destination.Login else Destination.PickRoom,
        typeMap = mapOf(typeOf<RoomId>() to RoomIdNavType)
    ) {
        composable<Destination.Login> {
            LoginScreen(
                onSuccess = { navController.navigate(Destination.PickRoom) }
            )
        }
        composable<Destination.PickRoom> {
            PickRoomScreen(
                onPick = { navController.navigate(Destination.Room(it)) }
            )
        }
        composable<Destination.Room>(typeMap = mapOf(typeOf<RoomId>() to RoomIdNavType)) {
            val roomId = it.toRoute<Destination.Room>().roomId
            RoomScreen(
                onSuccess = { /* TODO */ }
            )
        }
    }
}

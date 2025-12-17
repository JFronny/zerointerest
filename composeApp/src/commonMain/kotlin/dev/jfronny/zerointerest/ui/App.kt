package dev.jfronny.zerointerest.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.jfronny.zerointerest.Destination
import dev.jfronny.zerointerest.service.MatrixClientService
import dev.jfronny.zerointerest.ui.theme.AppTheme
import dev.jfronny.zerointerest.util.RoomIdNavType
import io.github.oshai.kotlinlogging.KotlinLogging
import net.folivo.trixnity.client.verification
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
            val route = it.toRoute<Destination.Room>()
            RoomScreen(
                onBack = { navController.popBackStack() },
                roomId = route.roomId
            )
        }
    }

    val rxclient by koinInject<MatrixClientService>().client.collectAsState(null)
    val client = rxclient

    if (client != null) {
        val verification by client.verification.activeDeviceVerification.collectAsState()
        var mutVer = verification
        LaunchedEffect(verification) {
            mutVer = verification
            log.info { "Verification changed to $verification" }
        }
        mutVer?.let { VerificationDialog(client, it) { mutVer = null } }
    }
}

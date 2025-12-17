package dev.jfronny.zerointerest.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.jfronny.zerointerest.Destination
import dev.jfronny.zerointerest.service.MatrixClientService
import dev.jfronny.zerointerest.service.Settings
import dev.jfronny.zerointerest.ui.theme.AppTheme
import dev.jfronny.zerointerest.util.RoomIdNavType
import dev.jfronny.zerointerest.util.rememberNavigationHelper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.verification
import net.folivo.trixnity.core.model.RoomId
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import kotlin.reflect.typeOf

const val appName = "zerointerest"
private val log = KotlinLogging.logger {}

private suspend fun Settings.startDestination() = rememberedRoom()?.let { Destination.Room(it) } ?: Destination.PickRoom

@Composable
@Preview
fun App() = AppTheme {
    val navHelper = rememberNavigationHelper()
    val service = koinInject<MatrixClientService>()
    val settings = koinInject<Settings>()

    NavHost(
        navController = navHelper.main,
        startDestination = Destination.Login,
        typeMap = mapOf(typeOf<RoomId>() to RoomIdNavType)
    ) {
        composable<Destination.Login> {
            LoginScreen(
                onSuccess = {
                    navHelper.navigate(settings.startDestination())
                }
            )
        }
        composable<Destination.PickRoom> {
            val scope = rememberCoroutineScope()
            PickRoomScreen(
                onPick = {
                    scope.launch {
                        settings.rememberRoom(it)
                        navHelper.navigate(Destination.Room(it))
                    }
                }
            )
        }
        composable<Destination.Room>(typeMap = mapOf(typeOf<RoomId>() to RoomIdNavType)) {
            val route = it.toRoute<Destination.Room>()
            val scope = rememberCoroutineScope()
            RoomScreen(
                roomId = route.roomId,
                onBack = {
                    scope.launch {
                        settings.clearRememberedRoom()
                        navHelper.popMainBackStack()
                    }
                },
                onAddTransaction = { navHelper.navigate(Destination.CreateTransaction(route.roomId)) },
                navHelper = navHelper
            )
        }
        composable<Destination.CreateTransaction>(typeMap = mapOf(typeOf<RoomId>() to RoomIdNavType)) {
            val route = it.toRoute<Destination.CreateTransaction>()
            CreateTransactionScreen(
                client = service.get(),
                roomId = route.roomId,
                onDone = { navHelper.popMainBackStack() },
                onBack = { navHelper.popMainBackStack() }
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

package dev.jfronny.zerointerest.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.jfronny.zerointerest.Destination
import dev.jfronny.zerointerest.service.MatrixClientService
import dev.jfronny.zerointerest.service.Settings
import dev.jfronny.zerointerest.ui.theme.AppTheme
import dev.jfronny.zerointerest.util.EventIdNavType
import dev.jfronny.zerointerest.util.RoomIdNavType
import dev.jfronny.zerointerest.util.rememberNavigationHelper
import kotlinx.coroutines.launch
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import kotlin.reflect.typeOf

const val appName = "zerointerest"

private suspend fun Settings.startDestination() = rememberedRoom()?.let { Destination.Room(it) } ?: Destination.PickRoom

@Composable
@Preview
fun App() = AppTheme { Surface {
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
                },
                logout = {
                    scope.launch {
                        service.logout()
                        navHelper.navigate(Destination.Login)
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
        composable<Destination.TransactionDetails>(
            typeMap = mapOf(
                typeOf<RoomId>() to RoomIdNavType,
                typeOf<EventId>() to EventIdNavType
            )
        ) {
            val route = it.toRoute<Destination.TransactionDetails>()
            TransactionDetailsScreen(
                client = service.get(),
                roomId = route.roomId,
                transactionId = route.transactionId,
                onBack = { navHelper.popMainBackStack() }
            )
        }
    }

    VerificationDialog()
} }

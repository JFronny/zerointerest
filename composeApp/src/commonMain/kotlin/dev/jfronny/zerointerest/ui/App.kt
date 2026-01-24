package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.jfronny.zerointerest.Destination
import dev.jfronny.zerointerest.data.TransactionTemplate
import dev.jfronny.zerointerest.service.MatrixClientService
import dev.jfronny.zerointerest.service.Settings
import dev.jfronny.zerointerest.service.ZeroInterestDatabase
import dev.jfronny.zerointerest.ui.theme.AppTheme
import dev.jfronny.zerointerest.util.EventIdNavType
import dev.jfronny.zerointerest.util.RoomIdNavType
import dev.jfronny.zerointerest.util.rememberNavigationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import org.koin.compose.koinInject
import kotlin.reflect.typeOf

const val appName = "zerointerest"

@Composable
fun App() = AppTheme {
    val navHelper = rememberNavigationHelper()
    val service = koinInject<MatrixClientService>()
    val settings = koinInject<Settings>()
    val database = koinInject<ZeroInterestDatabase>()

    suspend fun onLoginSuccess() {
        val rememberedRoom = settings.rememberedRoom()
        navHelper.navigate(Destination.PickRoom)
        if (rememberedRoom == null) return
        navHelper.navigate(Destination.Room(rememberedRoom))
    }

    NavHost(
        navController = navHelper.main,
        startDestination = Destination.LoadingScreen,
        typeMap = mapOf(typeOf<RoomId>() to RoomIdNavType)
    ) {
        composable<Destination.LoadingScreen> {
            LoadingScreen(
                onSuccess = ::onLoginSuccess,
                onError = {
                    navHelper.navigate(Destination.SelectHomeserver)
                }
            )
        }
        composable<Destination.SelectHomeserver> {
            HomeserverScreen(
                onContinue = { homeserver ->
                    navHelper.navigate(Destination.SelectLoginMethod(homeserver))
                }
            )
        }
        composable<Destination.SelectLoginMethod> {
            val route = it.toRoute<Destination.SelectLoginMethod>()
            LoginMethodScreen(
                homeserver = route.homeserver,
                onBack = { navHelper.popMainBackStack() },
                onSuccess = ::onLoginSuccess
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
                        navHelper.navigate(Destination.SelectHomeserver)
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
                onAddTransaction = { template ->
                    navHelper.navigate(Destination.CreateTransaction(route.roomId, template?.id))
                },
                navHelper = navHelper
            )
        }
        composable<Destination.CreateTransaction>(typeMap = mapOf(typeOf<RoomId>() to RoomIdNavType)) {
            val route = it.toRoute<Destination.CreateTransaction>()
            val templateId = route.templateId
            var initialTemplate by remember { mutableStateOf<TransactionTemplate?>(null) }
            LaunchedEffect(templateId) {
                if (templateId != null) {
                    initialTemplate = database.getTransactionTemplates(route.roomId).first().find { it.id == templateId }
                }
            }

            if (templateId != null && initialTemplate == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                CreateTransactionScreen(
                    client = service.get(),
                    roomId = route.roomId,
                    initialTemplate = initialTemplate,
                    onDone = { navHelper.popMainBackStack() },
                    onBack = { navHelper.popMainBackStack() }
                )
            }
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
}

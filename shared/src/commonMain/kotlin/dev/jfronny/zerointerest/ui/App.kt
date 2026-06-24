package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.svg.SvgDecoder
import dev.jfronny.zerointerest.Destination
import dev.jfronny.zerointerest.data.TransactionTemplate
import dev.jfronny.zerointerest.db.ZeroInterestDatabase
import dev.jfronny.zerointerest.service.Settings
import dev.jfronny.zerointerest.service.client.MatrixClientService
import dev.jfronny.zerointerest.ui.component.EmojiService
import dev.jfronny.zerointerest.ui.component.VerificationDialog
import dev.jfronny.zerointerest.ui.theme.AppTheme
import dev.jfronny.zerointerest.util.CoilMxcFetcher
import dev.jfronny.zerointerest.util.Navigator
import dev.jfronny.zerointerest.util.rememberNavigator
import dev.jfronny.zerointerest.util.toEntries
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

const val appName = "zerointerest"

@OptIn(coil3.annotation.ExperimentalCoilApi::class)
@Composable
fun App(navHelper: Navigator = rememberNavigator()) {
    remember { EmojiService.initialize() }
    val service = koinInject<MatrixClientService>()
    val settings = koinInject<Settings>()
    val database = koinInject<ZeroInterestDatabase>()
    val httpClient = koinInject<HttpClient>()

    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory(httpClient))
                add(CoilMxcFetcher.Factory(service))
                add(SvgDecoder.Factory(renderToBitmap = false))
            }
            .build()
    }

    suspend fun onLoginSuccess() {
        val rememberedRoom = settings.rememberedRoom()
        navHelper.navigate(Destination.PickRoom)
        if (rememberedRoom == null) return
        navHelper.navigate(Destination.Room(rememberedRoom))
    }

    AppTheme {
        AppNavigation(navHelper, service, settings, database, ::onLoginSuccess)
        VerificationDialog()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppNavigation(
    navHelper: Navigator,
    service: MatrixClientService,
    settings: Settings,
    database: ZeroInterestDatabase,
    onLoginSuccess: suspend () -> Unit,
) {
    val entryProvider = remember {
        entryProvider {
            entry<Destination.LoadingScreen> {
                LoadingScreen(
                    onSuccess = { onLoginSuccess() },
                    onError = {
                        navHelper.navigate(Destination.SelectHomeserver)
                    },
                )
            }
            entry<Destination.SelectHomeserver> {
                HomeserverScreen(
                    onContinue = { homeserver ->
                        navHelper.navigate(Destination.SelectLoginMethod(homeserver))
                    },
                )
            }
            entry<Destination.SelectLoginMethod> { route ->
                LoginMethodScreen(
                    homeserver = route.homeserver,
                    onBack = { navHelper.goBack() },
                    onSuccess = { onLoginSuccess() },
                )
            }
            entry<Destination.PickRoom> {
                val scope = rememberCoroutineScope()
                PickRoomScreen(
                    onPick = {
                        scope.launch {
                            settings.rememberRoom(it)
                            navHelper.navigate(Destination.Room(it))
                        }
                    },
                    openSettings = { navHelper.navigate(Destination.SettingsScreen) },
                )
            }
            entry<Destination.Room> { route ->
                val scope = rememberCoroutineScope()
                RoomScreen(
                    roomId = route.roomId,
                    onBack = {
                        scope.launch {
                            settings.clearRememberedRoom()
                            navHelper.goBack()
                        }
                    },
                    onAddTransaction = { template ->
                        navHelper.navigate(Destination.CreateTransaction(route.roomId, template?.id))
                    },
                    openSettings = { navHelper.navigate(Destination.SettingsScreen) },
                    navHelper = navHelper,
                )
            }
            entry<Destination.CreateTransaction> { route ->
                val templateId = route.templateId
                var initialTemplate by remember { mutableStateOf<TransactionTemplate?>(null) }
                LaunchedEffect(templateId) {
                    if (templateId != null) {
                        initialTemplate = database.getTransactionTemplates(route.roomId).first()
                            .find { it.id == templateId }
                    }
                }

                if (templateId != null && initialTemplate == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                } else {
                    CreateTransactionScreen(
                        roomId = route.roomId,
                        initialTemplate = initialTemplate,
                        onDone = { navHelper.goBack() },
                        onBack = { navHelper.goBack() },
                        openSettings = { navHelper.navigate(Destination.SettingsScreen) },
                    )
                }
            }
            entry<Destination.TransactionDetails> { route ->
                TransactionDetailsScreen(
                    client = service.getMatrixClient(),
                    roomId = route.roomId,
                    transactionId = route.transactionId,
                    onBack = { navHelper.goBack() },
                )
            }
            entry<Destination.SettleScreen> { route ->
                SettleScreen(
                    client = service.get(),
                    roomId = route.roomId,
                    onBack = { navHelper.goBack() },
                )
            }
            entry<Destination.SettingsScreen> {
                val scope = rememberCoroutineScope()
                SettingsScreen(
                    onBack = { navHelper.goBack() },
                    onLogout = {
                        scope.launch {
                            service.logout()
                            settings.clearRememberedRoom()
                            navHelper.navigate(Destination.SelectHomeserver)
                        }
                    },
                )
            }
        }
    }
    NavDisplay(
        entries = navHelper.stack.toEntries(entryProvider),
        onBack = { navHelper.goBack() },
    )
}

package dev.jfronny.zerointerest

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.core.util.Consumer
import de.connect2x.lognity.api.backend.Backend
import dev.jfronny.zerointerest.service.AndroidSsoLoginHandler
import dev.jfronny.zerointerest.ui.App
import dev.jfronny.zerointerest.util.LognityWrangler
import dev.jfronny.zerointerest.util.rememberNavigationHelper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.compose.KoinApplication
import org.koin.core.logger.Level
import org.koin.dsl.koinConfiguration

private val log = KotlinLogging.logger {}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        System.setProperty("slf4j.provider", AndroidServiceProvider::class.java.name)

        Backend.set(LognityWrangler)
        setContent {
            KoinApplication(
                configuration = koinConfiguration(declaration = {
                    androidLogger(Level.ERROR)
                    androidContext(this@MainActivity)
                    modules(listOf(createAppModule(), createExtraModule()))
                }),
                content = {
                    val navHelper = rememberNavigationHelper()
                    LaunchedEffect(Unit) {
                        callbackFlow {
                            val consumer = Consumer<Intent> { trySend(it) }
                            consumer.accept(intent)
                            addOnNewIntentListener(consumer)
                            awaitClose { removeOnNewIntentListener(consumer) }
                        }.collectLatest {
                            handleIntent(it, navHelper::navigate)
                        }
                    }
                    App(navHelper = navHelper)
                },
            )
        }
    }

    private fun handleIntent(
        intent: Intent,
        onNavigate: (Destination) -> Unit,
    ) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.data
                if (data != null && data.scheme == "zerointerest" && data.host == "sso") {
                    AndroidSsoLoginHandler.handleCallbackUrl(data.toString())
                } else {
                    log.warn { "Unknown intent data: $data" }
                }
            }

            Intent.ACTION_APPLICATION_PREFERENCES -> {
                onNavigate(Destination.SettingsScreen)
            }

            Intent.ACTION_MAIN -> {}

            else -> log.warn { "Unknown intent action: ${intent.action}" }
        }
    }
}

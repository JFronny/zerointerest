package dev.jfronny.zerointerest

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.connect2x.lognity.api.backend.Backend
import dev.jfronny.zerointerest.service.AndroidSsoLoginHandler
import dev.jfronny.zerointerest.ui.App
import dev.jfronny.zerointerest.util.LognityWrangler
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.compose.KoinApplication
import org.koin.core.logger.Level
import org.koin.dsl.koinConfiguration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        System.setProperty("slf4j.provider", AndroidServiceProvider::class.java.name)

        Backend.set(LognityWrangler)
        setContent {
            KoinApplication(configuration = koinConfiguration(declaration = {
                androidLogger(Level.ERROR)
                androidContext(this@MainActivity)
                modules(listOf(createAppModule(), createExtraModule()))
            }), content = {
                App()
            })
        }
        
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data
        if (data != null && data.scheme == "zerointerest" && data.host == "sso") {
            AndroidSsoLoginHandler.handleCallbackUrl(data.toString())
        }
    }
}

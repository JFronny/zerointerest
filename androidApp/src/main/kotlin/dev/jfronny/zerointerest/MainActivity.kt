package dev.jfronny.zerointerest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.jfronny.zerointerest.ui.App
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.compose.KoinApplication
import org.koin.core.logger.Level

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        System.setProperty("slf4j.provider", AndroidServiceProvider::class.java.name)

        setContent {
            KoinApplication(application = {
                androidLogger(Level.ERROR)
                androidContext(this@MainActivity)
                modules(listOf(createAppModule(), createExtraModule()))
            }) {
                App()
            }
        }
    }
}

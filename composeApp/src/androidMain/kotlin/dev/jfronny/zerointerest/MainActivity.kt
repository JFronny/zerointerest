package dev.jfronny.zerointerest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.jfronny.zerointerest.service.Settings
import dev.jfronny.zerointerest.ui.App
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.compose.KoinApplication
import org.koin.core.logger.Level
import org.koin.dsl.bind
import org.koin.dsl.module

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            KoinApplication(application = {
                androidLogger(Level.ERROR)
                androidContext(this@MainActivity)
                modules(listOf(createAppModule(), createExtraModule(), createAndroidModule()))
            }) {
                App()
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}

fun createAndroidModule() = module {
    single {
        AndroidSettings(get())
    } bind Settings::class
}

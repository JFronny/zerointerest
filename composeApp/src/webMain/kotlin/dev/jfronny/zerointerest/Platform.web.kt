package dev.jfronny.zerointerest

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.github.oshai.kotlinlogging.KotlinLogging
import web.events.EventHandler
import web.window.window
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

private val log = KotlinLogging.logger {}

@Composable
actual fun getPlatformTheme(darkTheme: Boolean): ColorScheme? = null

actual fun addShutdownHook(block: () -> Unit) {
    window.onbeforeunload = EventHandler(block)
}

fun launch(block: suspend () -> Unit) {
    block.startCoroutine(object : Continuation<Unit> {
        override val context: CoroutineContext get() = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) {
            result.onFailure {
                log.error(it) { "Coroutine failed" }
            }
        }
    })
}

actual suspend fun SQLiteConnection.execSQL(sql: String) = execSQL(sql)

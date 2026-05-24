package dev.jfronny.zerointerest

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import androidx.sqlite.execSQL
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.media.indexeddb.indexedDB
import de.connect2x.trixnity.client.store.repository.indexeddb.indexedDB
import dev.jfronny.zerointerest.db.ZeroInterestRoomDatabase
import dev.jfronny.zerointerest.shared.generated.resources.Res
import dev.jfronny.zerointerest.shared.generated.resources.timestamp_just_now
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import js.date.Date
import js.intl.DateTimeFormat
import js.intl.RelativeTimeFormat
import js.intl.RelativeTimeFormatUnit
import js.intl.day
import js.intl.hour
import js.intl.minute
import org.jetbrains.compose.resources.stringResource
import org.koin.core.scope.Scope
import org.w3c.dom.Worker
import web.console.console
import web.events.EventHandler
import web.window.window
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.toJsString
import kotlin.time.Instant

private val log = KotlinLogging.logger {}

class WebPlatform : Platform {
    override val name: String = "Web"
    override suspend fun getRepositoriesModule(): RepositoriesModule = RepositoriesModule.indexedDB()
    override suspend fun getMediaStoreModule(): MediaStoreModule = MediaStoreModule.indexedDB()
    override fun getHttpClientEngine(): HttpClientEngine = Js.create {}
    override fun createDataStore(): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(WebLocalStorage(
            name = "zerointerest",
            serializer = PreferencesSerializer,
        ))
    }

    override fun zerointerestDatabaseBuilder(): RoomDatabase.Builder<ZeroInterestRoomDatabase> =
        Room.databaseBuilder<ZeroInterestRoomDatabase>("zerointerest")
}

actual fun Scope.getPlatform(): Platform = WebPlatform()

@OptIn(ExperimentalWasmJsInterop::class)
actual fun createSQLiteDriver(): SQLiteDriver = WebWorkerSQLiteDriver(createSQLiteWorker().apply {
    onerror = { e -> console.error("Error in SQL.js worker".toJsString(), e) }
})

expect fun WebWorkerSQLiteDriver(worker: Worker): WebWorkerSQLiteDriver

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => new Worker(new URL('sqlite-web-worker/worker.js', import.meta.url), { type: 'module' })")
private external fun createSQLiteWorker(): Worker

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

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })")
private external fun createRelativeTimeFormat(): RelativeTimeFormat

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' })")
private external fun createShortDateTimeFormat(): DateTimeFormat

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'short', day: 'numeric' })")
private external fun createYearDateTimeFormat(): DateTimeFormat

@Composable
actual fun Instant.formatLocalized(style: TimestampStyle): String {
    val now = Date.now()
    val epochMillis = toEpochMilliseconds()
    val diffMs = now - epochMillis

    val minute = 60_000
    val hour = 60 * minute
    val day = 24 * hour

    val rtf = createRelativeTimeFormat()

    return when {
        diffMs < minute -> stringResource(Res.string.timestamp_just_now)

        diffMs < hour ->
            rtf.format(
                -(diffMs / minute).toLong().toDouble(),
                RelativeTimeFormatUnit.minute
            )

        diffMs < day ->
            rtf.format(
                -(diffMs / hour).toLong().toDouble(),
                RelativeTimeFormatUnit.hour
            )

        diffMs < 7 * day ->
            rtf.format(
                -(diffMs / day).toLong().toDouble(),
                RelativeTimeFormatUnit.day
            )

        else -> {
            val date = Date(epochMillis.toDouble())

            if (Date().getFullYear() == date.getFullYear()) {
                createShortDateTimeFormat()
            } else {
                createYearDateTimeFormat()
            }.format(date)
        }
    }
}

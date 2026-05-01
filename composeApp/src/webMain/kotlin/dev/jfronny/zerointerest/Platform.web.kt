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
import dev.jfronny.zerointerest.service.db.ZeroInterestRoomDatabase
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module
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
fun createExtraModule() = module {
    single { WebWorkerSQLiteDriver(createSQLiteWorker().apply {
        onerror = { e -> console.error("Error in SQL.js worker".toJsString(), e) }
    }) } bind SQLiteDriver::class
}

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

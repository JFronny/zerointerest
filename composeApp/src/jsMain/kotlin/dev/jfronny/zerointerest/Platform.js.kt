package dev.jfronny.zerointerest

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.WebStorage
import androidx.datastore.core.okio.WebStorageType
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.media.indexeddb.indexedDB
import de.connect2x.trixnity.client.store.repository.indexeddb.indexedDB
import dev.jfronny.zerointerest.service.db.RoomZeroInterestDatabase
import dev.jfronny.zerointerest.service.db.ZeroInterestRoomDatabase
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module
import org.w3c.dom.Worker

class JsPlatform : Platform {
    override val name: String = "Web"
    override suspend fun getRepositoriesModule(): RepositoriesModule = RepositoriesModule.indexedDB()
    override suspend fun getMediaStoreModule(): MediaStoreModule = MediaStoreModule.indexedDB()
    override fun getHttpClientEngine(): HttpClientEngine = Js.create {}
    override fun createDataStore(): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(WebStorage(
            name = "zerointerest",
            serializer = PreferencesSerializer,
            storageType = WebStorageType.SESSION //TODO: use non-session storage once available
        ))
    }

    override fun zerointerestDatabaseBuilder(): RoomDatabase.Builder<ZeroInterestRoomDatabase> =
        Room.databaseBuilder<ZeroInterestRoomDatabase>("zerointerest")

    override fun handleZerointerestDatabase(db: RoomZeroInterestDatabase) {
        super.handleZerointerestDatabase(db)
        launch { WebZeroInterestDatabaseMigration().migrateTo(db) }
    }
}

actual fun Scope.getPlatform(): Platform = JsPlatform()

actual fun createExtraModule() = module {
    single { WebWorkerSQLiteDriver(Worker(js("""new URL("sqlite-web-worker/worker.js", import.meta.url)"""))) } bind SQLiteDriver::class
}

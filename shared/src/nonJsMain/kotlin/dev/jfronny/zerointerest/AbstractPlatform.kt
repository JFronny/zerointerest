package dev.jfronny.zerointerest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.media.okio.okio
import de.connect2x.trixnity.client.store.repository.room.TrixnityRoomDatabase
import de.connect2x.trixnity.client.store.repository.room.room
import okio.Path

abstract class AbstractPlatform(val stateDir: Path) : Platform {

    final override suspend fun getRepositoriesModule(): RepositoriesModule = RepositoriesModule.room(trixnityDatabaseBuilder().setDriver(BundledSQLiteDriver()))
    final override suspend fun getMediaStoreModule(): MediaStoreModule = MediaStoreModule.okio(stateDir.resolve("media"))

    abstract fun trixnityDatabaseBuilder(): RoomDatabase.Builder<TrixnityRoomDatabase>

    protected fun createDataStore(producePath: () -> Path): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(produceFile = { producePath() })

    companion object {
        const val TRIXNITY_NAME = "trixnity.db"
        const val ZEROINTEREST_NAME = "zerointerest.db"
        const val DATASTORE_NAME = "zerointerest.preferences_pb"
    }
}

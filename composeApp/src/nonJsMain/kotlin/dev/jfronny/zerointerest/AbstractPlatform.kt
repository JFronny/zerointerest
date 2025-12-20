package dev.jfronny.zerointerest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import net.folivo.trixnity.client.media.okio.createOkioMediaStoreModule
import net.folivo.trixnity.client.store.repository.room.TrixnityRoomDatabase
import net.folivo.trixnity.client.store.repository.room.createRoomRepositoriesModule
import okio.Path
import org.koin.core.module.Module

abstract class AbstractPlatform(val stateDir: Path) : Platform {
    protected val TRIXNITY_NAME = "trixnity.db"
    protected val ZEROINTEREST_NAME = "zerointerest.db"
    protected val DATASTORE_NAME = "zerointerest.preferences_pb"

    final override suspend fun getRepositoriesModule(): Module = createRoomRepositoriesModule(trixnityDatabaseBuilder().setDriver(BundledSQLiteDriver()))
    final override suspend fun getMediaStoreModule(): Module = createOkioMediaStoreModule(stateDir.resolve("media"))

    abstract fun trixnityDatabaseBuilder(): RoomDatabase.Builder<TrixnityRoomDatabase>
    abstract fun zerointerestDatabaseBuilder(): RoomDatabase.Builder<ZeroInterestRoomDatabase>

    protected fun createDataStore(producePath: () -> Path): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(produceFile = { producePath() })
}

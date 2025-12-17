package dev.jfronny.zerointerest

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import net.folivo.trixnity.client.media.okio.createOkioMediaStoreModule
import net.folivo.trixnity.client.store.repository.room.TrixnityRoomDatabase
import net.folivo.trixnity.client.store.repository.room.createRoomRepositoriesModule
import okio.Path
import org.koin.core.module.Module

abstract class AbstractPlatform(protected val stateDir: Path) : Platform {
    protected val TRIXNITY_NAME = "trixnity.db"
    protected val ZEROINTEREST_NAME = "zerointerest.db"

    final override suspend fun getRepositoriesModule(): Module = createRoomRepositoriesModule(trixnityDatabaseBuilder().setDriver(BundledSQLiteDriver()))
    final override suspend fun getMediaStoreModule(): Module = createOkioMediaStoreModule(stateDir.resolve("media"))

    abstract fun trixnityDatabaseBuilder(): RoomDatabase.Builder<TrixnityRoomDatabase>
    abstract fun zerointerestDatabaseBuilder(): RoomDatabase.Builder<ZeroInterestRoomDatabase>
}

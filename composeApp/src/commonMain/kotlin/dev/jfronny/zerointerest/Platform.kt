package dev.jfronny.zerointerest

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room3.RoomDatabase
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import dev.jfronny.zerointerest.service.db.RoomZeroInterestDatabase
import dev.jfronny.zerointerest.service.db.ZeroInterestRoomDatabase
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.scope.Scope

interface Platform {
    val name: String

    suspend fun getRepositoriesModule(): RepositoriesModule
    suspend fun getMediaStoreModule(): MediaStoreModule
    fun getHttpClientEngine(): HttpClientEngine
    fun createDataStore(): DataStore<Preferences>

    fun zerointerestDatabaseBuilder(): RoomDatabase.Builder<ZeroInterestRoomDatabase>
    fun handleZerointerestDatabase(db: RoomZeroInterestDatabase) {}
}

expect fun Scope.getPlatform(): Platform
@Composable
expect fun getPlatformTheme(darkTheme: Boolean): ColorScheme?
expect fun addShutdownHook(block: () -> Unit)

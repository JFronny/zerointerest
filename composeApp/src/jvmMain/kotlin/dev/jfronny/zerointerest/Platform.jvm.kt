package dev.jfronny.zerointerest

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import io.ktor.client.engine.java.Java
import de.connect2x.trixnity.client.store.repository.room.TrixnityRoomDatabase
import okio.Path.Companion.toOkioPath
import org.koin.core.scope.Scope
import kotlin.io.path.absolutePathString

class JVMPlatform : AbstractPlatform(OS.stateDir.toOkioPath()) {
    override val name: String = "Desktop (${OS.type.displayName})"
    override fun getHttpClientEngine() = Java.create {}
    override fun trixnityDatabaseBuilder() = Room.databaseBuilder<TrixnityRoomDatabase>(stateDir.resolve(TRIXNITY_NAME).toNioPath().absolutePathString())
    override fun zerointerestDatabaseBuilder() = Room.databaseBuilder<ZeroInterestRoomDatabase>(stateDir.resolve(ZEROINTEREST_NAME).toNioPath().absolutePathString())
    override fun createDataStore(): DataStore<Preferences> = createDataStore {
        stateDir.resolve(DATASTORE_NAME)
    }
}

actual fun Scope.getPlatform(): Platform = JVMPlatform()
@Composable
actual fun getPlatformTheme(darkTheme: Boolean): ColorScheme? = null
actual fun addShutdownHook(block: () -> Unit) = Runtime.getRuntime().addShutdownHook(Thread(block))

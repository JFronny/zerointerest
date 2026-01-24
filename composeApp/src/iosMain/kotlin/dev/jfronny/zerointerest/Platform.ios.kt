package dev.jfronny.zerointerest

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import de.connect2x.trixnity.client.store.repository.room.TrixnityRoomDatabase
import kotlinx.cinterop.staticCFunction
import okio.Path.Companion.toPath
import org.koin.core.scope.Scope
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice
import platform.posix.atexit

class IOSPlatform : AbstractPlatform(documentDirectory().toPath()) {
    override val name: String = UIDevice.currentDevice.systemName()
    override fun trixnityDatabaseBuilder() = Room.databaseBuilder<TrixnityRoomDatabase>("${documentDirectory()}/$TRIXNITY_NAME")
    override fun zerointerestDatabaseBuilder() = Room.databaseBuilder<ZeroInterestRoomDatabase>("${documentDirectory()}/$ZEROINTEREST_NAME")
    @OptIn(ExperimentalForeignApi::class)
    override fun createDataStore(): DataStore<Preferences> = createDataStore {
        val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        (requireNotNull(documentDirectory).path + "/$DATASTORE_NAME").toPath()
    }

    override fun getHttpClientEngine() = Darwin.create {}
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}
actual fun Scope.getPlatform(): Platform = IOSPlatform()

@Composable
actual fun getPlatformTheme(darkTheme: Boolean): ColorScheme? = null
@OptIn(ExperimentalForeignApi::class)
actual fun addShutdownHook(block: () -> Unit) {
    atexit(staticCFunction<Unit> {
        block()
    })
}

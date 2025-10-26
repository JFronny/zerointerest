package dev.jfronny.zerointerest

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import net.folivo.trixnity.client.media.okio.createOkioMediaStoreModule
import net.folivo.trixnity.client.store.repository.room.createRoomRepositoriesModule
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override suspend fun getRepositoriesModule(): Module {
        return createRoomRepositoriesModule(Room.databaseBuilder(documentDirectory() + "/trixnity.db"))
    }

    override suspend fun getMediaStoreModule(): Module {
        return createOkioMediaStoreModule((documentDirectory() + "/media").toPath())
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
}

actual fun Scope.getPlatform(): Platform = IOSPlatform()
package dev.jfronny.zerointerest

import androidx.room.Room
import net.folivo.trixnity.client.media.okio.createOkioMediaStoreModule
import net.folivo.trixnity.client.store.repository.room.TrixnityRoomDatabase
import net.folivo.trixnity.client.store.repository.room.createRoomRepositoriesModule
import okio.Path.Companion.toOkioPath
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import kotlin.io.path.absolutePathString
import kotlin.io.path.div

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override suspend fun getRepositoriesModule(): Module = createRoomRepositoriesModule(Room.databaseBuilder<TrixnityRoomDatabase>((OS.stateDir/"trixnity.db").absolutePathString()))
    override suspend fun getMediaStoreModule(): Module = createOkioMediaStoreModule((OS.stateDir/"media").toOkioPath())
}

actual fun Scope.getPlatform(): Platform = JVMPlatform()

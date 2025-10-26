package dev.jfronny.zerointerest

import android.content.Context
import android.os.Build
import androidx.room.Room
import net.folivo.trixnity.client.media.okio.createOkioMediaStoreModule
import net.folivo.trixnity.client.store.repository.room.TrixnityRoomDatabase
import net.folivo.trixnity.client.store.repository.room.createRoomRepositoriesModule
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.scope.Scope

class AndroidPlatform(private val context: Context) : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    override suspend fun getRepositoriesModule(): Module =
        createRoomRepositoriesModule(Room.databaseBuilder(context, TrixnityRoomDatabase::class.java, "trixnity.db"))

    override suspend fun getMediaStoreModule(): Module =
        createOkioMediaStoreModule(context.dataDir.resolve("media").toOkioPath())
}

actual fun Scope.getPlatform(): Platform = AndroidPlatform(androidContext().applicationContext)

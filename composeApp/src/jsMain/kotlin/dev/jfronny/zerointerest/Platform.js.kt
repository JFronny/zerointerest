package dev.jfronny.zerointerest

import net.folivo.trixnity.client.media.indexeddb.createIndexedDBMediaStoreModule
import net.folivo.trixnity.client.store.repository.indexeddb.createIndexedDBRepositoriesModule
import org.koin.core.module.Module
import org.koin.core.scope.Scope

class JsPlatform : Platform {
    override val name: String = "Web with Kotlin/JS"
    override suspend fun getRepositoriesModule(): Module = createIndexedDBRepositoriesModule()
    override suspend fun getMediaStoreModule(): Module = createIndexedDBMediaStoreModule()
}

actual fun Scope.getPlatform(): Platform = JsPlatform()
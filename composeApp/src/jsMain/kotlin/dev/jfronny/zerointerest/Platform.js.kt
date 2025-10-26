package dev.jfronny.zerointerest

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import net.folivo.trixnity.client.media.indexeddb.createIndexedDBMediaStoreModule
import net.folivo.trixnity.client.store.repository.indexeddb.createIndexedDBRepositoriesModule
import org.koin.core.module.Module
import org.koin.core.scope.Scope

class JsPlatform : Platform {
    override val name: String = "Web with Kotlin/JS"
    override suspend fun getRepositoriesModule(): Module = createIndexedDBRepositoriesModule()
    override suspend fun getMediaStoreModule(): Module = createIndexedDBMediaStoreModule()
    override fun getHttpClientEngine(): HttpClientEngine = Js.create {}
}

actual fun Scope.getPlatform(): Platform = JsPlatform()

@Composable
actual fun getPlatformTheme(darkTheme: Boolean): ColorScheme? = null
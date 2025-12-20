package dev.jfronny.zerointerest

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.WebStorage
import androidx.datastore.core.okio.WebStorageType
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import dev.jfronny.zerointerest.service.SummaryTrustDatabase
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import net.folivo.trixnity.client.media.indexeddb.createIndexedDBMediaStoreModule
import net.folivo.trixnity.client.store.repository.indexeddb.createIndexedDBRepositoriesModule
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module

class JsPlatform : Platform {
    override val name: String = "Web"
    override suspend fun getRepositoriesModule(): Module = createIndexedDBRepositoriesModule()
    override suspend fun getMediaStoreModule(): Module = createIndexedDBMediaStoreModule()
    override fun getHttpClientEngine(): HttpClientEngine = Js.create {}
    override fun createDataStore(): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(WebStorage(
            name = "zerointerest",
            serializer = PreferencesSerializer,
            storageType = WebStorageType.SESSION //TODO: use non-session storage once available
        ))
    }
}

actual fun Scope.getPlatform(): Platform = JsPlatform()

@Composable
actual fun getPlatformTheme(darkTheme: Boolean): ColorScheme? = null

actual fun createExtraModule() = module {
    single {
        WebSummaryTrustDatabase()
    } bind SummaryTrustDatabase::class
}

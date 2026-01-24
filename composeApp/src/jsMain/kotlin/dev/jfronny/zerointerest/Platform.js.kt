package dev.jfronny.zerointerest

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.WebStorage
import androidx.datastore.core.okio.WebStorageType
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.media.indexeddb.indexedDB
import de.connect2x.trixnity.client.store.repository.indexeddb.indexedDB
import dev.jfronny.zerointerest.service.SummaryTrustDatabase
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module

class JsPlatform : Platform {
    override val name: String = "Web"
    override suspend fun getRepositoriesModule(): RepositoriesModule = RepositoriesModule.indexedDB()
    override suspend fun getMediaStoreModule(): MediaStoreModule = MediaStoreModule.indexedDB()
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

actual fun createExtraModule() = module {
    single {
        WebSummaryTrustDatabase()
    } bind SummaryTrustDatabase::class
}

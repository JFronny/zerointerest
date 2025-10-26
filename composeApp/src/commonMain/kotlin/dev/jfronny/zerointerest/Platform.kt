package dev.jfronny.zerointerest

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.core.scope.Scope

interface Platform {
    val name: String

    suspend fun getRepositoriesModule(): Module
    suspend fun getMediaStoreModule(): Module
    fun getHttpClientEngine(): HttpClientEngine
}

expect fun Scope.getPlatform(): Platform
@Composable
expect fun getPlatformTheme(darkTheme: Boolean): ColorScheme?

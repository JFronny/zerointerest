package dev.jfronny.zerointerest

import org.koin.core.module.Module
import org.koin.core.scope.Scope

interface Platform {
    val name: String

    suspend fun getRepositoriesModule(): Module
    suspend fun getMediaStoreModule(): Module
}

expect fun Scope.getPlatform(): Platform
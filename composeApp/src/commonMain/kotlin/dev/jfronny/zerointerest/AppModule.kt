package dev.jfronny.zerointerest

import kotlinx.coroutines.sync.Mutex
import org.koin.core.module.Module
import org.koin.dsl.module

fun createAppModule(): Module {
    return module {
        single { getPlatform() }
        single { MatrixClientService(get()) }
    }
}

class SuspendLazy<T>(private val block: suspend () -> T) {
    private val mutex = Mutex()
    private var value: T? = null
    suspend fun get(): T {
        val v = value
        if (v != null) return v
        mutex.lock()
        try {
            val v2 = value
            if (v2 != null) return v2
            val created = block()
            value = created
            return created
        } finally {
            mutex.unlock()
        }
    }
}

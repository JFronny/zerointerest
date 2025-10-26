package dev.jfronny.zerointerest

import kotlinx.coroutines.sync.Mutex
import org.koin.core.module.Module
import org.koin.dsl.module

fun createAppModule(): Module {
    return module {
        single { getPlatform() }
    }
}

abstract class SuspendBox<T> {
    private val mutex = Mutex()
    private var value: T? = null
    suspend fun get(): T {
        val v = value
        if (v != null) return v
        mutex.lock()
        try {
            val v2 = value
            if (v2 != null) return v2
            val created = doGet()
            value = created
            return created
        } finally {
            mutex.unlock()
        }
    }

    abstract suspend fun doGet(): T
}

package dev.jfronny.zerointerest

import dev.jfronny.zerointerest.data.ZeroInterestEventContent
import kotlinx.coroutines.sync.Mutex
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.messageOf
import org.koin.dsl.module

fun createAppModule() = module {
    single { getPlatform() }
    single { MatrixClientService(get()) }
}

fun createAppMatrixModule() = module {
    single<EventContentSerializerMappings> {
        DefaultEventContentSerializerMappings + createEventContentSerializerMappings {
            messageOf<ZeroInterestEventContent>(ZeroInterestEventContent.TYPE)
        }
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

package dev.jfronny.zerointerest

import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.service.MatrixClientService
import dev.jfronny.zerointerest.service.SummaryTrustService
import kotlinx.coroutines.sync.Mutex
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.messageOf
import net.folivo.trixnity.core.serialization.events.stateOf
import org.koin.dsl.module

fun createAppModule() = module {
    single { getPlatform() }
    single { MatrixClientService(get()) }
    single { SummaryTrustService(get(), get()) }
}

fun createAppMatrixModule() = module {
    single<EventContentSerializerMappings> {
        DefaultEventContentSerializerMappings + createEventContentSerializerMappings {
            stateOf<ZeroInterestSummaryEvent>(ZeroInterestSummaryEvent.TYPE)
            messageOf<ZeroInterestTransactionEvent>(ZeroInterestTransactionEvent.TYPE)
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

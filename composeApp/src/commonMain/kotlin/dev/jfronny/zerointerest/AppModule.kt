package dev.jfronny.zerointerest

import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.core.serialization.events.invoke
import de.connect2x.trixnity.core.serialization.events.messageOf
import de.connect2x.trixnity.core.serialization.events.stateOf
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.service.MatrixClientService
import dev.jfronny.zerointerest.service.Settings
import dev.jfronny.zerointerest.service.SummaryTrustService
import dev.jfronny.zerointerest.service.createSsoLoginHandler
import kotlinx.coroutines.sync.Mutex
import org.koin.dsl.module

const val SourceCodeUrl = "https://git.jfronny.dev/Johannes/zerointerest"

fun createAppModule() = module {
    single { getPlatform() }
    single { createSsoLoginHandler() }
    single { MatrixClientService(get(), get()) }
    single { SummaryTrustService(get(), get()) }
    single { Settings(get<Platform>().createDataStore()) }
}

fun createAppMatrixModule() = module {
    single<EventContentSerializerMappings> {
        EventContentSerializerMappings.default(EventContentSerializerMappings {
            stateOf<ZeroInterestSummaryEvent>(ZeroInterestSummaryEvent.TYPE)
            messageOf<ZeroInterestTransactionEvent>(ZeroInterestTransactionEvent.TYPE)
        })
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

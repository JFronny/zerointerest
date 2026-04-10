package dev.jfronny.zerointerest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.sqlite.SQLiteDriver
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
import dev.jfronny.zerointerest.service.ZeroInterestDatabase
import dev.jfronny.zerointerest.service.createSsoLoginHandler
import dev.jfronny.zerointerest.service.db.Migration1_2
import dev.jfronny.zerointerest.service.db.Migration2_3
import dev.jfronny.zerointerest.service.db.Migration3_4
import dev.jfronny.zerointerest.service.db.RoomZeroInterestDatabase
import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
import org.koin.compose.currentKoinScope
import org.koin.core.qualifier.Qualifier
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module

const val SourceCodeUrl = "https://git.jfronny.dev/Johannes/zerointerest"

fun createAppModule() = module {
    single { getPlatform() }
    single { HttpClient(get<Platform>().getHttpClientEngine()) }
    single { createSsoLoginHandler() }
    single { MatrixClientService(get(), get()) }
    single { SummaryTrustService(get(), get()) }
    single { Settings(get<Platform>().createDataStore()) }
    single {
        get<Platform>()
            .zerointerestDatabaseBuilder()
            .setDriver(get<SQLiteDriver>())
            .addMigrations(Migration1_2, Migration2_3, Migration3_4)
            .build()
    }
    single {
        RoomZeroInterestDatabase(get())
            .apply { get<Platform>().handleZerointerestDatabase(this@apply) }
    } bind ZeroInterestDatabase::class
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

@Composable
inline fun <reified T> koinInjectOrNull(
    qualifier: Qualifier? = null,
    scope: Scope = currentKoinScope()
): T? = remember(qualifier, scope) {
    scope.getOrNull(qualifier)
}

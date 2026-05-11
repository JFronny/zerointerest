package dev.jfronny.zerointerest.service

import androidx.room3.Room
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.client.TestZiClient
import dev.jfronny.zerointerest.client.TestZiServer
import dev.jfronny.zerointerest.createSQLiteDriver
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.db.ZeroInterestDatabase
import dev.jfronny.zerointerest.db.ZeroInterestRoomDatabase
import dev.jfronny.zerointerest.inMemoryDatabaseBuilder
import dev.jfronny.zerointerest.service.client.ZiClient
import dev.jfronny.zerointerest.service.client.ZiClientProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeout
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.time.Duration.Companion.milliseconds

abstract class CoreServicesTest : KoinTest, FunSpec() {
    val roomId = RoomId("!test:example.com")
    val testUser = UserId("@testuser:example.com")
    val alice = UserId("@alice:example.com")
    val bob = UserId("@bob:example.com")

    init {
        extension(KoinExtension(module {
            single { createSQLiteDriver() }
            single {
                Room.inMemoryDatabaseBuilder<ZeroInterestRoomDatabase>()
                    .setDriver(get())
                    .build()
            }
            single { ZeroInterestDatabase(get()) }
            single { TestZiServer() }
            single { TestZiClient(get(), userId = testUser) } bind ZiClient::class
            single<ZiClientProvider> { object : ZiClientProvider {
                override fun get(): ZiClient = get<ZiClient>()
            } }
            single { TransactionService(get(), get()) }
            single { SummaryTrustService(get(), get()) }
        }, mode = KoinLifecycleMode.Test))
    }

    suspend fun SummaryTrustService.checkTrusted(
        roomId: RoomId = this@CoreServicesTest.roomId,
        messageId: EventId? = null,
        expectedMessageId: EventId? = null,
    ) = if (messageId == null) {
        val server by inject<TestZiServer>()
        val current = server.stateEvents[roomId to ZeroInterestSummaryEvent.TYPE]!!
        if (expectedMessageId != null) current.id shouldBe expectedMessageId
        checkTrusted(roomId, current.id, current.originTimestamp, current.content as ZeroInterestSummaryEvent)
    } else {
        expectedMessageId shouldBe null
        val client by inject<TestZiClient>()
        val event = client.getSummaryEventWithTimeout(roomId, messageId)!!.getOrThrow()
        checkTrusted(roomId, messageId, event.ts, event.value)
    }

    suspend fun <T> Flow<T>.getValue() = withTimeout(500.milliseconds) {
        firstOrNull()
    }
}

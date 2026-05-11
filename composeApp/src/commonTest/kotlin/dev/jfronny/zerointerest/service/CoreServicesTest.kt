package dev.jfronny.zerointerest.service

import androidx.room3.Room
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import dev.jfronny.zerointerest.client.TestZiClient
import dev.jfronny.zerointerest.client.TestZiServer
import dev.jfronny.zerointerest.client.exportHistory
import dev.jfronny.zerointerest.client.restoreHistory
import dev.jfronny.zerointerest.client.toGraphviz
import dev.jfronny.zerointerest.createSQLiteDriver
import dev.jfronny.zerointerest.data.TrustState
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.db.ZeroInterestDatabase
import dev.jfronny.zerointerest.db.ZeroInterestRoomDatabase
import dev.jfronny.zerointerest.inMemoryDatabaseBuilder
import dev.jfronny.zerointerest.readTestResource
import dev.jfronny.zerointerest.service.client.ZiClient
import dev.jfronny.zerointerest.service.client.ZiClientProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

class CoreServicesTest : KoinTest, FunSpec() {
    init {
        val roomId = RoomId("!test:example.com")
        val testUser = UserId("@testuser:example.com")

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

        test("TransactionService creates initial summary") {
            val server by inject<TestZiServer>()
            val client by inject<TestZiClient>()
            val summaryService by inject<SummaryTrustService>()
            val transactionService by inject<TransactionService>()

            val content = ZeroInterestTransactionEvent(
                description = "Dinner",
                sender = UserId("@alice:example.com"),
                receivers = mapOf(UserId("@bob:example.com") to 100L)
            )

            transactionService.sendTransaction(roomId, content)
            client.sync()

            val summaryFlow = summaryService.getSummary(roomId)
            val summary = summaryFlow.first { it !is SummaryTrustService.Summary.Empty }

            summary::class shouldBe SummaryTrustService.Summary.Trusted::class

            server.toGraphviz() shouldBe readTestResource("initial_summary.dot")
        }
        test("SummaryTrustService accepts history_starter") {
            val server by inject<TestZiServer>()
            val client by inject<TestZiClient>()
            val summaryService by inject<SummaryTrustService>()

            server.restoreHistory(readTestResource("history_starter.json"))
            client.sync()

            val current = server.stateEvents[roomId to ZeroInterestSummaryEvent.TYPE]!!

            current.id shouldBe EventId($$"$4")

            summaryService.checkTrusted(roomId, current.id, current.originTimestamp, current.content as ZeroInterestSummaryEvent) shouldBe TrustState.TRUSTED

            server.toGraphviz() shouldBe readTestResource("history_starter.dot")
        }
        test("SummaryTrustService forceAccept recursively accepts parents") {
            val server by inject<TestZiServer>()
            val client by inject<TestZiClient>()
            val summaryService by inject<SummaryTrustService>()

            server.restoreHistory(readTestResource("history_starter.json"))

            client.sync()

            val initialId = EventId($$"$0")
            val s1Id = EventId($$"$2")
            val s2Id = EventId($$"$4")
            val s2Event = client.getSummaryEventWithTimeout(roomId, s2Id)!!.getOrThrow().value

            // Add dummy rejection to make ensure trust fails
            client.server.reactions.getOrPut(roomId to s1Id) { mutableMapOf() }.getOrPut("\uD83D\uDC4E") { mutableSetOf(
                de.connect2x.trixnity.client.store.TimelineEvent(
                    event = ClientEvent.RoomEvent.MessageEvent(
                        content = RoomMessageEventContent.TextBased.Text(""),
                        id = EventId("\$rejection"),
                        sender = client.userId,
                        roomId = roomId,
                        originTimestamp = 6
                    ),
                    previousEventId = null, nextEventId = null, gap = null
                )
            ) }

            val trustStateS2 = summaryService.checkTrusted(roomId, s2Id, 5, s2Event)
            trustStateS2 shouldBe TrustState.REJECTED

            // Now force accept S2
            summaryService.forceAccept(SummaryTrustService.Summary.Untrusted(roomId, s2Id, s2Event))

            // It should have accepted S2, S1, and initial
            val db by inject<ZeroInterestDatabase>()
            db.checkTrust(roomId, s2Id) shouldBe TrustState.TRUSTED
            db.checkTrust(roomId, s1Id) shouldBe TrustState.TRUSTED
            db.checkTrust(roomId, initialId) shouldBe TrustState.TRUSTED

            // Also it should have redacted the rejection!
            // Redaction mocks in TestZiClient just remove the reaction from server.reactions
            val reactions = server.reactions[roomId to s1Id]?.get("\uD83D\uDC4E")
            reactions?.isEmpty() shouldBe true
            println("Reactions size: ${reactions?.size}")
        }
    }
}

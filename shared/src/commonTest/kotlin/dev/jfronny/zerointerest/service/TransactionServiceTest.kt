package dev.jfronny.zerointerest.service

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import dev.jfronny.zerointerest.client.TestZiClient
import dev.jfronny.zerointerest.client.TestZiServer
import dev.jfronny.zerointerest.client.restoreHistory
import dev.jfronny.zerointerest.client.toGraphviz
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.data.ZiConfigStateEvent
import dev.jfronny.zerointerest.data.money.toMoney
import dev.jfronny.zerointerest.db.ZeroInterestDatabase
import dev.jfronny.zerointerest.readTestResource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.koin.test.inject

class TransactionServiceTest : CoreServicesTest() {
    init {
        test("TransactionService creates initial summary") {
            val server by inject<TestZiServer>()
            val client by inject<TestZiClient>()
            val summaryService by inject<SummaryTrustService>()
            val transactionService by inject<TransactionService>()

            val content = ZeroInterestTransactionEvent(
                description = "Dinner",
                sender = UserId("@alice:example.com"),
                receivers = mapOf(UserId("@bob:example.com") to 100L.toMoney()),
            )

            transactionService.sendTransaction(roomId, content)
            client.sync()

            val summaryFlow = summaryService.getSummary(roomId).filterNotNull()
            val summary = summaryFlow.first { it !is SummaryTrustService.Summary.Empty }

            summary::class shouldBe SummaryTrustService.Summary.Trusted::class

            server.toGraphviz() shouldBe readTestResource("initial_summary.dot")
        }

        test("TransactionService creates transactions and summaries seamlessly on top of imported history") {
            val server by inject<TestZiServer>()
            val client by inject<TestZiClient>()
            val summaryService by inject<SummaryTrustService>()
            val transactionService by inject<TransactionService>()

            server.restoreHistory(readTestResource("history_starter.json"))
            client.sync()

            // Wait for history to be trusted
            val summaryFlow = summaryService.getSummary(roomId)
            summaryFlow.first { it is SummaryTrustService.Summary.Trusted }

            // Create one more transaction on top of the restored history
            transactionService.sendTransaction(roomId, ZeroInterestTransactionEvent("Tx 3", alice, mapOf(bob to 5L.toMoney())))
            client.sync()

            val updatedSummary = summaryFlow.first {
                it is SummaryTrustService.Summary.Trusted &&
                    it.event.parents.keys.first().full == $$"$4" // Should refer to the last summary from starter history
            } as SummaryTrustService.Summary.Trusted

            updatedSummary.event.balances[alice] shouldBe (-10L).toMoney()
            updatedSummary.event.balances[bob] shouldBe 10L.toMoney()
        }

        test("TransactionService merges multiple heads correctly") {
            val server by inject<TestZiServer>()
            val client by inject<TestZiClient>()
            val summaryService by inject<SummaryTrustService>()
            val transactionService by inject<TransactionService>()

            server.restoreHistory(readTestResource("history_heads_part1.json"))
            client.sync()

            // Wait for history to be trusted
            val summaryFlow = summaryService.getSummary(roomId)
            summaryFlow.first { it is SummaryTrustService.Summary.Trusted }

            // Restore second part that introduces a new head
            server.restoreHistory(readTestResource("history_heads_part2.json"), allowAppend = true)
            client.sync()

            // Collect the flow again to trigger trust checking
            summaryFlow.first { it is SummaryTrustService.Summary.Trusted && it.isMerge }

            val db by inject<ZeroInterestDatabase>()
            db.getHeadsFlow(roomId).first { it.size == 2 }

            // Sending a transaction merges the heads
            transactionService.sendTransaction(roomId, ZeroInterestTransactionEvent("Tx C", alice, mapOf(bob to 10L.toMoney())))
            client.sync()

            server.toGraphviz() shouldBe readTestResource("history_heads.dot")
        }

        test("TransactionService refuses to write to rooms with a newer protocol version") {
            val server by inject<TestZiServer>()
            val client by inject<TestZiClient>()
            val transactionService by inject<TransactionService>()

            server.stateEvents[roomId to ZiConfigStateEvent.TYPE] = ClientEvent.RoomEvent.StateEvent(
                content = ZiConfigStateEvent.Newer(2),
                id = server.nextEventId(),
                sender = bob,
                roomId = roomId,
                originTimestamp = server.nextTimestamp(),
                stateKey = ZiConfigStateEvent.TYPE,
            )
            client.sync()

            client.getRoomConfigFlow(roomId).first() shouldBe ZiConfigStateEvent.Newer(2)

            runCatching {
                transactionService.sendTransaction(roomId, ZeroInterestTransactionEvent("Tx", alice, mapOf(bob to 5L.toMoney())))
            }.exceptionOrNull().shouldBeInstanceOf<TransactionService.UnsupportedRoomProtocolException>()
            runCatching {
                transactionService.sendTransactions(roomId, listOf(ZeroInterestTransactionEvent("Tx", alice, mapOf(bob to 5L.toMoney()))))
            }.exceptionOrNull().shouldBeInstanceOf<TransactionService.UnsupportedRoomProtocolException>()
            runCatching {
                transactionService.createSummary(
                    transactionService.prepareSummaryCreation(roomId, emptyList()),
                    emptyList(),
                )
            }.exceptionOrNull().shouldBeInstanceOf<TransactionService.UnsupportedRoomProtocolException>()
        }

        test("sending a ZiConfigStateEvent upgrades the room to protocol version 1 with a room currency") {
            val client by inject<TestZiClient>()

            client.getRoomConfigFlow(roomId).first() shouldBe ZiConfigStateEvent.V0()

            client.sendStateEvent(
                roomId,
                ZiConfigStateEvent.V1(rawCurrency = "EUR"),
                ZiConfigStateEvent.TYPE,
            )
            client.sync()

            client.getRoomConfigFlow(roomId).first() shouldBe ZiConfigStateEvent.V1(rawCurrency = "EUR")
        }
    }
}

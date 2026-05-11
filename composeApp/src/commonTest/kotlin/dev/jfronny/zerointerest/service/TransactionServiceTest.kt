package dev.jfronny.zerointerest.service

import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.client.TestZiClient
import dev.jfronny.zerointerest.client.TestZiServer
import dev.jfronny.zerointerest.client.restoreHistory
import dev.jfronny.zerointerest.client.toGraphviz
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.db.ZeroInterestDatabase
import dev.jfronny.zerointerest.readTestResource
import io.kotest.matchers.shouldBe
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
                receivers = mapOf(UserId("@bob:example.com") to 100L)
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
            transactionService.sendTransaction(roomId, ZeroInterestTransactionEvent("Tx 3", alice, mapOf(bob to 5L)))
            client.sync()

            val updatedSummary = summaryFlow.first {
                it is SummaryTrustService.Summary.Trusted &&
                        it.event.parents.keys.first().full == $$"$4" // Should refer to the last summary from starter history
            } as SummaryTrustService.Summary.Trusted

            updatedSummary.event.balances[alice] shouldBe -10L
            updatedSummary.event.balances[bob] shouldBe 10L
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
            transactionService.sendTransaction(roomId, ZeroInterestTransactionEvent("Tx C", alice, mapOf(bob to 10L)))
            client.sync()

            server.toGraphviz() shouldBe readTestResource("history_heads.dot")
        }
    }
}

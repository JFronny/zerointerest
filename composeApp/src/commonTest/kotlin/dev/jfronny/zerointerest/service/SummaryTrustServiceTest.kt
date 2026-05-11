package dev.jfronny.zerointerest.service

import de.connect2x.trixnity.core.model.EventId
import dev.jfronny.zerointerest.client.TestZiClient
import dev.jfronny.zerointerest.client.TestZiServer
import dev.jfronny.zerointerest.client.restoreHistory
import dev.jfronny.zerointerest.client.toGraphviz
import dev.jfronny.zerointerest.data.TrustState
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.db.ZeroInterestDatabase
import dev.jfronny.zerointerest.readTestResource
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.filterNotNull
import org.koin.test.inject

class SummaryTrustServiceTest : CoreServicesTest() {
    init {
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
        test("SummaryTrustService considers explicit rejection") {
            val server by inject<TestZiServer>()
            val client by inject<TestZiClient>()
            val summaryService by inject<SummaryTrustService>()

            server.restoreHistory(readTestResource("history_explicit_reject.json"))
            client.sync()

            val current = server.stateEvents[roomId to ZeroInterestSummaryEvent.TYPE]!!
            current.id shouldBe EventId($$"$4")

            summaryService.checkTrusted(roomId, current.id, current.originTimestamp, current.content as ZeroInterestSummaryEvent) shouldBe TrustState.REJECTED
        }
        test("SummaryTrustService forceAccept recursively accepts parents") {
            val server by inject<TestZiServer>()
            val client by inject<TestZiClient>()
            val summaryService by inject<SummaryTrustService>()

            server.restoreHistory(readTestResource("history_explicit_reject.json"))
            client.sync()

            val initialId = EventId($$"$0")
            val s1Id = EventId($$"$2")
            val s2Id = EventId($$"$4")

            val trustStateS2 = summaryService.checkTrusted(roomId, s2Id)
            trustStateS2 shouldBe TrustState.REJECTED
            val summary = summaryService.getSummary(roomId).filterNotNull().getValue()

            require(summary is SummaryTrustService.Summary.Untrusted)
            summary.messageId shouldBe s2Id

            // Now force accept S2
            summaryService.forceAccept(summary)

            // It should have accepted S2, S1, and initial
            val db by inject<ZeroInterestDatabase>()
            db.checkTrust(roomId, s2Id) shouldBe TrustState.TRUSTED
            db.checkTrust(roomId, s1Id) shouldBe TrustState.TRUSTED
            db.checkTrust(roomId, initialId) shouldBe TrustState.TRUSTED

            // Also it should have redacted the rejection!
            // Redaction mocks in TestZiClient just remove the reaction from server.reactions
            val reactions = server.reactions[roomId to s1Id]?.get(SummaryTrustService.rejectionKey)
            reactions?.isEmpty() shouldBe true
        }
        test("TrustService rejects summary with incorrect balances") {
            val server by inject<TestZiServer>()
            val client by inject<TestZiClient>()
            val summaryService by inject<SummaryTrustService>()

            server.restoreHistory(readTestResource("history_bad_balance.json"))
            client.sync()

            summaryService.checkTrusted(roomId, expectedMessageId = EventId($$"$2")) shouldBe TrustState.REJECTED
        }
    }
}

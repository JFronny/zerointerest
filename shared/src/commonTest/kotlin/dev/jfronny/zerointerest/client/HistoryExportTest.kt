package dev.jfronny.zerointerest.client

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.data.money.toMoney
import dev.jfronny.zerointerest.readTestResource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.collections.emptyMap

class HistoryExportTest : FunSpec() {
    init {
        val roomId = RoomId("!test:example.com")
        val alice = UserId("@alice:example.com")
        val bob = UserId("@bob:example.com")

        test("exportHistory works as expected") {
            val server = TestZiServer()

            // 1. Initial Summary
            val initialEvent = ZeroInterestSummaryEvent(emptyMap(), emptyMap())
            val initialId = EventId("\$0")
            val initialClientEvent = ClientEvent.RoomEvent.StateEvent(
                content = initialEvent,
                id = initialId,
                sender = alice,
                roomId = roomId,
                originTimestamp = 0,
                stateKey = ZeroInterestSummaryEvent.TYPE,
            )
            server.eventHistory.add(roomId to initialClientEvent)
            server.stateEvents[roomId to ZeroInterestSummaryEvent.TYPE] = initialClientEvent

            // 2. Tx 1
            val tx1Event = ZeroInterestTransactionEvent("Tx1", alice, mapOf(bob to 10L.toMoney()))
            val tx1Id = EventId("\$1")
            server.eventHistory.add(
                roomId to ClientEvent.RoomEvent.MessageEvent(
                    content = tx1Event,
                    id = tx1Id,
                    sender = alice,
                    roomId = roomId,
                    originTimestamp = 1,
                ),
            )

            // 3. Summary 1 (untrusted at first due to db being fresh, but wait, it's not first so it needs checks)
            val s1Event = ZeroInterestSummaryEvent(mapOf(alice to (-10L).toMoney(), bob to 10L.toMoney()), mapOf(initialId to setOf(tx1Id)))
            val s1Id = EventId("\$2")
            val s1ClientEvent = ClientEvent.RoomEvent.StateEvent(
                content = s1Event,
                id = s1Id,
                sender = alice,
                roomId = roomId,
                originTimestamp = 2,
                stateKey = ZeroInterestSummaryEvent.TYPE,
            )
            server.eventHistory.add(roomId to s1ClientEvent)
            server.stateEvents[roomId to ZeroInterestSummaryEvent.TYPE] = s1ClientEvent

            // 4. Tx 2
            val tx2Event = ZeroInterestTransactionEvent("Tx2", bob, mapOf(alice to 5L.toMoney()))
            val tx2Id = EventId("\$3")
            server.eventHistory.add(
                roomId to ClientEvent.RoomEvent.MessageEvent(
                    content = tx2Event,
                    id = tx2Id,
                    sender = bob,
                    roomId = roomId,
                    originTimestamp = 3,
                ),
            )

            // 5. Summary 2
            val s2Event = ZeroInterestSummaryEvent(mapOf(alice to (-5L).toMoney(), bob to 5L.toMoney()), mapOf(s1Id to setOf(tx2Id)))
            val s2Id = EventId("\$4")
            val s2ClientEvent = ClientEvent.RoomEvent.StateEvent(
                content = s2Event,
                id = s2Id,
                sender = bob,
                roomId = roomId,
                originTimestamp = 4,
                stateKey = ZeroInterestSummaryEvent.TYPE,
            )
            server.eventHistory.add(roomId to s2ClientEvent)
            server.stateEvents[roomId to ZeroInterestSummaryEvent.TYPE] = s2ClientEvent

            server.exportHistory() shouldBe readTestResource("history_starter.json")

            val server2 = TestZiServer()
            server2.restoreHistory(server.exportHistory())

            server2.eventHistory shouldBe server.eventHistory
        }
        test("restoreHistory is consistent") {
            val server = TestZiServer()
            val starter = readTestResource("history_starter.json")
            server.restoreHistory(starter)
            server.exportHistory() shouldBe starter
        }
    }
}

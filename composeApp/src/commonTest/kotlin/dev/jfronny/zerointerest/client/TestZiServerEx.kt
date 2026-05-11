package dev.jfronny.zerointerest.client

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.ReactionEventContent
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json {
    prettyPrint = true
}

suspend fun TestZiServer.exportHistory(): String = json.encodeToString(buildList {
    eventHistory.forEach { (roomId, event) ->
        when (event) {
            is ClientEvent.RoomEvent.StateEvent -> {
                add(TestZiEvent.Summary(event.sender, roomId, event.content as ZeroInterestSummaryEvent))
            }
            is ClientEvent.RoomEvent.MessageEvent -> {
                when (val content = event.content) {
                    is ZeroInterestTransactionEvent -> {
                        add(TestZiEvent.Transaction(event.sender, roomId, content))
                    }
                    is ReactionEventContent -> {
                        val relatesTo = content.relatesTo!!
                        add(TestZiEvent.Reaction(event.sender, roomId, relatesTo.eventId, relatesTo.key!!))
                    }
                }
            }
        }
    }
})

suspend fun TestZiServer.restoreHistory(historyJson: String) {
    nextUserId shouldBe 0
    nextEventId shouldBe 0
    val userClients = mutableMapOf<UserId, TestZiClient>()
    for (event in json.decodeFromString<List<TestZiEvent>>(historyJson)) {
        val client = userClients.getOrPut(event.sender) { TestZiClient(this, event.sender) }
        when (event) {
            is TestZiEvent.Reaction -> client.reactToEvent(event.roomId, event.event, event.key)
            is TestZiEvent.Summary -> client.sendStateEvent(event.roomId, event.event, ZeroInterestSummaryEvent.TYPE)
            is TestZiEvent.Transaction -> {
                val txId = client.scheduleMessageEvent(event.roomId, event.event).getOrThrow()
                client.awaitScheduledMessageEvent(event.roomId, txId).getOrThrow()
            }
        }
    }
}

@Serializable
private sealed interface TestZiEvent {
    val sender: UserId
    val roomId: RoomId
    @Serializable @SerialName("transaction") data class Transaction(
        override val sender: UserId,
        override val roomId: RoomId,
        val event: ZeroInterestTransactionEvent,
    ) : TestZiEvent
    @Serializable @SerialName("summary") data class Summary(
        override val sender: UserId,
        override val roomId: RoomId,
        val event: ZeroInterestSummaryEvent,
    ): TestZiEvent
    @Serializable @SerialName("reaction") data class Reaction(
        override val sender: UserId,
        override val roomId: RoomId,
        val event: EventId,
        val key: String,
    ): TestZiEvent
}

fun TestZiServer.toGraphviz(): String {
    val builder = StringBuilder()
    builder.appendLine("digraph G {")
    builder.appendLine("  node [shape=box];")

    // Collect all events
    val summaries = mutableListOf<ClientEvent.RoomEvent.StateEvent<ZeroInterestSummaryEvent>>()
    val transactions = mutableListOf<ClientEvent.RoomEvent.MessageEvent<ZeroInterestTransactionEvent>>()

    for ((_, event) in eventHistory) {
        if (event is ClientEvent.RoomEvent.StateEvent<*> && event.content is ZeroInterestSummaryEvent) {
            summaries.add(event as ClientEvent.RoomEvent.StateEvent<ZeroInterestSummaryEvent>)
        } else if (event is ClientEvent.RoomEvent.MessageEvent<*> && event.content is ZeroInterestTransactionEvent) {
            transactions.add(event as ClientEvent.RoomEvent.MessageEvent<ZeroInterestTransactionEvent>)
        }
    }

    // Nodes for transactions
    for (tx in transactions) {
        val desc = tx.content.description.replace("\"", "\\\"")
        val sender = tx.sender.full.substringBefore(":")
        val receivers = tx.content.receivers.entries.joinToString(", ") { "${it.key.full.substringBefore(":")}:${it.value}" }
        val label = "Tx ${tx.id.full}\\n$sender ->\\n$receivers\\n$desc"
        builder.appendLine("  \"${tx.id.full}\" [label=\"$label\", style=filled, fillcolor=lightblue];")
    }

    // Nodes for summaries
    for (summary in summaries) {
        val balances = summary.content.balances.entries.joinToString("\\n") { "${it.key.full.substringBefore(":")}: ${it.value}" }
        val label = "Summary ${summary.id.full}\\n$balances"
        builder.appendLine("  \"${summary.id.full}\" [label=\"$label\", style=filled, fillcolor=lightgreen];")

        // Edges for parents (summaries -> previous summaries and summaries -> transactions)
        for ((parentSummaryId, parentTxIds) in summary.content.parents) {
            val indId = "ind_${summary.id.full}_${parentSummaryId.full}"
            builder.appendLine("  \"$indId\" [shape=point];")
            builder.appendLine("  \"${summary.id.full}\" -> \"${indId}\" [color=green];")
            builder.appendLine("  \"${indId}\" -> \"${parentSummaryId.full}\" [color=green];")
            for (txId in parentTxIds) {
                builder.appendLine("  \"${indId}\" -> \"${txId.full}\" [color=blue];")
            }
        }
    }

    builder.appendLine("}")
    return builder.toString()
}

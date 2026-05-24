package dev.jfronny.zerointerest.client

import de.connect2x.trixnity.core.model.events.ClientEvent
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent

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

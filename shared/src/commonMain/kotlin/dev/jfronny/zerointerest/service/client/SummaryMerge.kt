package dev.jfronny.zerointerest.service.client

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

suspend fun ZiClient.computeMergedSummary(
    roomId: RoomId,
    heads: Map<EventId, ZeroInterestSummaryEvent>,
    newTransactionIds: List<EventId> = emptyList(),
    newTransactions: List<ZeroInterestTransactionEvent> = emptyList(),
): ZeroInterestSummaryEvent {
    require(newTransactionIds.size == newTransactions.size) { "newTransactionIds and newTransactions must have the same size" }

    // 2. Calculate the union of history for all heads
    val allVisitedSummaries = mutableSetOf<EventId>()
    val summaryQueue = ArrayDeque<EventId>()
    summaryQueue.addAll(heads.keys)

    val summaryGraph = mutableMapOf<EventId, ZeroInterestSummaryEvent>()

    // Load graph
    log.info { "Building summary graph for room $roomId" }
    while (summaryQueue.isNotEmpty()) {
        val currentId = summaryQueue.removeFirst()
        if (currentId in allVisitedSummaries) continue
        allVisitedSummaries.add(currentId)

        val event = if (currentId in heads) {
            heads[currentId]!!
        } else {
            this.getSummaryEventWithTimeout(roomId, currentId)?.getOrNull()?.value
        } ?: continue

        summaryGraph[currentId] = event
        summaryQueue.addAll(event.parents.keys)
    }

    // Now we have a subgraph in summaryGraph.
    // We need to compute the set of transactions for each head.
    // We can do this by propagating sets of transactions up from the leaves (or common ancestors).

    // Topological sort or just recursive memoized calculation.
    val headTransactions = mutableMapOf<EventId, Set<EventId>>()
    val memoizedHistory = mutableMapOf<EventId, Set<EventId>>()

    fun getHistory(id: EventId): Set<EventId> {
        if (id in memoizedHistory) return memoizedHistory[id]!!
        val event = summaryGraph[id] ?: return emptySet() // Should be in graph if we visited it

        val myHistory = mutableSetOf<EventId>()
        for ((parentId, txs) in event.parents) {
            myHistory.addAll(txs)
            myHistory.addAll(getHistory(parentId))
        }
        memoizedHistory[id] = myHistory
        return myHistory
    }

    // Compute history for all heads
    val allTransactions = mutableSetOf<EventId>()
    for (headId in heads.keys) {
        val h = getHistory(headId)
        headTransactions[headId] = h
        allTransactions.addAll(h)
    }

    // Now compute the new parents map
    val newParents = mutableMapOf<EventId, Set<EventId>>()
    for (headId in heads.keys) {
        val missing = allTransactions - headTransactions[headId]!!
        newParents[headId] = missing + newTransactionIds
    }

    // Compute new balances
    // Take the first head, apply its missing transactions + new transaction
    val baseHeadId = heads.keys.first()
    val baseHeadEvent = heads[baseHeadId]!!
    val baseBalances = baseHeadEvent.balances.toMutableMap()

    val toApply = newParents[baseHeadId]!!

    log.info { "Applying ${toApply.size} transactions to compute new balances for room $roomId" }
    for (txId in toApply) {
        val newIndex = newTransactionIds.indexOf(txId)
        if (newIndex != -1) {
            newTransactions[newIndex].apply(baseBalances)
        } else {
            this.getTransactionEventWithTimeout(roomId, txId)
                ?.getOrNull()
                ?.value
                ?.apply(baseBalances)
        }
    }

    return ZeroInterestSummaryEvent(
        balances = baseBalances,
        parents = newParents,
    )
}

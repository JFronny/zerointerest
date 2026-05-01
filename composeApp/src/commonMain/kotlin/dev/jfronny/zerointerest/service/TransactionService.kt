package dev.jfronny.zerointerest.service

import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.util.getSummaryEventWithTimeout
import dev.jfronny.zerointerest.util.getTransactionEventWithTimeout
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

private val log = KotlinLogging.logger {}

class TransactionService(
    private val clientService: MatrixClientService,
    private val database: ZeroInterestDatabase,
) {
    private val client get() = clientService.get()

    data class PreparedSummary(
        val roomId: RoomId,
        val contents: List<ZeroInterestTransactionEvent>,
        val heads: Map<EventId, ZeroInterestSummaryEvent>,
    )

    suspend fun prepareSummaryCreation(roomId: RoomId, contents: List<ZeroInterestTransactionEvent>): PreparedSummary = withContext(NonCancellable) {
        log.info { "Fetching head events" }
        val heads = database.getHeads(roomId)
        val headEvents = heads.associateWith {
            val first = client.getSummaryEventWithTimeout(roomId, it)
            log.info { "Fetched head $first" }
            first?.getOrNull()?.value
        }.filterValues { it != null }.mapValues { it.value!! }

        if (headEvents.isEmpty() && !heads.isEmpty()) {
            throw IllegalStateException("No heads found for room $roomId while head set is $heads")
        }

        PreparedSummary(
            roomId = roomId,
            contents = contents,
            heads = headEvents,
        )
    }

    suspend fun createSummary(preparedSummary: PreparedSummary, newTransactionIds: List<EventId>) = withContext(NonCancellable) {
        require(preparedSummary.contents.size == newTransactionIds.size) { "Size of contents and newTransactionIds must match" }
        val roomId = preparedSummary.roomId
        val contents = preparedSummary.contents
        val heads = preparedSummary.heads
        if (heads.isEmpty()) {
            log.info { "No heads found for room $roomId, creating initial summary" }

            val initialEvent = ZeroInterestSummaryEvent(
                balances = emptyMap(),
                parents = emptyMap()
            )
            val initialResponse = client.api.room.sendStateEvent(roomId, initialEvent, ZeroInterestSummaryEvent.TYPE).getOrThrow()
            database.addTrustedSummary(roomId, initialResponse, initialEvent, root = true)

            log.info { "Creating summary for first transactions $newTransactionIds in room $roomId" }
            val balances = mutableMapOf<UserId, Long>()
            contents.forEach { it.apply(balances) }
            val event = ZeroInterestSummaryEvent(
                balances = balances,
                parents = mapOf(initialResponse to newTransactionIds.toSet())
            )
            val response = client.api.room.sendStateEvent(roomId, event, ZeroInterestSummaryEvent.TYPE).getOrThrow()
            database.addTrustedSummary(roomId, response, event)
        } else {
            log.info { "Merging ${heads.size} heads for new transactions $newTransactionIds in room $roomId" }
            // Merge heads

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

                val event = if (currentId in heads) heads[currentId]!! else {
                    client.getSummaryEventWithTimeout(roomId, currentId)?.getOrNull()?.value
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
                val event = summaryGraph[id] ?: return emptySet()  // Should be in graph if we visited it

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
                    contents[newIndex].apply(baseBalances)
                } else {
                    client.getTransactionEventWithTimeout(roomId, txId)
                        ?.getOrNull()
                        ?.value
                        ?.apply(baseBalances)
                }
            }

            log.info { "Creating merged summary for new transactions $newTransactionIds in room $roomId" }
            val event = ZeroInterestSummaryEvent(
                balances = baseBalances,
                parents = newParents
            )
            val response = client.api.room.sendStateEvent(roomId, event, ZeroInterestSummaryEvent.TYPE).getOrThrow()
            database.addTrustedSummary(roomId, response, event)
        }
        log.info { "Summary creation for new transactions $newTransactionIds in room $roomId completed" }
    }

    suspend fun sendTransactions(roomId: RoomId, contents: List<ZeroInterestTransactionEvent>) = withContext(NonCancellable) {
        if (contents.isEmpty()) return@withContext
        val preparedSummary = try {
            prepareSummaryCreation(roomId, contents)
        } catch (e: Exception) {
            throw FailedPrepareSummaryException(e)
        }

        val txIds = contents.map { content ->
            try {
                client.room.sendMessage(roomId) {
                    content(content)
                }
            } catch (e: Exception) {
                throw FailedSendMessageException(cause = e)
            }
        }.map { txId -> async {
            val outbox = client.room.getOutbox(roomId, txId)
                .filterNotNull()
                .filter { it.eventId != null || it.sendError != null }
                .firstOrNull()
            if (outbox == null || outbox.sendError != null) {
                throw FailedSendMessageException(outbox?.sendError?.toString() ?: "Outbox entry disappeared for tx $txId")
            }
            outbox.eventId!!
        } }.awaitAll()

        createSummary(preparedSummary, txIds)
    }

    class FailedPrepareSummaryException(cause: Throwable) : Exception("Failed to prepare summary", cause)
    class FailedSendMessageException(message: String = "Failed to send message", cause: Throwable? = null) : Exception(message, cause)

    suspend fun sendTransaction(roomId: RoomId, content: ZeroInterestTransactionEvent) {
        sendTransactions(roomId, listOf(content))
    }
}

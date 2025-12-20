package dev.jfronny.zerointerest.service

import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.any
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.getState
import net.folivo.trixnity.client.room.getTimelineEventReactionAggregation
import net.folivo.trixnity.client.room.message.react
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.originTimestamp
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent

private data class Timed<T>(val ts: Long, val value: T)

private const val rejectionKey = "\uD83D\uDC4E"
private val log = KotlinLogging.logger {}

class SummaryTrustService(
    private val clientService: MatrixClientService,
    private val database: SummaryTrustDatabase
) {
    private val client get() = clientService.get()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getSummary(roomId: RoomId): Flow<Summary> {
        return client.room
            .getState<ZeroInterestSummaryEvent>(roomId, ZeroInterestSummaryEvent.TYPE)
            .mapLatest {
                if (it !is ClientEvent.RoomEvent.StateEvent) {
                    log.info { "No summary found for room $roomId" }
                    return@mapLatest Summary.Empty
                }
                log.info { "Received summary event: $it" }
                val trust = checkTrusted(roomId, it.id, it.originTimestamp, it.content)
                log.info { "Checked trust" }
                if (trust == SummaryTrustDatabase.TrustState.TRUSTED) {
                    log.info { "Trusted summary found for room $roomId" }
                    Summary.Trusted(it.content)
                } else {
                    log.warn { "Latest summary found for room $roomId is not trusted" }
                    Summary.Untrusted(roomId, it.id, it.content)
                }
            }
    }

    sealed interface Summary {
        data class Untrusted(val roomId: RoomId, val messageId: EventId, val content: ZeroInterestSummaryEvent) : Summary
        object Empty : Summary
        data class Trusted(val event: ZeroInterestSummaryEvent) : Summary
    }

    suspend fun checkTrusted(roomId: RoomId, messageId: EventId, timestamp: Long, content: ZeroInterestSummaryEvent): SummaryTrustDatabase.TrustState {
        val dbState = database.checkTrust(roomId, messageId)
        if (dbState != SummaryTrustDatabase.TrustState.UNTRUSTED) return dbState

        log.info { "1. If a rejection for the summary exists, the summary is always rejected." }
        val reactions = client.room.getTimelineEventReactionAggregation(roomId, messageId).first()
        if (reactions.reactions[rejectionKey]?.isNotEmpty() ?: false) return reject(roomId, messageId, send = false)

        log.info { "2. Otherwise, if the summary event is the first summary event ever encountered in a room, it is trusted." }
        if (content.parents.isEmpty()) {
            log.info { "Get events before $messageId" }
            val hasPrevious = client.room.getTimelineEvents(roomId, messageId, direction = GetEvents.Direction.BACKWARDS) {
                maxSize = 1024 // trixnity doesn't easily allow us to filter just for ZeroInterestSummaryEvents, and this should be fine
            }.any { timelineEventFlow ->
                val event = timelineEventFlow.first()
                event.eventId != messageId && event.content?.getOrNull() is ZeroInterestSummaryEvent
            }
            log.info { "Got events before $messageId" }
            if (!hasPrevious) return accept(roomId, messageId, content)
        }

        log.info { "Prefetch events as the next steps need them" }
        val summaries = mutableMapOf<EventId, Timed<ZeroInterestSummaryEvent>>()
        for (eventId in content.parents.keys) {
            val event = client.room.getTimelineEvent(roomId, eventId).filterNotNull().first()
            val content = event.content?.getOrNull() as? ZeroInterestSummaryEvent ?: continue
            summaries[eventId] = Timed(event.originTimestamp, content)
        }
        val transactions = mutableMapOf<EventId, Timed<ZeroInterestTransactionEvent>>()
        for (eventId in content.parents.values.flatten()) {
            val event = client.room.getTimelineEvent(roomId, eventId).filterNotNull().first()
            val content = event.content?.getOrNull() as? ZeroInterestTransactionEvent ?: continue
            transactions[eventId] = Timed(event.originTimestamp, content)
        }

        log.info { "3. Otherwise, if the summary event has no trusted parents, it is rejected." }
        val trustedParents = mutableMapOf<EventId, Set<EventId>>()
        for ((summaryId, transactionIds) in content.parents) {
            val summary = summaries[summaryId] ?: continue
            val trust = checkTrusted(roomId, summaryId, summary.ts, summary.value)
            if (trust == SummaryTrustDatabase.TrustState.TRUSTED) {
                trustedParents[summaryId] = transactionIds
            }
        }
        if (trustedParents.isEmpty()) {
            return reject(roomId, messageId)
        }

        log.info { "4. Otherwise, if the sum of transactions from a parent does not result in the balances, the event is rejected." }
        for ((summaryId, transactionIds) in content.parents) {
            val balances = summaries[summaryId]?.value?.balances ?: continue
            val transactions = transactionIds.mapNotNull { transactions[it]?.value }
            if (transactions.size < transactionIds.size) return reject(roomId, messageId)
            val computedBalances = mutableMapOf<UserId, Long>()
            for (event in transactions) {
                event.apply(computedBalances)
            }
            if (computedBalances != balances) return reject(roomId, messageId)
        }

        log.info { "5. Otherwise, if a common ancestor exists between two parents, and the following transactions differ between them, the event is rejected." }
        for ((summaryId1, summaryId2) in content.parents.keys.toList().combinations()) {
            val summary1 = summaries[summaryId1]?.value ?: continue
            val summary2 = summaries[summaryId2]?.value ?: continue
            // Only checks for direct ancestors. Not technically correct, but good enough for now.
            for (ancestorId in summary1.parents.keys.intersect(summary2.parents.keys)) {
                val transactions1 = summary1.parents[ancestorId]!! + content.parents[summaryId1]!!
                val transactions2 = summary2.parents[ancestorId]!! + content.parents[summaryId2]!!
                if (transactions1 != transactions2) return reject(roomId, messageId)
            }
        }

        log.info { "6. Otherwise, if transactions that are not between the summaries temporally are referenced, the event is rejected." }
        for ((summaryId, transactionIds) in content.parents) {
            val summary = summaries[summaryId] ?: continue
            val transactions = transactionIds.mapNotNull { transactions[it] }
            for (transaction in transactions) {
                if (transaction.ts < summary.ts) return reject(roomId, messageId)
                if (transaction.ts > timestamp) return reject(roomId, messageId)
            }
        }

        log.info { "7. Otherwise, the summary event is trusted." }
        return accept(roomId, messageId, content)
    }

    private suspend fun reject(roomId: RoomId, messageId: EventId, send: Boolean = true): SummaryTrustDatabase.TrustState {
        log.info { "Rejecting $messageId" }
        database.markRejected(roomId, messageId)
        if (send) {
            client.room.sendMessage(roomId) {
                react(messageId, rejectionKey)
            }
        }
        return SummaryTrustDatabase.TrustState.REJECTED
    }

    suspend fun accept(summary: Summary.Untrusted) = accept(summary.roomId, summary.messageId, summary.content)

    suspend fun accept(roomId: RoomId, messageId: EventId, content: ZeroInterestSummaryEvent): SummaryTrustDatabase.TrustState {
        log.info { "Accepting $messageId" }
        database.markTrusted(roomId, messageId)
        database.addHead(roomId, messageId)
        database.removeHeads(roomId, content.parents.keys)
        return SummaryTrustDatabase.TrustState.TRUSTED
    }

    private fun <T> List<T>.combinations(): List<Pair<T, T>> =
        this.flatMapIndexed { index, a -> this.drop(index + 1).map { b -> a to b } }

    suspend fun createSummary(roomId: RoomId, newTransactionId: EventId, content: ZeroInterestTransactionEvent) {
        val heads = database.getHeads(roomId)
        if (heads.isEmpty()) {
            log.info { "No heads found for room $roomId, creating initial summary" }

            val balances = mutableMapOf<UserId, Long>()
            content.apply(balances)

            log.info { "Creating summary for first transaction $newTransactionId in room $roomId" }
            val response = client.api.room.sendStateEvent(roomId, ZeroInterestSummaryEvent(
                balances = balances,
                parents = emptyMap()
            ), ZeroInterestSummaryEvent.TYPE).getOrThrow()
            database.markTrusted(roomId, response)
            database.setHeads(roomId, setOf(response))
        } else {
            log.info { "Merging ${heads.size} heads for new transaction $newTransactionId in room $roomId" }
            // Merge heads
            // 1. Fetch all heads
            val headEvents = heads.associateWith {
                val first = client.room.getTimelineEvent(roomId, it).filterNotNull().first()
                log.info { "Fetched head $first" }
                first.content?.getOrNull() as? ZeroInterestSummaryEvent
            }.filterValues { it != null }.mapValues { it.value!! }

            if (headEvents.isEmpty()) {
                log.warn { "Head set is empty!" }
                return // Should not happen if heads is not empty
            }

            // 2. Calculate the union of history for all heads
            val allVisitedSummaries = mutableSetOf<EventId>()
            val summaryQueue = ArrayDeque<EventId>()
            summaryQueue.addAll(heads)

            val summaryGraph = mutableMapOf<EventId, ZeroInterestSummaryEvent>()

            // Load graph
            log.info { "Building summary graph for room $roomId" }
            while (summaryQueue.isNotEmpty()) {
                val currentId = summaryQueue.removeFirst()
                if (currentId in allVisitedSummaries) continue
                allVisitedSummaries.add(currentId)

                val event = if (currentId in heads) headEvents[currentId]!! else {
                    client.room.getTimelineEvent(roomId, currentId).filterNotNull().first().content?.getOrNull() as? ZeroInterestSummaryEvent
                } ?: continue

                summaryGraph[currentId] = event
                summaryQueue.addAll(event.parents.keys)
            }

            // Now we have a subgraph in summaryGraph.
            // We need to compute the set of transactions for each head.
            // We can do this by propagating sets of transactions up from the leaves (or common ancestors).

            // Topological sort or just recursive memoized calculation.
            val headTransactions = mutableMapOf<EventId, Set<EventId>>()

            // Helper to get transactions for a summary node (relative to the bottom of our graph)
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
            for (headId in heads) {
                val h = getHistory(headId)
                headTransactions[headId] = h
                allTransactions.addAll(h)
            }

            // Now compute the new parents map
            val newParents = mutableMapOf<EventId, Set<EventId>>()
            for (headId in heads) {
                val missing = allTransactions - headTransactions[headId]!!
                newParents[headId] = missing + newTransactionId
            }

            // Compute new balances
            // Take the first head, apply its missing transactions + new transaction
            val baseHeadId = heads.first()
            val baseHeadEvent = headEvents[baseHeadId]!!
            val baseBalances = baseHeadEvent.balances.toMutableMap()

            val toApply = newParents[baseHeadId]!!

            // We need to fetch the transaction contents to apply them
            log.info { "Applying ${toApply.size} transactions to compute new balances for room $roomId" }
            for (txId in toApply) {
                val tx = client.room.getTimelineEvent(roomId, txId).filterNotNull().first().content?.getOrNull() as? ZeroInterestTransactionEvent
                tx?.apply(baseBalances)
            }

            log.info { "Creating merged summary for new transaction $newTransactionId in room $roomId" }
            val response = client.api.room.sendStateEvent(roomId, ZeroInterestSummaryEvent(
                balances = baseBalances,
                parents = newParents
            ), ZeroInterestSummaryEvent.TYPE).getOrThrow()
            database.markTrusted(roomId, response)
            database.setHeads(roomId, setOf(response))
        }
        log.info { "Summary creation for new transaction $newTransactionId in room $roomId completed" }
    }
}

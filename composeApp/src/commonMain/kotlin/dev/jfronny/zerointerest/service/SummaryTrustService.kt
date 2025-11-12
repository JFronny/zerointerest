package dev.jfronny.zerointerest.service

import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import kotlinx.coroutines.flow.any
import kotlinx.coroutines.flow.last
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.getTimelineEventReactionAggregation
import net.folivo.trixnity.client.room.message.react
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.originTimestamp
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

private data class Timed<T>(val ts: Long, val value: T)

private const val rejectionKey = "\uD83D\uDC4E"
class SummaryTrustService(
    private val client: MatrixClientService,
    private val database: SummaryTrustDatabase
) {
    suspend fun checkTrusted(roomId: RoomId, messageId: EventId, timestamp: Long, content: ZeroInterestSummaryEvent): SummaryTrustDatabase.TrustState {
        val dbState = database.checkTrust(roomId, messageId)
        if (dbState != SummaryTrustDatabase.TrustState.UNTRUSTED) return dbState
        // 1. If a rejection for the summary exists, the summary is always rejected.
        val reactions = client.get().room.getTimelineEventReactionAggregation(roomId, messageId).last()
        if (reactions.reactions[rejectionKey]?.isNotEmpty() ?: false) return reject(roomId, messageId, send = false)
        // 2. Otherwise, if the summary event is the first summary event ever encountered in a room, it is trusted.
        if (content.parents.isEmpty()) {
            val hasPrevious = client.get().room.getTimelineEvents(roomId, messageId, direction = GetEvents.Direction.BACKWARDS) {
                maxSize = 1024 // trixnity doesn't easily allow us to filter just for ZeroInterestSummaryEvents, and this should be fine
            }.last().any {
                it.eventId != messageId && it.content?.getOrNull() is ZeroInterestSummaryEvent
            }
            if (!hasPrevious) return accept(roomId, messageId)
        }
        // Prefetch events as the next steps need them
        val summaries = mutableMapOf<EventId, Timed<ZeroInterestSummaryEvent>>()
        for (eventId in content.parents.keys) {
            val event = client.get().room.getTimelineEvent(roomId, eventId).last()
            val content = event?.content?.getOrNull() as? ZeroInterestSummaryEvent ?: continue
            summaries[eventId] = Timed(event.originTimestamp, content)
        }
        val transactions = mutableMapOf<EventId, Timed<ZeroInterestTransactionEvent>>()
        for (eventId in content.parents.values.flatten()) {
            val event = client.get().room.getTimelineEvent(roomId, eventId).last()
            val content = event?.content?.getOrNull() as? ZeroInterestTransactionEvent ?: continue
            transactions[eventId] = Timed(event.originTimestamp, content)
        }
        // 3. Otherwise, if the summary event has no trusted parents, it is rejected.
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
        // 4. Otherwise, if the sum of transactions from a parent does not result in the balances, the event is rejected.
        for ((summaryId, transactionIds) in content.parents) {
            val balances = summaries[summaryId]?.value?.balances ?: continue
            val transactions = transactionIds.mapNotNull { transactions[it]?.value }
            if (transactions.size < transactionIds.size) return reject(roomId, messageId)
            val computedBalances = mutableMapOf<UserId, Long>()
            for (event in transactions) {
                computedBalances[event.sender] = (computedBalances[event.sender] ?: 0L) - event.total
                for ((receiver, delta) in event.receivers) {
                    computedBalances[receiver] = (computedBalances[receiver] ?: 0L) + delta
                }
            }
            if (computedBalances != balances) return reject(roomId, messageId)
        }
        // 5. Otherwise, if a common ancestor exists between two parents, and the following transactions differ between them, the event is rejected.
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
        // 6. Otherwise, if transactions that are not between the summaries temporally are referenced, the event is rejected.
        for ((summaryId, transactionIds) in content.parents) {
            val summary = summaries[summaryId] ?: continue
            val transactions = transactionIds.mapNotNull { transactions[it] }
            for (transaction in transactions) {
                if (transaction.ts < summary.ts) return reject(roomId, messageId)
                if (transaction.ts > timestamp) return reject(roomId, messageId)
            }
        }
        // 7. Otherwise, the summary event is trusted.
        return accept(roomId, messageId)
    }

    private suspend fun reject(roomId: RoomId, messageId: EventId, send: Boolean = true): SummaryTrustDatabase.TrustState {
        database.markRejected(roomId, messageId)
        if (send) {
            client.get().room.sendMessage(roomId) {
                react(messageId, rejectionKey)
            }
        }
        return SummaryTrustDatabase.TrustState.REJECTED
    }

    private suspend fun accept(roomId: RoomId, messageId: EventId): SummaryTrustDatabase.TrustState {
        database.markTrusted(roomId, messageId)
        return SummaryTrustDatabase.TrustState.TRUSTED
    }

    private fun <T> List<T>.combinations(): List<Pair<T, T>> =
        this.flatMapIndexed { index, a -> this.drop(index + 1).map { b -> a to b } }
}
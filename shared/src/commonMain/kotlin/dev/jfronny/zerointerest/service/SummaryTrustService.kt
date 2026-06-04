package dev.jfronny.zerointerest.service

import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import dev.jfronny.zerointerest.data.TrustState
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.db.ZeroInterestDatabase
import dev.jfronny.zerointerest.service.client.ZiClientProvider
import dev.jfronny.zerointerest.service.client.computeMergedSummary
import dev.jfronny.zerointerest.util.Timed
import dev.jfronny.zerointerest.util.fold
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class SummaryTrustService(
    private val clientProvider: ZiClientProvider,
    private val database: ZeroInterestDatabase
) {
    companion object {
        const val rejectionKey = "\uD83D\uDC4E"
        private val log = KotlinLogging.logger {}
    }

    private val client get() = clientProvider.get()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getSummary(roomId: RoomId): Flow<Summary?> {
        return client.getSummaryStateFlow(roomId)
            .flatMapLatest {
                flow {
                    if (it == null) {
                        log.info { "No summary found for room $roomId" }
                        emit(Summary.Empty)
                        return@flow
                    }
                    emit(null)
                    log.info { "Received summary event: $it" }
                    val trust = withTimeoutOrNull(20.seconds) {
                        checkTrusted(roomId, it.id, it.originTimestamp, it.content)
                    }
                    log.info { "Checked trust: $trust" }
                    if (trust == TrustState.TRUSTED) {
                        log.info { "Trusted summary found for room $roomId. Checking heads." }
                        database.getHeadsFlow(roomId).collect { headIds ->
                            if (headIds.isEmpty()) {
                                emit(Summary.Empty)
                            } else {
                                val heads = headIds.associateWith { headId ->
                                    client.getSummaryEventWithTimeout(roomId, headId)?.getOrNull()?.value
                                }.filterValues { head -> head != null }.mapValues { head -> head.value!! }

                                if (heads.size == 1) {
                                    emit(Summary.Trusted(heads.values.first(), isMerge = false))
                                } else {
                                    emit(Summary.Trusted(client.computeMergedSummary(roomId, heads), isMerge = true))
                                }
                            }
                        }
                    } else {
                        log.warn { "Latest summary found for room $roomId is not trusted" }
                        emit(Summary.Untrusted(roomId, it.id, it.content))
                    }
                }
            }
    }

    sealed interface Summary {
        data class Untrusted(val roomId: RoomId, val messageId: EventId, val content: ZeroInterestSummaryEvent) : Summary
        object Empty : Summary
        data class Trusted(val event: ZeroInterestSummaryEvent, val isMerge: Boolean) : Summary
    }

    suspend fun checkTrusted(roomId: RoomId, messageId: EventId, timestamp: Long, content: ZeroInterestSummaryEvent): TrustState {
        val dbState = database.checkTrust(roomId, messageId)
        if (dbState != TrustState.UNTRUSTED) return dbState

        log.info { "Checking trust for summary event $messageId in room $roomId" }

        log.info { "1. If a rejection for the summary exists, the summary is always rejected." }
        val reactions = client.getTimelineEventReactionAggregation(roomId, messageId).first()[rejectionKey]
        if (reactions?.isNotEmpty() ?: false) return reject(roomId, messageId, "rejection in timeline: ${reactions.first().eventId} by ${reactions.first().sender}", send = false)

        log.info { "2. Otherwise, if the summary event is the first summary event ever encountered in a room, it is trusted." }
        if (content.parents.isEmpty()) {
            log.info { "Get events before $messageId" }
            val hasPrevious = client.hasPreviousSummary(roomId, messageId)
            log.info { "Got events before $messageId" }
            if (!hasPrevious) return accept(roomId, messageId, content)
        }

        log.info { "Prefetch events as the next steps need them" }
        val summaries = mutableMapOf<EventId, Timed<ZeroInterestSummaryEvent>>()
        loop@ for (eventId in content.parents.keys) {
            if (eventId == messageId) throw IllegalStateException("Summary event cannot reference itself as a parent. You have passed the wrong arguments to this function!")
            val event = client.getSummaryEventWithTimeout(roomId, eventId).fold(
                onSuccess = { Timed(it.ts, it.value) },
                onFailure = { e ->
                    log.error(e) { "Could not fetch event $eventId in room $roomId. Continuing with other parents." }
                    continue@loop
                },
                onNull = {
                    log.error { "Could not fetch event $eventId in room $roomId (in a timely manner). Continuing with other parents." }
                    continue@loop
                }
            )
            summaries[eventId] = event
        }
        val transactions = mutableMapOf<EventId, Timed<ZeroInterestTransactionEvent>>()
        for (eventId in content.parents.values.flatten()) {
            val event = client.getTransactionEventWithTimeout(roomId, eventId).fold(
                onSuccess = { it },
                onFailure = { e ->
                    log.error(e) { "Could not fetch event $eventId in room $roomId. Aborting trust check" }
                    return@checkTrusted TrustState.UNTRUSTED
                },
                onNull = {
                    log.error { "Could not fetch event $eventId in room $roomId (in a timely manner). Aborting trust check" }
                    return@checkTrusted TrustState.UNTRUSTED
                }
            )
            transactions[eventId] = event
        }

        log.info { "3. Otherwise, if the summary event has no trusted parents, it is rejected." }
        val trustedParents = mutableMapOf<EventId, Set<EventId>>()
        for ((summaryId, transactionIds) in content.parents) {
            val summary = summaries[summaryId] ?: continue
            val trust = checkTrusted(roomId, summaryId, summary.ts, summary.value)
            if (trust == TrustState.TRUSTED) {
                trustedParents[summaryId] = transactionIds
            }
        }
        if (trustedParents.isEmpty()) {
            if (content.parents.isEmpty()) return reject(roomId, messageId, "no parents but not first summary")
            return reject(roomId, messageId, "untrusted parents: ${content.parents.keys}")
        }

        log.info { "4. Otherwise, if the sum of transactions from a parent does not result in the balances, the event is rejected." }
        for ((parentId, transactionIds) in content.parents) {
            val balances = summaries[parentId]?.value?.balances ?: continue
            val computedBalances = balances.toMutableMap()
            for (transactionId in transactionIds) {
                val event = transactions[transactionId]?.value ?: throw IllegalStateException("Event $transactionId should have gotten resolved in prefetch but somehow didn't")
                event.apply(computedBalances)
            }
            if (computedBalances != content.balances) return reject(roomId, messageId, "balances do not match after summary $parentId: ${computedBalances.toList()} != ${balances.toList()}")
        }

        log.info { "5. Otherwise, if a common ancestor exists between two parents, and the following transactions differ between them, the event is rejected." }
        for ((summaryId1, summaryId2) in content.parents.keys.toList().combinations()) {
            val summary1 = summaries[summaryId1]?.value ?: continue
            val summary2 = summaries[summaryId2]?.value ?: continue
            // Only checks for direct ancestors. Not technically correct, but good enough for now.
            for (ancestorId in summary1.parents.keys.intersect(summary2.parents.keys)) {
                val transactions1 = summary1.parents[ancestorId]!! + content.parents[summaryId1]!!
                val transactions2 = summary2.parents[ancestorId]!! + content.parents[summaryId2]!!
                if (transactions1 != transactions2) return reject(roomId, messageId, "transactions do not match between summaries $summaryId1 and $summaryId2")
            }
        }

        log.info { "6. Otherwise, if transactions that are not between the summaries temporally are referenced, the event is rejected." }
        for ((summaryId, transactionIds) in content.parents) {
            val summary = summaries[summaryId] ?: continue
            for (transactionId in transactionIds) {
                val transaction = transactions[transactionId] ?: continue
                if (transaction.ts < summary.ts) return reject(roomId, messageId, "transaction $transactionId is before summary $summaryId")
                if (transaction.ts > timestamp) return reject(roomId, messageId, "transaction $transactionId is after new summary")
            }
        }

        log.info { "7. Otherwise, the summary event is trusted." }
        return accept(roomId, messageId, content)
    }

    private suspend fun reject(roomId: RoomId, messageId: EventId, reason: String, send: Boolean = true): TrustState = withContext(NonCancellable) {
        log.info { "Rejecting $messageId. Reason: $reason" }
        database.markRejected(roomId, messageId)
        if (send) {
            client.reactToEvent(roomId, messageId, rejectionKey)
        }
        return@withContext TrustState.REJECTED
    }

    suspend fun forceAccept(summary: Summary.Untrusted) {
        accept(summary.roomId, summary.messageId, summary.content, override = true)
    }

    private suspend fun accept(
        roomId: RoomId,
        messageId: EventId,
        content: ZeroInterestSummaryEvent,
        override: Boolean = false,
        isPropagation: Boolean = false,  // if we're overriding, we can assume all previous heads are invalid
    ): TrustState = withContext(NonCancellable) {
        if (override && database.checkTrust(roomId, messageId) == TrustState.TRUSTED) return@withContext TrustState.TRUSTED
        log.info { "Accepting $messageId" }
        database.addTrustedSummary(
            roomId,
            messageId,
            content,
            isRoot = override,
            updateHeads = !isPropagation,
        )
        if (override || isPropagation) {
            log.info { "Propagating trust of $messageId" }
            val reactions = client.getTimelineEventReactionAggregation(roomId, messageId).first()[rejectionKey]
            if (reactions != null) {
                for (event in reactions) {
                    if (event.sender == client.userId) {
                        client.redactEvent(roomId, event.eventId, "Event became trusted")
                    }
                }
            }
            for (id in content.parents.keys) {
                val event = client.getSummaryEventWithTimeout(roomId, id)?.getOrNull() ?: continue
                // do not clear heads when accepting parents
                accept(roomId, id, event.value, override = false, isPropagation = true)
            }
        }
        return@withContext TrustState.TRUSTED
    }

    suspend fun getSummariesReferencingTransactions(roomId: RoomId, transactions: Set<EventId>): Map<EventId, Set<EventId>> {
        return database.getSummariesReferencingTransactions(roomId, transactions)
    }

    private fun <T> List<T>.combinations(): List<Pair<T, T>> =
        this.flatMapIndexed { index, a -> this.drop(index + 1).map { b -> a to b } }
}

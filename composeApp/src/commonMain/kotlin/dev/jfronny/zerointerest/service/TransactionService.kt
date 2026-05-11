package dev.jfronny.zerointerest.service

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.db.ZeroInterestDatabase
import dev.jfronny.zerointerest.service.client.ZiClientProvider
import dev.jfronny.zerointerest.service.client.computeMergedSummary
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

private val log = KotlinLogging.logger {}

class TransactionService(
    private val clientProvider: ZiClientProvider,
    private val database: ZeroInterestDatabase,
) {
    private val client get() = clientProvider.get()

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
            val initialResponse = client.sendStateEvent(roomId, initialEvent, ZeroInterestSummaryEvent.TYPE).getOrThrow()
            database.addTrustedSummary(roomId, initialResponse, initialEvent, isRoot = true)

            log.info { "Creating summary for first transactions $newTransactionIds in room $roomId" }
            val balances = mutableMapOf<UserId, Long>()
            contents.forEach { it.apply(balances) }
            val event = ZeroInterestSummaryEvent(
                balances = balances,
                parents = mapOf(initialResponse to newTransactionIds.toSet())
            )
            val response = client.sendStateEvent(roomId, event, ZeroInterestSummaryEvent.TYPE).getOrThrow()
            database.addTrustedSummary(roomId, response, event)
        } else {
            log.info { "Merging ${heads.size} heads for new transactions $newTransactionIds in room $roomId" }
            
            val event = client.computeMergedSummary(roomId, heads, newTransactionIds, contents)
            
            log.info { "Creating merged summary for new transactions $newTransactionIds in room $roomId" }
            val response = client.sendStateEvent(roomId, event, ZeroInterestSummaryEvent.TYPE).getOrThrow()
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
                client.scheduleMessageEvent(roomId, content).getOrThrow()
            } catch (e: Exception) {
                throw FailedSendMessageException(cause = e)
            }
        }.map { txId -> async {
            client.awaitScheduledMessageEvent(roomId, txId).getOrThrow()
        } }.awaitAll()

        createSummary(preparedSummary, txIds)
    }

    class FailedPrepareSummaryException(cause: Throwable) : Exception("Failed to prepare summary", cause)
    class FailedSendMessageException(message: String = "Failed to send message", cause: Throwable? = null) : Exception(message, cause)

    suspend fun sendTransaction(roomId: RoomId, content: ZeroInterestTransactionEvent) {
        sendTransactions(roomId, listOf(content))
    }
}

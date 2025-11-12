package dev.jfronny.zerointerest.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.StateEventContent

@Serializable
data class ZeroInterestSummaryEvent(
    val balances: Map<UserId, Long>,
    val parents: Map<EventId, Set<EventId>>, // previous summary -> transactions between them
) : StateEventContent {
    @SerialName("external_url") override val externalUrl: String? = null

    companion object {
        const val TYPE = "dev.jfronny.zerointerest.summary"
    }
}

package dev.jfronny.zerointerest.data

import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo

@Serializable
data class ZeroInterestTransactionEvent(
    val description: String,
    val sender: UserId,
    val receivers: Map<UserId, Long>,
    @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
) : MessageEventContent {
    val total by lazy { receivers.values.sum() }

    @SerialName("m.mentions") override val mentions: Mentions = Mentions(users = setOf(sender) + receivers.keys)
    @SerialName("external_url") override val externalUrl: String? = null

    override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo)

    @SerialName("format") val format: String = "org.matrix.custom.html"
    @SerialName("body") val body: String = "${sender.localpart} sent $total to ${receivers.keys.formatList { it.localpart }}"
    @SerialName("formatted_body") val formattedBody: String = "${sender.mention()} sent $total to ${receivers.keys.formatList { it.mention() }}"

    private fun <K> Iterable<K>.formatList(print: (K) -> String) = buildString {
        val iterator = this@formatList.iterator()
        while (iterator.hasNext()) {
            val next = print(iterator.next())
            if (iterator.hasNext()) append(", ")
            else append(" and ")
            append(next)
        }
    }

    private fun UserId.mention() = """<a href="${URLBuilder("https://matrix.to/#").appendPathSegments(full, encodeSlash = true).build()}">${localpart}</a>"""

    companion object {
        const val TYPE = "dev.jfronny.zerointerest.transaction"
        const val PAYMENT_DESCRIPTION = "Payment"
    }
}

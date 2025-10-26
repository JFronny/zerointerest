package dev.jfronny.zerointerest.data

import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

@Serializable
data class ZeroInterestEventContent(
    @SerialName("$TYPE.balances") val balances: Map<UserId, Long>,
    @SerialName("$TYPE.transaction") val transaction: Transaction?,
    @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null
) : MessageEventContent {
    @SerialName("format") val format: String = "org.matrix.custom.html"
    @SerialName("m.mentions") override val mentions: Mentions = Mentions(users = transaction?.let { setOf(it.sender) + it.receivers.keys })
    @SerialName("external_url") override val externalUrl: String? = null
    @SerialName("body") val body: String = if (transaction == null) "(balance update)"
        else "${transaction.sender.localpart} sent ${transaction.total} to ${transaction.receivers.keys.formatList { it.localpart }}"
    @SerialName("formatted_body") val formattedBody: String = if (transaction == null) " (balance update)"
        else "${transaction.sender.mention()} sent ${transaction.total} to ${transaction.receivers.keys.formatList { it.mention() }}"

    override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo)

    companion object {
        const val TYPE = "dev.jfronny.zerointerest"

        fun from(msg: RoomMessageEventContent.Unknown): ZeroInterestEventContent {
            if (msg.type != TYPE) throw IllegalArgumentException("Message type should be $TYPE but was ${msg.type}")
            return Json.decodeFromJsonElement(serializer(), msg.raw)
        }
    }

    @Serializable data class Transaction(
        val sender: UserId,
        val receivers: Map<UserId, Long>,
    ) {
        val total by lazy { receivers.values.sum() }
    }

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
}

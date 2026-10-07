package dev.jfronny.zerointerest.data

import androidx.compose.runtime.Immutable
import de.connect2x.trixnity.core.model.events.StateEventContent
import dev.jfronny.zerointerest.data.money.MonetaryUnit
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer

/**
 * Room state event carrying the zerointerest protocol configuration of a room.
 *
 * The [version] is the zerointerest protocol version of the room. Clients must not write to rooms
 * with a version newer than [CURRENT_PROTOCOL_VERSION] and must not send this event without
 * explicit user confirmation.
 *
 * Instances are one of the version-specific subtypes, dispatched by [ZiConfigStateEvent.Serializer],
 * because the protocol payload is effectively polymorphic over versions (kotlinx.serialization's
 * automatic polymorphic serialization is not usable here, as the protocol has no string
 * discriminator field, only the numeric [version]).
 */
@Serializable(with = ZiConfigStateEvent.Serializer::class)
@Immutable
sealed interface ZiConfigStateEvent : StateEventContent {
    /** The zerointerest protocol version of the room. Null when it could not be determined. */
    val version: Int?

    override val externalUrl: String?
        get() = null

    companion object {
        const val TYPE = "dev.jfronny.zerointerest.config"

        /** The zerointerest protocol version supported by this client. */
        const val CURRENT_PROTOCOL_VERSION = 1
    }

    object Serializer : JsonContentPolymorphicSerializer<ZiConfigStateEvent>(ZiConfigStateEvent::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ZiConfigStateEvent> {
            val version = (element.jsonObject["version"] as? JsonPrimitive)?.intOrNull
                ?: throw SerializationException("Could not read a numeric 'version' from ZiConfigStateEvent content: $element")
            require(CURRENT_PROTOCOL_VERSION == 1)
            return when (version) {
                0 -> serializer<V0>()
                1 -> serializer<V1>()
                else -> serializer<Newer>()
            }
        }
    }

    sealed interface Acceptable : ZiConfigStateEvent {
        /**
         * The currency to display amounts of this room with.
         * Null when the room uses a protocol version newer than this client supports.
         */
        val currency: MonetaryUnit get() = MonetaryUnit.default
    }

    /**
     * A config with a version older than [CURRENT_PROTOCOL_VERSION].
     * Also used as a stand-in when a room has no config event at all.
     */
    @Serializable
    data class V0(override val version: Int = 0) : Acceptable

    /** A config using the current protocol version. */
    @Serializable
    data class V1(
        override val version: Int = 1,
        /** The room specific currency (e.g. "EUR" or "€"), as declared in the event. */
        @SerialName("currency")
        val rawCurrency: String? = null,
    ) : Acceptable {
        init {
            require(version == 1)
        }

        override val currency: MonetaryUnit get() = rawCurrency?.let { runCatching { MonetaryUnit(it) }.getOrNull() } ?: MonetaryUnit.default
    }

    object Unknown : ZiConfigStateEvent {
        override val version: Int? get() = null
    }

    /**
     * A config using a protocol version newer than this client supports.
     * Unknown fields of the event are ignored.
     */
    @Serializable
    data class Newer(override val version: Int) : ZiConfigStateEvent {
        init {
            require(version > 1 || version < 0)
        }
    }
}

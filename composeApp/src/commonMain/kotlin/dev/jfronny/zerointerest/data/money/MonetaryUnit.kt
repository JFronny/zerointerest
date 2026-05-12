package dev.jfronny.zerointerest.data.money

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline

@JvmInline
@Serializable(with = MonetaryUnit.Serializer::class)
value class MonetaryUnit(val code: String) {
    init {
        require(code.trim() == code)
        require(code.isNotBlank())
        require(code.none { it.isDigit() || it == '.' || it == ',' || it in MoneyParser.symbols })
    }

    override fun toString(): String = code

    companion object {
        val default = MonetaryUnit("€")
    }

    object Serializer : KSerializer<MonetaryUnit> {
        override val descriptor = String.serializer().descriptor
        override fun deserialize(decoder: Decoder) = MonetaryUnit(decoder.decodeString())
        override fun serialize(encoder: Encoder, value: MonetaryUnit) = encoder.encodeString(value.code)
    }
}

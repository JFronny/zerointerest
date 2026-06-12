package dev.jfronny.zerointerest.data.money

import kotlin.jvm.JvmInline
import kotlin.math.abs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline
@Serializable(with = Money.Serializer::class)
value class Money(val amount: Long) : Comparable<Money> {
    override fun toString(): String {
        // 0 -> 0.00 €
        // 1234 -> 12.34 €
        // -1024 -> -10.24 €
        val euros = amount / 100
        val cents = abs(amount % 100)
        return "$euros.${cents.toString().padStart(2, '0')}"
    }

    fun format(unit: MonetaryUnit): String = "$this $unit"

    operator fun plus(other: Money) = Money(amount + other.amount)
    operator fun minus(other: Money) = Money(amount - other.amount)
    operator fun unaryMinus() = Money(-amount)
    override fun compareTo(other: Money): Int = amount.compareTo(other.amount)

    object Serializer : KSerializer<Money> {
        override val descriptor = Long.serializer().descriptor
        override fun deserialize(decoder: Decoder) = Money(decoder.decodeLong())
        override fun serialize(encoder: Encoder, value: Money) = encoder.encodeLong(value.amount)
    }

    companion object {
        val zero = Money(0)

        fun parse(input: String, unit: MonetaryUnit) = MoneyParser.parse(input, unit).toMoney()
        fun parse(input: String, targetUnit: MonetaryUnit, exchangeRates: Map<MonetaryUnit, Double>) = MoneyParser.parse(input, targetUnit, exchangeRates).toMoney()
    }
}

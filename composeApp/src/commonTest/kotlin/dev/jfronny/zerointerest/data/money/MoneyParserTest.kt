package dev.jfronny.zerointerest.data.money

import dev.jfronny.zerointerest.data.money.MoneyParser.ParseException
import dev.jfronny.zerointerest.util.multiplyExact
import dev.jfronny.zerointerest.util.multiplyExactNaive
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll

class MoneyParserTest : FunSpec() {
    init {
        val eur = MonetaryUnit("EUR")
        val usd = MonetaryUnit("USD")
        test("parses simple money") {
            Money.parse("100 EUR", eur) shouldBe Money(10000)
            Money.parse("100.00 EUR", eur) shouldBe Money(10000)
            Money.parse("100,00 EUR", eur) shouldBe Money(10000)
            Money.parse("-100,00 EUR", eur) shouldBe Money(-10000)
            Money.parse("100,50$", MonetaryUnit("$")) shouldBe Money(10050)

            Money.parse("12", eur) shouldBe Money(1200)
            Money.parse("-12", eur) shouldBe Money(-1200)
            Money.parse("12.00", eur) shouldBe Money(1200)
            Money.parse("12,00", eur) shouldBe Money(1200)
            Money.parse("-12,00", eur) shouldBe Money(-1200)
        }
        test("throws on invalid input") {
            shouldThrow<ParseException> { Money.parse("100 EUR", MonetaryUnit("USD")) }
            shouldThrow<ParseException> { Money.parse("100.5", eur) }
            shouldThrow<ParseException> { Money.parse("100.500", eur) }
        }
        test("handles whitespace") {
            Money.parse("100 EUR ", eur) shouldBe Money(10000)
            Money.parse("   10    EUR ", eur) shouldBe Money(1000)
        }
        test("handles empty input") {
            Money.parse("", eur) shouldBe Money(0)
        }
        test("handles simple math") {
            Money.parse("100 EUR + 100 EUR", eur) shouldBe Money(20000)
            Money.parse("100 EUR - 100 EUR", eur) shouldBe Money(0)
            Money.parse("100 EUR + 100,50 EUR", eur) shouldBe Money(20050)
            Money.parse("100 EUR - 100,50 EUR", eur) shouldBe Money(-50)
            Money.parse("100 EUR + 100 EUR - 100 EUR", eur) shouldBe Money(10000)
            Money.parse("12 * 100 EUR", eur) shouldBe Money(120000)
            Money.parse("12 / 10 EUR", eur) shouldBe Money(120)
            shouldThrow<ParseException> { Money.parse("12 / (10 EUR)", eur) }
            Money.parse("12 / (10 - 5) EUR", eur) shouldBe Money(240)
            shouldThrow<ParseException> { Money.parse("12 / (10 - 10) EUR", eur) }
        }
        test("multiplyExact is used") {
            Money.parse("12 * 100 EUR", eur) shouldBe Money(120000)
            shouldThrow<ParseException> { Money.parse("12 EUR / 10 EUR", eur) }
            shouldThrow<ParseException> { Money.parse("12 EUR * 10 EUR", eur) }
            Money.parse("${Long.MAX_VALUE / 500} EUR", eur) shouldBe Money(Long.MAX_VALUE / 500 * 100)
            shouldThrow<ParseException> { Money.parse("${Long.MAX_VALUE / 500} * 6 EUR", eur) }
        }
        test("converts units") {
            val exchangeRates = mapOf(
                MonetaryUnit("EUR") to 1.0,
                MonetaryUnit("USD") to 1.25,
            )
            Money.parse("100 EUR", eur, exchangeRates) shouldBe Money(10000)
            Money.parse("100 USD", eur, exchangeRates) shouldBe Money(12500)
            Money.parse("10 * 10 EUR + 100 USD", eur, exchangeRates) shouldBe Money(22500)
        }
        test("handles partial") {
            Money.parse("100 EUR * 12", eur) shouldBe Money(120000)
            shouldThrow<ParseException> { Money.parse("100 EU", eur) }
            shouldThrow<ParseException> { Money.parse("100 *", eur) }
            shouldThrow<ParseException> { Money.parse("100 +", eur) }
        }
    }
}

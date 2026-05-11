package dev.jfronny.zerointerest.util

import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.service.calculateSettlementTransactions
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DebtSettlementTest : FunSpec({
    test("calculates simple exact match settlement") {
        val balances = mapOf(
            UserId("@alice:example.com") to 100L,
            UserId("@bob:example.com") to -100L
        )

        val transactions = calculateSettlementTransactions(balances)

        transactions.size shouldBe 1
        transactions[0].sender shouldBe UserId("@alice:example.com")
        transactions[0].receivers shouldBe mapOf(UserId("@bob:example.com") to 100L)
        transactions[0].description shouldBe ZeroInterestTransactionEvent.PAYMENT_DESCRIPTION
    }

    test("calculates multiple debtors to one creditor") {
        val balances = mapOf(
            UserId("@alice:example.com") to 50L,
            UserId("@charlie:example.com") to 50L,
            UserId("@bob:example.com") to -100L
        )

        val transactions = calculateSettlementTransactions(balances)

        transactions.size shouldBe 2
        transactions.map { it.sender }.toSet() shouldBe setOf(UserId("@alice:example.com"), UserId("@charlie:example.com"))
        transactions[0].receivers shouldBe mapOf(UserId("@bob:example.com") to 50L)
        transactions[1].receivers shouldBe mapOf(UserId("@bob:example.com") to 50L)
    }

    test("calculates complex multi-way settlement") {
        val balances = mapOf(
            UserId("@a:example.com") to 150L,
            UserId("@b:example.com") to 50L,
            UserId("@c:example.com") to -100L,
            UserId("@d:example.com") to -100L
        )

        val transactions = calculateSettlementTransactions(balances)

        // @a pays 100 to @c and 50 to @d
        // @b pays 50 to @d
        transactions.size shouldBe 3
        val sumResolved = transactions.sumOf { it.receivers.values.sum() }
        sumResolved shouldBe 200L
    }
})
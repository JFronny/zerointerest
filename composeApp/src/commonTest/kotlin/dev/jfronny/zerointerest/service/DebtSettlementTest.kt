package dev.jfronny.zerointerest.service

import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.data.money.sum
import dev.jfronny.zerointerest.data.money.sumOfM
import dev.jfronny.zerointerest.data.money.toMoney
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DebtSettlementTest : FunSpec({
    test("calculates simple exact match settlement") {
        val balances = mapOf(
            UserId("@alice:example.com") to 100L.toMoney(),
            UserId("@bob:example.com") to (-100L).toMoney()
        )

        val transactions = calculateSettlementTransactions(balances)

        transactions.size shouldBe 1
        transactions[0].sender shouldBe UserId("@alice:example.com")
        transactions[0].receivers shouldBe mapOf(UserId("@bob:example.com") to 100L.toMoney())
        transactions[0].description shouldBe ZeroInterestTransactionEvent.PAYMENT_DESCRIPTION
    }

    test("calculates multiple debtors to one creditor") {
        val balances = mapOf(
            UserId("@alice:example.com") to 50L.toMoney(),
            UserId("@charlie:example.com") to 50L.toMoney(),
            UserId("@bob:example.com") to (-100L).toMoney()
        )

        val transactions = calculateSettlementTransactions(balances)

        transactions.size shouldBe 2
        transactions.map { it.sender }.toSet() shouldBe setOf(UserId("@alice:example.com"), UserId("@charlie:example.com"))
        transactions[0].receivers shouldBe mapOf(UserId("@bob:example.com") to 50L.toMoney())
        transactions[1].receivers shouldBe mapOf(UserId("@bob:example.com") to 50L.toMoney())
    }

    test("calculates complex multi-way settlement") {
        val balances = mapOf(
            UserId("@a:example.com") to 150L.toMoney(),
            UserId("@b:example.com") to 50L.toMoney(),
            UserId("@c:example.com") to (-100L).toMoney(),
            UserId("@d:example.com") to (-100L).toMoney()
        )

        val transactions = calculateSettlementTransactions(balances)

        // @a pays 100 to @c and 50 to @d
        // @b pays 50 to @d
        transactions.size shouldBe 3
        val sumResolved = transactions.sumOfM { it.receivers.values.sum() }
        sumResolved shouldBe 200L.toMoney()
    }
})
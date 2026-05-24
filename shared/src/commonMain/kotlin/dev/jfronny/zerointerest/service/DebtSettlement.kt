package dev.jfronny.zerointerest.service

import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent
import dev.jfronny.zerointerest.data.money.Money

fun calculateSettlementTransactions(balances: Map<UserId, Money>): List<ZeroInterestTransactionEvent> {
    val debtors = balances.filterValues { it.amount > 0 }.toList().sortedByDescending { it.second }.toMutableList()
    val creditors = balances.filterValues { it.amount < 0 }.toList().sortedBy { it.second }.toMutableList()

    val transactions = mutableListOf<ZeroInterestTransactionEvent>()

    var i = 0
    var j = 0

    while (i < debtors.size && j < creditors.size) {
        val debtor = debtors[i]
        val creditor = creditors[j]

        val amount = minOf(debtor.second, -creditor.second)

        transactions.add(
            ZeroInterestTransactionEvent(
                description = ZeroInterestTransactionEvent.PAYMENT_DESCRIPTION,
                sender = debtor.first,
                receivers = mapOf(creditor.first to amount)
            )
        )

        debtors[i] = debtor.copy(second = debtor.second - amount)
        creditors[j] = creditor.copy(second = creditor.second + amount)

        if (debtors[i].second == Money.zero) {
            i++
        }
        if (creditors[j].second == Money.zero) {
            j++
        }
    }

    return transactions
}

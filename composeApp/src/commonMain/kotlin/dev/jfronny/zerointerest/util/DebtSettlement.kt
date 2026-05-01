package dev.jfronny.zerointerest.util

import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.data.ZeroInterestTransactionEvent

fun calculateSettlementTransactions(balances: Map<UserId, Long>): List<ZeroInterestTransactionEvent> {
    val debtors = balances.filterValues { it > 0 }.toList().sortedByDescending { it.second }.toMutableList()
    val creditors = balances.filterValues { it < 0 }.toList().sortedBy { it.second }.toMutableList()

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

        if (debtors[i].second == 0L) {
            i++
        }
        if (creditors[j].second == 0L) {
            j++
        }
    }

    return transactions
}

package dev.jfronny.zerointerest.data.money

inline fun Collection<Money>.sum() = sumOf { it.amount }.toMoney()
inline fun Long.toMoney() = Money(this)
inline fun <T> Iterable<T>.sumOfM(selector: (T) -> Money): Money {
    return sumOf { selector(it).amount }.toMoney()
}

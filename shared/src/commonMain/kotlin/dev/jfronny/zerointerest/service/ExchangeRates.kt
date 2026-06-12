package dev.jfronny.zerointerest.service

import dev.jfronny.zerointerest.data.importedCurrencyToSymbol
import dev.jfronny.zerointerest.data.importedExchangeRateOrigin
import dev.jfronny.zerointerest.data.importedExchangeRates
import dev.jfronny.zerointerest.data.importedSymbolToCurrency
import dev.jfronny.zerointerest.data.money.MonetaryUnit

fun getExchangeRates(toUnit: MonetaryUnit): Map<MonetaryUnit, Double> {
    val candidates = candidates(toUnit)
    if (candidates.isEmpty()) return mapOf(toUnit to 1.0)
//    require(candidates.size == 1) { "Ambiguous exchange rate: $candidates" }
    val pickedCandidate = candidates.first()
    val toOrigin = if (pickedCandidate == importedExchangeRateOrigin) {
        1.0
    } else {
        importedExchangeRates[pickedCandidate]!!
    }
    return buildMap {
        for ((unit, rate) in importedExchangeRates) {
            if (toOrigin == 0.0 || rate == 0.0) continue
            put(unit, toOrigin / rate)
            val symbol = importedCurrencyToSymbol[unit] ?: continue
            put(symbol, toOrigin / rate)
        }
        put(toUnit, 1.0)
        put(pickedCandidate, 1.0)
    }
}

private fun candidates(from: MonetaryUnit): Set<MonetaryUnit> = buildSet {
    if (importedExchangeRates.containsKey(from) || from == importedExchangeRateOrigin) add(from)
    val currency = importedSymbolToCurrency[from] ?: return@buildSet
    if (importedExchangeRates.containsKey(currency) || currency == importedExchangeRateOrigin) add(currency)
}

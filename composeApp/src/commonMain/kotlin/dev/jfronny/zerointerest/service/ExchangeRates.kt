package dev.jfronny.zerointerest.service

import dev.jfronny.zerointerest.data.money.MonetaryUnit

fun getExchangeRates(toUnit: MonetaryUnit): Map<MonetaryUnit, Double> {
    val candidates = candidates(toUnit)
    if (candidates.isEmpty()) return mapOf(toUnit to 1.0)
//    require(candidates.size == 1) { "Ambiguous exchange rate: $candidates" }
    val pickedCandidate = candidates.first()
    val toOrigin = if (pickedCandidate.code == importedExchangeRateOrigin) 1.0
    else importedExchangeRates[pickedCandidate.code]!!
    return buildMap {
        for ((unit, rate) in importedExchangeRates) {
            if (toOrigin == 0.0 || rate == 0.0) continue
            put(MonetaryUnit(unit), toOrigin / rate)
            val symbol = importedCurrencyToSymbol[unit] ?: continue
            put(MonetaryUnit(symbol), toOrigin / rate)
        }
        put(toUnit, 1.0)
        put(pickedCandidate, 1.0)
    }
}

private fun candidates(from: MonetaryUnit): Set<MonetaryUnit> = buildSet {
    if (importedExchangeRates.containsKey(from.code) || from.code == importedExchangeRateOrigin) add(from)
    val currency = importedSymbolToCurrency[from.code] ?: return@buildSet
    if (importedExchangeRates.containsKey(currency) || currency == importedExchangeRateOrigin) add(MonetaryUnit(currency))
}

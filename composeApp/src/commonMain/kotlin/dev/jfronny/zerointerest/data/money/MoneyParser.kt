package dev.jfronny.zerointerest.data.money

import dev.jfronny.zerointerest.util.multiplyExact

class MoneyParser(
    val input: String,
    val targetUnit: MonetaryUnit,
    val exchangeRates: Map<MonetaryUnit, Double>
) {
    init {
        require(exchangeRates[targetUnit] == 1.0) { "Target unit must be the base unit" }
    }

    constructor(input: String, unit: MonetaryUnit) : this(
        input,
        unit,
        mapOf(unit to 1.0),
    )

    private var start = 0
    private var current = 0

    private fun match(symbol: Char): Boolean {
        skipWhitespace()
        if (current >= input.length) return false
        if (input[current] == symbol) {
            current++
            return true
        }
        return false
    }

    private fun consume(symbol: Char) {
        if (match(symbol)) return
        throw Exception("Expected $symbol")
    }

    private fun skipWhitespace() {
        while (current < input.length && input[current].isWhitespace()) current++
        start = current
    }

    private fun error(message: String) = ParseException("Error at $current: $message")

    fun parse(): Result {
        if (input.isBlank()) return Result(0, consumedUnit = false, usedMath = false)
        val result = expression()
        skipWhitespace()
        if (current < input.length) throw error("Unexpected character")
        return result
    }

    private fun expression(): Result = term()

    private fun term(): Result {
        var expr = factor()
        while (true) {
            expr = when {
                match('+') -> {
                    val right = factor()
                    Result(expr.amount + right.amount, usedMath = true, consumedUnit = expr.consumedUnit || right.consumedUnit)
                }
                match('-') -> {
                    val right = factor()
                    Result(expr.amount - right.amount, usedMath = true, consumedUnit = expr.consumedUnit || right.consumedUnit)
                }
                else -> return expr
            }
        }
    }

    private fun factor(): Result {
        var term = primary()
        while (true) {
            term = when {
                match('*') -> {
                    val right = primary()
                    if (term.consumedUnit && right.consumedUnit) throw error("Cannot multiply by monetary amount")
                    val value = try {
                        (term.amount multiplyExact right.amount) / 100
                    } catch (_: ArithmeticException) {
                        throw error("Overflow")
                    }
                    Result(value, usedMath = true, consumedUnit = term.consumedUnit || right.consumedUnit)
                }
                match('/') -> {
                    val right = primary()
                    if (right.consumedUnit) throw error("Cannot divide by monetary amount")
                    val value = try {
                        (term.amount multiplyExact 100) / right.amount
                    } catch (_: ArithmeticException) {
                        throw error("Overflow")
                    }
                    Result(value, usedMath = true, consumedUnit = term.consumedUnit || right.consumedUnit)
                }
                maybeConsumeUnit() -> {
                    if (term.consumedUnit) throw error("Cannot multiply by monetary amount")
                    val amount = if (consumedUnit!!.key == targetUnit) term.amount
                    else (term.amount * consumedUnit!!.value).toLong()
                    term.copy(amount = amount, consumedUnit = true, usedMath = term.usedMath || consumedUnit!!.key != targetUnit)
                }
                else -> return term
            }
        }
    }

    private fun primary(): Result {
        skipWhitespace()
        if (current >= input.length) throw error("Unexpected end of input")
        return when (input[current]) {
            in '0'..'9' -> number()
            '-' -> number()
            '(' -> {
                current++
                val result = expression()
                consume(')')
                result
            }
            else -> throw error("Expected number")
        }
    }

    private fun number(): Result {
        val sign = if (input[current] == '-') {
            current++
            start = current
            -1
        } else {
            1
        }
        while (current < input.length && input[current].isDigit()) current++
        val value = if (current < input.length && (input[current] == '.' || input[current] == ',')) {
            val left = input.substring(start, current)
            current++
            start = current
            if (current + 2 > input.length || !input[current++].isDigit()
                || !input[current++].isDigit())
                throw error("Expected two digits after decimal point")
            val right = input.substring(start, current)
            try {
                sign * (left + right).toLong()
            } catch (_: NumberFormatException) {
                throw error("Invalid number")
            }
        } else {
            val part = input.substring(start, current)
            try {
                sign * part.toLong() multiplyExact 100
            } catch (_: ArithmeticException) {
                throw error("Overflow")
            } catch (_: NumberFormatException) {
                throw error("Invalid number")
            }
        }
        return Result(value, consumedUnit = false, usedMath = false)
    }

    private var consumedUnit: Map.Entry<MonetaryUnit, Double>? = null
    private fun maybeConsumeUnit(): Boolean {
        skipWhitespace()
        if (current >= input.length || input[current] in symbols) return false
        val head = input.substring(current)
        exchangeRates.forEach { entry ->
            if (head.startsWith(entry.key.code, ignoreCase = true)) {
                current += entry.key.code.length
                consumedUnit = entry
                return@maybeConsumeUnit true
            }
        }
        return false
    }

    data class Result(
        val amount: Long,
        val consumedUnit: Boolean,
        val usedMath: Boolean,
    ) {
        fun toMoney() = Money(amount)
    }

    companion object {
        val symbols = setOf('+', '*', '/', '(', ')') // See also ConvertExchangeRatesTask.bannedSymbols
        fun parse(input: String, unit: MonetaryUnit) = MoneyParser(input, unit).parse()
        fun parse(input: String, targetUnit: MonetaryUnit, exchangeRates: Map<MonetaryUnit, Double>) =
            MoneyParser(input, targetUnit, exchangeRates).parse()
    }

    class ParseException(message: String) : Exception(message)
}

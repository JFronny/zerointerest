package dev.jfronny.zerointerest

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

abstract class ConvertExchangeRatesTask : DefaultTask() {
    @get:InputFile
    abstract val ecbExchangeRatesFile: RegularFileProperty
    @get:InputFile
    abstract val frankfurterExchangeRatesFile: RegularFileProperty
    @get:InputFile
    abstract val symbolMapFile: RegularFileProperty
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "build"
        outputDirectory.convention(project.layout.buildDirectory.dir("generated/exchangerates"))
    }

    @TaskAction
    fun convert() {
        val ratesMap = mergeRates(readEcbRatesFile(), readFrankfurterRatesFile())
        val symbolMap = pruneCollidingSymbols(filterHardToParse(readSymbolMapFile()), ratesMap.keys)

        val MonetaryUnit = ClassName.bestGuess("dev.jfronny.zerointerest.data.money.MonetaryUnit")

        FileSpec.builder("dev.jfronny.zerointerest.data", "ExchangeRates")
            .addProperty(
                PropertySpec.builder("importedExchangeRateOrigin", MonetaryUnit)
                    .initializer("%T(%S)", MonetaryUnit, "EUR")
                    .build()
            )
            .addProperty(
                PropertySpec.builder("importedExchangeRates", Map::class.asClassName().parameterizedBy(MonetaryUnit, Double::class.asClassName()))
                    .initializer(CodeBlock.builder().apply {
                        add("mapOf(")
                        ratesMap.map { (currency, rate) ->
                            add("\n%T(%S) to %L,", MonetaryUnit, currency, rate)
                        }
                        add("\n)")
                    }.build())
                    .build()
            )
            .addProperty(
                PropertySpec.builder("importedSymbolToCurrency", Map::class.asClassName().parameterizedBy(MonetaryUnit, MonetaryUnit))
                    .initializer(CodeBlock.builder().apply {
                        add("mapOf(")
                        symbolMap.map { (currency, symbol) ->
                            add("\n%T(%S) to %T(%S),", MonetaryUnit, symbol, MonetaryUnit, currency)
                        }
                        add("\n)")
                    }.build())
                    .build()
            )
            .addProperty(
                PropertySpec.builder("importedCurrencyToSymbol", Map::class.asClassName().parameterizedBy(MonetaryUnit, MonetaryUnit))
                    .initializer("importedSymbolToCurrency.entries.associate { it.value to it.key }")
                    .build()
            )
            .build()
            .writeTo(outputDirectory.asFile.get())
    }

    private fun readEcbRatesFile() = buildMap {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(ecbExchangeRatesFile.asFile.get())
        val rates = doc.getElementsByTagName("Cube")
        rates.forEach {
            if (it !is Element) return@forEach
            val currency = it.getAttribute("currency").ifBlank { null } ?: return@forEach
            require(currency.matches(Regex("[A-Z]{3}"))) { "Invalid currency code: $currency" }
            val rate = it.getAttribute("rate").toDouble()
            this@buildMap[currency] = rate
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun readFrankfurterRatesFile(): Map<String, Double> {
        val rates = frankfurterExchangeRatesFile.asFile.get().inputStream().use { Json.decodeFromStream<List<FrankfurterRate>>(it) }
        val dates = rates.distinctBy { it.date }
        require(dates.size <= 3) { "Expected exactly one date in Frankfurter exchange rates file, got $dates" }
        val bases = rates.map { it.base }.distinct()
        require(bases.size == 1) { "Expected exactly one base currency in Frankfurter exchange rates file, got $bases" }
        return rates.associate { it.quote to it.rate }
    }

    private fun readSymbolMapFile() = buildMap {
        symbolMapFile.asFile.get().useLines { it.filter { it.isNotBlank() && it.startsWith("  ") }.forEach { line ->
            val pair = line.trim().trimEnd(',').split(":")
            require(pair.size == 2) { "Invalid line: $line" }
            val currency = pair[0].trim()
            val symbol = pair[1].trim().trim('\'')
            require(currency.matches(Regex("[A-Z]{3}"))) { "Invalid currency code: $currency" }
            require(symbol.isNotBlank() && !symbol.contains("\\") && !symbol.contains("\"")) { "Invalid symbol: $symbol" }
            this@buildMap[currency] = symbol
        } }
    }

    private fun mergeRates(ecb: Map<String, Double>, frankfurter: Map<String, Double>): Map<String, Double> = buildMap {
        ecb.forEach { (currency, rate) ->
            require(rate > 0.000001) { "Tiny ECB exchange rate for $currency: $rate" }
            put(currency, rate)
        }
        frankfurter.forEach { (currency, rate) ->
            require(rate > 0.000001) { "Tiny Frankfurter exchange rate for $currency: $rate" }
            if (currency in ecb) {
                val ecbRate = ecb[currency]!!
                require(abs((rate - ecbRate) / ecbRate) < 0.2) { "Inconsistent exchange rates for $currency: $ecbRate (ECB) vs $rate (Frankfurter)" }
                put(currency, ecbRate + (rate - ecbRate) / 2)
            } else {
                put(currency, rate)
            }
        }
    }

    private val bannedSymbols = setOf('+', '*', '/', '(', ')') // See also MoneyParser.symbols
    private fun filterHardToParse(symbolMap: Map<String, String>) = buildMap {
        symbolMap.forEach { (currency, symbol) ->
            if (symbol.isBlank()) return@forEach
            if (symbol.trim() != symbol) return@forEach
            val first = symbol.first()
            if (first == '.' || first == ',') return@forEach
            if (symbol.any { it.isDigit() || it in bannedSymbols }) return@forEach
            this@buildMap[currency] = symbol
        }
    }

    // this adjustment set was chosen arbitrarily to meet the map interface
    private val extraPrioritizedCurrencies = mapOf(
        "$"  to "USD",
        "£"  to "GBP",
        "L"  to "HNL",
        "ƒ"  to "AWG",
        "лв" to "KGS",
        "Br" to "BYN",
        "¥"  to "CNY",
        "₱"  to "PHP",
        "kr" to "SEK",
        "﷼"  to "SAR",
        "₩"  to "KRW",
        "₨" to "PKR",
        "lei" to "RON",
        "K"  to "MMK",
        "UM" to "MRU",
        "Db" to "STN",
    )

    private fun pruneCollidingSymbols(symbolMap: Map<String, String>, knownCurrencies: Set<String>) = buildMap {
        val prioritizedCurrencies = knownCurrencies.filter { c -> symbolMap[c]?.let { extraPrioritizedCurrencies[it] == c } ?: true }
        extraPrioritizedCurrencies.forEach { (k, v) ->
            require(v in prioritizedCurrencies) { "Extra prioritized currency $v not in known currencies $prioritizedCurrencies"}
            require(symbolMap[v] == k) { "Extra prioritized currency $v has wrong symbol $k" }
        }
        var exception: IllegalArgumentException? = null
        for ((k, v) in symbolMap) {
            val matching = symbolMap.filter { it.value == v }
            if (matching.size == 1) {
                this@buildMap[k] = v
                continue
            }
            val prioritized = matching.filter { it.key in prioritizedCurrencies }
            if (prioritized.count() != 1) {
                exception = exception ?: IllegalArgumentException("Symbol map contains colliding symbols")
                exception.addSuppressed(IllegalArgumentException("Colliding currencies for ${v}: $prioritized of ${matching.keys}"))
            } else {
                if (k in prioritizedCurrencies) this@buildMap[k] = v
            }
        }
        if (exception != null) throw exception
    }
}

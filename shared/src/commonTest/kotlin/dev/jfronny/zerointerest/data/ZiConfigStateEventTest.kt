package dev.jfronny.zerointerest.data

import dev.jfronny.zerointerest.data.money.MonetaryUnit
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ZiConfigStateEventTest : FunSpec({
    // Same configuration as the Json Trixnity uses for events
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun parse(raw: String): ZiConfigStateEvent = json.decodeFromString(ZiConfigStateEvent.Serializer, raw)

    test("maps versions below the current one to V0") {
        parse("""{"version": 0}""") shouldBe ZiConfigStateEvent.V0(0)
        parse("""{"version": -1, "currency": "EUR"}""") shouldBe ZiConfigStateEvent.Newer(-1)
    }

    test("maps the current version to V1") {
        parse("""{"version": 1, "currency": "EUR"}""") shouldBe ZiConfigStateEvent.V1(rawCurrency = "EUR")
        parse("""{"version": 1}""") shouldBe ZiConfigStateEvent.V1()
    }

    test("maps newer versions to Newer, ignoring unknown fields") {
        parse("""{"version": 2, "currency": "EUR"}""") shouldBe ZiConfigStateEvent.Newer(2)
        parse("""{"version": 2, "currency": {"code": "EUR"}, "features": [1]}""") shouldBe
            ZiConfigStateEvent.Newer(2)
    }

    test("throws when the version is missing or not a number") {
        shouldThrow<SerializationException> { parse("{}") }
        shouldThrow<SerializationException> { parse("""{"version": 1.0}""") }
        shouldThrow<SerializationException> { parse("""{"version": ${Long.MAX_VALUE / 2}}""") }
    }

    test("currency falls back to the default currency for a missing or invalid currency") {
        ZiConfigStateEvent.V0().currency shouldBe MonetaryUnit.default
        ZiConfigStateEvent.V1(rawCurrency = "123").currency shouldBe MonetaryUnit.default
        ZiConfigStateEvent.V1(rawCurrency = "EUR").currency shouldBe MonetaryUnit("EUR")
    }

    test("serializes V1 with the protocol field names") {
        json.encodeToString(ZiConfigStateEvent.Serializer, ZiConfigStateEvent.V1(rawCurrency = "EUR"))
            .let(json::parseToJsonElement) shouldBe json.parseToJsonElement("""{"version": 1, "currency": "EUR"}""")
    }
})

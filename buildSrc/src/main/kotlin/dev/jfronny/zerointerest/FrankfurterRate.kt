package dev.jfronny.zerointerest

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class FrankfurterRate(
    val date: @Serializable(with = LocalDateSerializaer::class)  LocalDate,
    val base: String,
    val quote: String,
    val rate: Double,
)

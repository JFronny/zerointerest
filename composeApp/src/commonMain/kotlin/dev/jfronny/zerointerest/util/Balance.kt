package dev.jfronny.zerointerest.util

import kotlin.math.abs

fun formatBalance(balance: Long): String {
    // 0 -> 0.00 €
    // 1234 -> 12.34 €
    // -1024 -> -10.24 €
    val euros = balance / 100
    val cents = abs(balance % 100)
    return "$euros.${cents.toString().padStart(2, '0')} €"
}
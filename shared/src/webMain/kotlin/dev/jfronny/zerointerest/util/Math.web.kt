package dev.jfronny.zerointerest.util

actual infix fun Long.multiplyExact(other: Long): Long = multiplyExactNaive(other)

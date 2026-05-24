package dev.jfronny.zerointerest.util

expect infix fun Long.multiplyExact(other: Long): Long
fun Long.multiplyExactNaive(other: Long): Long {
    if (this == 0L || other == 0L) return 0L
    val result = this * other
    if (result / this != other) throw ArithmeticException("Overflow")
    return result
}

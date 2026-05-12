package dev.jfronny.zerointerest.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll

class MathTest : FunSpec() {
    init {
        test("multiplyExact is consistent") {
            checkAll<Long, Long> { a, b ->
                val result = try {
                    Result.success(a multiplyExact b)
                } catch (e: ArithmeticException) {
                    Result.failure(e)
                }
                if (result.isFailure) {
                    shouldThrow<ArithmeticException> { a.multiplyExactNaive(b) }
                } else {
                    a.multiplyExactNaive(b) shouldBe result.getOrThrow()
                }
            }
        }
    }
}
package dev.jfronny.zerointerest.util

import kotlin.fold as foldK

inline fun <T, R> Result<T>?.fold(
    onSuccess: (T) -> R,
    onFailure: (Throwable) -> R,
    onNull: () -> R,
): R {
    if (this == null) return onNull()
    return this.foldK(onSuccess = onSuccess, onFailure = onFailure)
}

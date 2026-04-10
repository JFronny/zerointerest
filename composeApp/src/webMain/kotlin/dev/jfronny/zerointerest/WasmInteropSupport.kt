package dev.jfronny.zerointerest

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.get
import kotlin.js.length

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalWasmJsInterop::class)
inline fun <T : JsAny?> JsArray<T>.forEach(action: (T) -> Unit) {
    for (i in 0..<length) action(this[i] as T)
}

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalWasmJsInterop::class)
inline fun <T : JsAny?, R> JsArray<T>.map(action: (T) -> R): List<R> {
    return List(length) { action(this[it] as T) }
}

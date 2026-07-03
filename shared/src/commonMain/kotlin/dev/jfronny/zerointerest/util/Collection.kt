package dev.jfronny.zerointerest.util

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf

inline fun <K, V> MutableMap<K, V>.compute(key: K, ifMissing: () -> V, ifPresent: (V) -> V): V {
    val value = get(key)
    return if (value == null) {
        val answer = ifMissing()
        put(key, answer)
        answer
    } else {
        val answer = ifPresent(value)
        put(key, answer)
        answer
    }
}

inline fun <V> buildPersistentList(builderAction: MutableList<V>.() -> Unit): PersistentList<V> = persistentListOf<V>().mutate(builderAction)

inline fun <K, V> buildPersistentHashMap(builderAction: MutableMap<K, V>.() -> Unit): PersistentMap<K, V> = persistentHashMapOf<K, V>().mutate(builderAction)

package dev.jfronny.zerointerest.util

import androidx.navigation.NavType
import androidx.savedstate.SavedState
import de.connect2x.trixnity.core.model.EventId

object EventIdNavType : NavType<EventId?>(isNullableAllowed = true) {
    override fun put(bundle: SavedState, key: String, value: EventId?) = StringType.put(bundle, key, value?.full)
    override fun get(bundle: SavedState, key: String): EventId? = StringType[bundle, key]?.let { EventId(it) }
    override fun parseValue(value: String): EventId? = StringType.parseValue(value)?.let { EventId(it) }
}


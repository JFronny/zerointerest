package dev.jfronny.zerointerest.util

import androidx.navigation.NavType
import androidx.savedstate.SavedState
import de.connect2x.trixnity.core.model.RoomId

object RoomIdNavType : NavType<RoomId?>(isNullableAllowed = true) {
    override fun put(bundle: SavedState, key: String, value: RoomId?) = StringType.put(bundle, key, value?.full)
    override fun get(bundle: SavedState, key: String): RoomId? = StringType[bundle, key]?.let { RoomId(it) }
    override fun parseValue(value: String): RoomId? = StringType.parseValue(value)?.let { RoomId(it) }
}

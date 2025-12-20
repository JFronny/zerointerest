package dev.jfronny.zerointerest.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.folivo.trixnity.core.model.RoomId

class Settings(private val store: DataStore<Preferences>) {
    private val rememberedRoom = stringPreferencesKey("rememberedRoom")
    private val defaultHomeserver = stringPreferencesKey("defaultHomeserver")

    suspend fun rememberRoom(roomId: RoomId) {
        store.updateData {
            it.toMutablePreferences().apply {
                set(rememberedRoom, roomId.full)
            }
        }
    }

    suspend fun rememberedRoom(): RoomId? {
        return store.data.map { it[rememberedRoom]?.let { RoomId(it) } }.first()
    }

    suspend fun clearRememberedRoom() {
        store.updateData {
            it.toMutablePreferences().apply {
                remove(rememberedRoom)
            }
        }
    }

    suspend fun setDefaultHomeserver(homeserver: String) {
        store.updateData {
            it.toMutablePreferences().apply {
                set(defaultHomeserver, homeserver)
            }
        }
    }

    suspend fun defaultHomeserver(): String {
        return store.data.map { it[defaultHomeserver] ?: FALLBACK_HOMESERVER }.first()
    }

    companion object {
        const val FALLBACK_HOMESERVER = "https://matrix.org"
    }
}

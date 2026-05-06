package dev.jfronny.zerointerest.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import de.connect2x.trixnity.core.model.RoomId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class Settings(private val store: DataStore<Preferences>) {
    private val rememberedRoom = stringPreferencesKey("rememberedRoom")
    private val defaultHomeserver = stringPreferencesKey("defaultHomeserver")
    private val flipBalancesKey = booleanPreferencesKey("flipBalances")
    private val debugHintsKey = booleanPreferencesKey("debugHints")

    val flipBalances = store.data.map { it[flipBalancesKey] ?: true }
    val debugHints = store.data.map { it[debugHintsKey] ?: false }

    suspend fun setFlipBalances(flip: Boolean) {
        store.updateData {
            it.toMutablePreferences().apply {
                set(flipBalancesKey, flip)
            }
        }
    }

    suspend fun setDebugHints(enabled: Boolean) {
        store.updateData {
            it.toMutablePreferences().apply {
                set(debugHintsKey, enabled)
            }
        }
    }

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

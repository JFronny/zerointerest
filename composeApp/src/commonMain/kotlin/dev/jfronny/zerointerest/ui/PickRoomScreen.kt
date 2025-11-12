package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.jfronny.zerointerest.MatrixClientService
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.hasBeenReplaced
import net.folivo.trixnity.core.model.RoomId
import org.koin.compose.koinInject

@Composable
fun PickRoomScreen(onPick: (RoomId) -> Unit) {
    val client = koinInject<MatrixClientService>().get()
    val rooms by client.room.getAll().flattenValues().collectAsState(initial = setOf())
    PickRoomScreen(
        rooms = rooms,
        onPick = onPick
    )
}

@Composable
fun PickRoomScreen(
    rooms: Set<Room>,
    onPick: (RoomId) -> Unit
) {
    val sorted = remember(rooms) {
        rooms.asSequence()
            .filterNot { it.hasBeenReplaced }
            .sortedWith(compareBy({ it.name?.explicitName ?: "\uFFFF" }, { it.roomId.full }))
            .toList()
    }
    if (sorted.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "No rooms available",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }
    LazyColumn {
        items(sorted) {
            RoomListItem(
                room = it,
                onClick = { onPick(it.roomId) }
            )
        }
    }
}

@Composable
fun RoomListItem(
    room: Room,
    onClick: () -> Unit
) {
    val name = room.name?.explicitName ?: room.roomId.full
    ListItem(
        headlineContent = { Text(name) },
        modifier = Modifier.clickable { onClick() }
    )
}

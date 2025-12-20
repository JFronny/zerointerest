package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.jfronny.zerointerest.service.MatrixClientService
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.hasBeenReplaced
import net.folivo.trixnity.core.model.RoomId
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import zerointerest.composeapp.generated.resources.*

@Composable
fun PickRoomScreen(onPick: (RoomId) -> Unit, logout: () -> Unit) {
    val rxclient by koinInject<MatrixClientService>().client.collectAsState(null)
    val client = rxclient ?: return
    val rooms by remember(client) { client.room.getAll().flattenValues() }.collectAsState(initial = setOf())
    PickRoomScreen(
        rooms = rooms,
        onPick = onPick,
        logout = logout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickRoomScreen(
    rooms: Set<Room>,
    onPick: (RoomId) -> Unit,
    logout: () -> Unit,
) = Scaffold(
    topBar = {
        TopAppBar(
            title = { Text(stringResource(Res.string.pick_a_room)) },
            actions = {
                IconButton(onClick = logout) {
                    Icon(Icons.AutoMirrored.Filled.Logout, stringResource(Res.string.logout))
                }
            }
        )
    }
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
                text = stringResource(Res.string.no_rooms_available),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return@Scaffold
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

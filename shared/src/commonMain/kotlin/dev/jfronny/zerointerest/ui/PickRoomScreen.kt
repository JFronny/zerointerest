package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.tooling.preview.Preview
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.RoomDisplayName
import de.connect2x.trixnity.client.store.hasBeenReplaced
import de.connect2x.trixnity.core.model.RoomId
import dev.jfronny.zerointerest.shared.generated.resources.*
import dev.jfronny.zerointerest.service.client.MatrixClientService
import dev.jfronny.zerointerest.ui.component.MoreOptionsButton
import dev.jfronny.zerointerest.ui.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun PickRoomScreen(onPick: (RoomId) -> Unit, openSettings: () -> Unit) {
    val rxclient by koinInject<MatrixClientService>().client.collectAsState(null)
    val client = rxclient ?: return
    val rooms by remember(client) { client.room.getAll().flattenValues() }.collectAsState(initial = setOf())
    PickRoomContent(
        rooms = rooms,
        onPick = onPick,
        openSettings = openSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickRoomContent(
    rooms: Set<Room>,
    onPick: (RoomId) -> Unit,
    openSettings: () -> Unit,
) = Scaffold(
    topBar = {
        TopAppBar(
            title = { Text(stringResource(Res.string.pick_a_room)) },
            actions = {
                MoreOptionsButton(openSettings = openSettings)
            }
        )
    }
) { paddingValues ->
    val sorted = remember(rooms) {
        rooms.asSequence()
            .filterNot { it.hasBeenReplaced }
            .sortedWith(compareBy({ it.name?.explicitName ?: "\uFFFF" }, { it.roomId.full }))
            .toList()
    }
    if (sorted.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Text(
                text = stringResource(Res.string.no_rooms_available),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return@Scaffold
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues)
    ) {
        items(sorted) {
            RoomListItem(
                room = it,
                onClick = { onPick(it.roomId) }
            )
        }
    }
}

@Preview
@Composable
private fun PickRoomScreenPreview() = AppTheme {
    PickRoomContent(
        rooms = setOf(
            Room(
                roomId = RoomId("!room1:example.com"),
                name = RoomDisplayName(explicitName = "Room 1", summary = null),
            ),
            Room(
                roomId = RoomId("!room2:example.com"),
                name = RoomDisplayName(explicitName = "Room 2", summary = null),
            ),
            Room(
                roomId = RoomId("!room3:example.com"),
                name = RoomDisplayName(explicitName = "Room 3", summary = null),
            ),
        ),
        onPick = {},
        openSettings = {}
    )
}

@Composable
private fun RoomListItem(
    room: Room,
    onClick: () -> Unit
) {
    val name = room.name?.explicitName ?: room.roomId.full
    ListItem(
        headlineContent = { Text(name) },
        modifier = Modifier.clickable { onClick() }
    )
}

@Preview
@Composable
private fun RoomListItemPreview() = AppTheme {
    RoomListItem(
        room = Room(
            roomId = RoomId("!room1:example.com"),
            name = RoomDisplayName(explicitName = "Room 1", summary = null),
        ),
        onClick = {}
    )
}

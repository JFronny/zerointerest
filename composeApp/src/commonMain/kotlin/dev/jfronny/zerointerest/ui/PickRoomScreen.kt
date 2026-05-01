package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.hasBeenReplaced
import de.connect2x.trixnity.core.model.RoomId
import dev.jfronny.zerointerest.composeapp.generated.resources.Res
import dev.jfronny.zerointerest.composeapp.generated.resources.no_rooms_available
import dev.jfronny.zerointerest.composeapp.generated.resources.pick_a_room
import dev.jfronny.zerointerest.composeapp.generated.resources.settings
import dev.jfronny.zerointerest.service.MatrixClientService
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun PickRoomScreen(onPick: (RoomId) -> Unit, openSettings: () -> Unit) {
    val rxclient by koinInject<MatrixClientService>().client.collectAsState(null)
    val client = rxclient ?: return
    val rooms by remember(client) { client.room.getAll().flattenValues() }.collectAsState(initial = setOf())
    PickRoomScreen(
        rooms = rooms,
        onPick = onPick,
        openSettings = openSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickRoomScreen(
    rooms: Set<Room>,
    onPick: (RoomId) -> Unit,
    openSettings: () -> Unit,
) = Scaffold(
    topBar = {
        TopAppBar(
            title = { Text(stringResource(Res.string.pick_a_room)) },
            actions = {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.settings)) },
                            onClick = {
                                expanded = false
                                openSettings()
                            }
                        )
                    }
                }
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

package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.RoomDisplayName
import de.connect2x.trixnity.client.store.hasBeenReplaced
import de.connect2x.trixnity.core.model.RoomId
import dev.jfronny.zerointerest.service.Settings
import dev.jfronny.zerointerest.service.client.MatrixClientService
import dev.jfronny.zerointerest.shared.generated.resources.*
import dev.jfronny.zerointerest.ui.component.IconSize
import dev.jfronny.zerointerest.ui.component.MoreOptionsButton
import dev.jfronny.zerointerest.ui.component.WebImageOrFallback
import dev.jfronny.zerointerest.ui.theme.AppTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun PickRoomScreen(onPick: (RoomId) -> Unit, openSettings: () -> Unit) {
    val settings = koinInject<Settings>()
    val scope = rememberCoroutineScope()
    val rxclient by koinInject<MatrixClientService>().client.collectAsState(null)
    val client = rxclient ?: return
    val rooms by remember(client) { client.room.getAll().flattenValues().map { it.toImmutableSet() } }.collectAsState(initial = persistentSetOf())
    val favorites by settings.favoriteRooms.collectAsState(initial = emptyList())

    val (favoriteRooms, otherRooms) = remember(rooms, favorites) {
        val favoriteIds = favorites.toSet()
        fun Sequence<Room>.prep() = filterNot { it.hasBeenReplaced }
            .sortedWith(
                compareBy(
                    { it.name?.explicitName ?: "\uFFFF" },
                    { it.roomId.full },
                ),
            ).toPersistentList()
        Pair(
            rooms.asSequence().filterNot { it.roomId !in favoriteIds }.prep(),
            rooms.asSequence().filterNot { it.roomId in favoriteIds }.prep(),
        )
    }

    PickRoomContent(
        favoriteRooms = favoriteRooms,
        otherRooms = otherRooms,
        onPick = onPick,
        onToggleFavorite = { roomId, favorite ->
            scope.launch { settings.setFavoriteRoom(roomId, favorite) }
        },
        openSettings = openSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickRoomContent(
    favoriteRooms: ImmutableList<Room>,
    otherRooms: ImmutableList<Room>,
    onPick: (RoomId) -> Unit,
    onToggleFavorite: (RoomId, Boolean) -> Unit,
    openSettings: () -> Unit,
) = Scaffold(
    topBar = {
        TopAppBar(
            title = { Text(stringResource(Res.string.pick_a_room)) },
            actions = {
                MoreOptionsButton(openSettings = openSettings)
            },
        )
    },
) { paddingValues ->
    var sheetRoom by remember { mutableStateOf<Room?>(null) }
    if (favoriteRooms.isEmpty() && otherRooms.isEmpty()) {
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
        modifier = Modifier.fillMaxSize().padding(paddingValues),
    ) {
        if (favoriteRooms.isNotEmpty()) {
            item(key = "favorites_header") {
                Text(
                    text = stringResource(Res.string.favorites),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(favoriteRooms, key = { it.roomId.full }) {
                RoomListItem(
                    room = it,
                    onClick = { onPick(it.roomId) },
                    onLongClick = { sheetRoom = it },
                )
            }
            if (otherRooms.isNotEmpty()) {
                item(key = "favorites_separator") {
                    HorizontalDivider()
                }
            }
        }
        items(otherRooms, key = { it.roomId.full }) {
            RoomListItem(
                room = it,
                onClick = { onPick(it.roomId) },
                onLongClick = { sheetRoom = it },
            )
        }
    }
    sheetRoom?.let { room ->
        val isFavorite = favoriteRooms.any { it.roomId == room.roomId }
        ModalBottomSheet(onDismissRequest = { sheetRoom = null }) {
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(
                            if (isFavorite) Res.string.unfavorite_room else Res.string.favorite_room,
                        ),
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = stringResource(Res.string.favorite),
                    )
                },
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .clickable {
                        onToggleFavorite(room.roomId, !isFavorite)
                        sheetRoom = null
                    },
            )
        }
    }
}

@Preview
@Composable
private fun PickRoomScreenPreview() = AppTheme {
    PickRoomContent(
        favoriteRooms = persistentListOf(
            Room(
                roomId = RoomId("!room1:example.com"),
                name = RoomDisplayName(explicitName = "Room 1", summary = null),
            ),
            Room(
                roomId = RoomId("!room3:example.com"),
                name = RoomDisplayName(explicitName = "Room 3", summary = null),
            ),
        ),
        otherRooms = persistentListOf(
            Room(
                roomId = RoomId("!room2:example.com"),
                name = RoomDisplayName(explicitName = "Room 2", summary = null),
            ),
        ),
        onPick = {},
        onToggleFavorite = { _, _ -> },
        openSettings = {},
    )
}

@Composable
private fun RoomListItem(
    room: Room,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val name = room.name?.explicitName ?: room.roomId.full
    ListItem(
        headlineContent = { Text(name) },
        leadingContent = {
            WebImageOrFallback(size = IconSize.Regular, name = name, url = room.avatarUrl, contentDescription = stringResource(Res.string.avatar))
        },
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
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
        onClick = {},
        onLongClick = {},
    )
}

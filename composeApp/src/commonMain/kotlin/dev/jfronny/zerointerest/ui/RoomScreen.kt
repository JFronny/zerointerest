package dev.jfronny.zerointerest.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.jfronny.zerointerest.MatrixClientService
import dev.jfronny.zerointerest.Platform
import net.folivo.trixnity.client.room
import net.folivo.trixnity.core.model.RoomId
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import zerointerest.composeapp.generated.resources.Res
import zerointerest.composeapp.generated.resources.app_icon

@Composable
fun RoomScreen(roomId: RoomId) {
    val matrixClient = koinInject<MatrixClientService>()
    val room by matrixClient.get().room.getById(roomId).collectAsState(null)
    var showContent by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer)
            .safeContentPadding()
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Viewing room ${room?.name?.explicitName ?: roomId.full}"
        )
        Button(onClick = { showContent = !showContent }) {
            Text("Click me!")
        }
        val platform = koinInject<Platform>()
        AnimatedVisibility(showContent) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(painterResource(Res.drawable.app_icon), null)
                Text("Compose: ${platform.name}")
            }
        }
    }
}

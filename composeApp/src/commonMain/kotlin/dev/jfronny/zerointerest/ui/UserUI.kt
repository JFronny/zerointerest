package dev.jfronny.zerointerest.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.media
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.avatarUrl
import net.folivo.trixnity.client.user
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

interface UserUI {
    @Composable
    operator fun invoke(userId: UserId) {
        val component = component(userId)
        component.Icon()
        Spacer(Modifier.width(8.dp))
        component.Name()
    }

    @Composable
    fun component(userId: UserId): Component

    interface Component {
        @Composable fun Icon()
        @Composable fun Name() {
            Text(name)
        }
        val name: String
    }

    companion object {
        @Composable
        operator fun invoke(client: MatrixClient, roomId: RoomId): UserUI {
            val flow = remember(roomId) { client.user.getAll(roomId) }
            val users by flow.collectAsState(emptyMap())
            return remember(users) { UserUIImpl(client, users) }
        }
    }
}

object PreviewUserUI : UserUI {
    @Composable
    override fun invoke(userId: UserId) {
        Text(userId.full)
    }

    @Composable
    override fun component(userId: UserId): UserUI.Component = object : UserUI.Component {
        @Composable
        override fun Icon() {
            FallbackIcon(userId.localpart)
        }

        override val name: String get() = userId.localpart
    }
}

class UserUIImpl(private val client: MatrixClient, private val users: Map<UserId, Flow<RoomUser?>>) : UserUI {
    @Composable
    override fun component(userId: UserId): UserUI.Component {
        val user = users[userId] ?: return PreviewUserUI.component(userId)
        val state by user.collectAsState(null)
        val name = state?.name ?: userId.full
        return object : UserUI.Component {
            @Composable
            override fun Icon() {
                val state = state
                if (state?.avatarUrl == null) {
                    FallbackIcon(name)
                    return
                }
                var media by remember { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(state.avatarUrl) {
                    media = client.media
                        .getMedia(state.avatarUrl!!)
                        .getOrNull()
                        ?.toByteArray(coroutineScope = this, maxSize = 1024 * 1024 * 8)
                        ?.decodeToImageBitmap()
                }
                media?.let {
                    IconWrapper {
                        Image(it, "Avatar")
                    }
                } ?: FallbackIcon(name)
            }

            override val name: String get() = name
        }
    }
}

@Composable
private fun FallbackIcon(name: String) {
    val initials = name
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { name.take(2).uppercase() }
    IconWrapper {
        Text(
            text = initials,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun IconWrapper(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

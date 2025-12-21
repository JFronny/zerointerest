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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jfronny.zerointerest.composeapp.generated.resources.Res
import dev.jfronny.zerointerest.composeapp.generated.resources.avatar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.media
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.avatarUrl
import net.folivo.trixnity.client.user
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.compose.resources.stringResource

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

class UserUIImpl(
    private val client: MatrixClient,
    private val users: Map<UserId, Flow<RoomUser?>>,
) : UserUI {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val bitmaps: Map<UserId, Flow<ImageBitmap?>> = users.mapValues { (k, v) ->
        v.mapLatest {
            it?.avatarUrl?.let {
                client.media.getMedia(it).getOrNull()?.toByteArray()?.decodeToImageBitmap()
            }
        }
    }

    @Composable
    override fun component(userId: UserId): UserUI.Component {
        val user = remember(userId, users) { users[userId] } ?: return PreviewUserUI.component(userId)
        val state by user.collectAsState(null)
        val name = remember(state) { state?.name ?: userId.full }
        val bitmap by remember(userId, users) { bitmaps[userId] ?: flowOf(null) }.collectAsState(null)
        return object : UserUI.Component {
            @Composable
            override fun Icon() {
                val state = state
                if (state?.avatarUrl == null) {
                    FallbackIcon(name)
                    return
                }
                bitmap?.let {
                    IconWrapper {
                        Image(it, stringResource(Res.string.avatar))
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

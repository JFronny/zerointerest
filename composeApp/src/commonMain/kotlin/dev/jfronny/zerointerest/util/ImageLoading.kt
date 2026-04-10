package dev.jfronny.zerointerest.util

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.media
import dev.jfronny.zerointerest.service.MatrixClientService
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import okio.Buffer
import org.kodein.emoji.compose.EmojiUrl

@Composable
fun nihEmojiDownloader(httpClient: HttpClient) : suspend (EmojiUrl) -> ByteArray {
    val ctx = LocalPlatformContext.current
    return dl@{
        when (it.type) {
            EmojiUrl.Type.SVG -> {
                val loader = SingletonImageLoader.get(ctx)
                val image = loader.execute(ImageRequest.Builder(ctx)
                    .data(it.url)
                    .build()
                ).image

                val diskCache = loader.diskCache
                val snapshot = diskCache?.openSnapshot(it.url)
                if (snapshot != null) {
                    try {
                        return@dl diskCache.fileSystem.read(snapshot.data) { readByteArray() }
                    } finally {
                        snapshot.close()
                    }
                } else {
                    val bytes = httpClient.get(it.url).readRawBytes()
                    val editor = diskCache?.openEditor(it.url)
                    if (editor != null) {
                        try {
                            diskCache.fileSystem.write(editor.data) { write(bytes) }
                            editor.commit()
                        } catch (e: Exception) {
                            editor.abort()
                        }
                    }
                    return@dl bytes
                }
            }
            EmojiUrl.Type.Lottie -> httpClient.get(it.url).readRawBytes()
        }
    }
}

class CoilMxcFetcher(
    private val client: MatrixClient,
    private val url: String,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val result = client.media.getMedia(url).getOrNull()?.toByteArray() ?: return null
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().apply { write(result) },
                fileSystem = options.fileSystem
            ),
            mimeType = null,
            dataSource = DataSource.NETWORK
        )
    }

    class Factory(private val service: MatrixClientService) : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher? {
            if (!data.scheme.equals("mxc", ignoreCase = true)) return null
            val client = service.client.value ?: return null
            return CoilMxcFetcher(client, data.toString(), options)
        }
    }
}

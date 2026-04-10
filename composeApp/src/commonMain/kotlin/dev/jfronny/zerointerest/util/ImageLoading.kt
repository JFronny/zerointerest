package dev.jfronny.zerointerest.util

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.media
import dev.jfronny.zerointerest.service.MatrixClientService
import okio.Buffer

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

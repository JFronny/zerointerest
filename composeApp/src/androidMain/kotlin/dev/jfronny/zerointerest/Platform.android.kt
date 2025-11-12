package dev.jfronny.zerointerest

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room
import io.ktor.client.engine.android.*
import net.folivo.trixnity.client.store.repository.room.TrixnityRoomDatabase
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.scope.Scope

class AndroidPlatform(private val context: Context) : AbstractPlatform(context.dataDir.toOkioPath()) {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override fun trixnityDatabaseBuilder() = Room.databaseBuilder(context, TrixnityRoomDatabase::class.java, "trixnity.db")
    override fun getHttpClientEngine() = Android.create {}
}

actual fun Scope.getPlatform(): Platform = AndroidPlatform(androidContext().applicationContext)
@Composable
actual fun getPlatformTheme(darkTheme: Boolean): ColorScheme? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val context = LocalContext.current
    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
} else null
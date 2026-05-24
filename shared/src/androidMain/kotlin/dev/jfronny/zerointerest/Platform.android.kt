package dev.jfronny.zerointerest

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.text.format.DateUtils
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import de.connect2x.trixnity.client.store.repository.room.TrixnityRoomDatabase
import dev.jfronny.zerointerest.db.ZeroInterestRoomDatabase
import dev.jfronny.zerointerest.shared.generated.resources.Res
import dev.jfronny.zerointerest.shared.generated.resources.timestamp_just_now
import io.ktor.client.engine.android.Android
import okio.Path.Companion.toOkioPath
import org.jetbrains.compose.resources.stringResource
import org.koin.android.ext.koin.androidContext
import org.koin.core.scope.Scope
import java.util.Calendar
import kotlin.time.Instant
import androidx.room3.Room as Room3

// Rooms very smart API design decisions require us to get a context from the DatabaseConstructor object
// Which cannot be passed any arguments
// This hack is therefore unavoidable
@SuppressLint("StaticFieldLeak")
private lateinit var context: Context

class AndroidPlatform(private val context: Context) : AbstractPlatform(context.dataDir.toOkioPath()) {
    init {
        dev.jfronny.zerointerest.context = context
    }

    override val name: String = "Android"
    override fun trixnityDatabaseBuilder() = Room.databaseBuilder(context, TrixnityRoomDatabase::class.java, TRIXNITY_NAME)
    override fun zerointerestDatabaseBuilder() = Room3.databaseBuilder(context, ZeroInterestRoomDatabase::class.java, ZEROINTEREST_NAME)
    override fun createDataStore(): DataStore<Preferences> = createDataStore {
        context.filesDir.resolve(DATASTORE_NAME).toOkioPath()
    }

    override fun getHttpClientEngine() = Android.create {}
}

actual fun Scope.getPlatform(): Platform = AndroidPlatform(androidContext().applicationContext)
@Composable
actual fun getPlatformTheme(darkTheme: Boolean): ColorScheme? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val context = LocalContext.current
    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
} else null

actual fun addShutdownHook(block: () -> Unit) = Runtime.getRuntime().addShutdownHook(Thread(block))

@Composable
actual fun Instant.formatLocalized(style: TimestampStyle): String {
    val epochMillis = toEpochMilliseconds()
    val now = System.currentTimeMillis()
    val diff = kotlin.math.abs(now - epochMillis)

    // < 1 minute
    if (diff < DateUtils.MINUTE_IN_MILLIS) {
        return stringResource(Res.string.timestamp_just_now)
    }

    // < 1 week -> relative
    if (diff < DateUtils.WEEK_IN_MILLIS) {
        return DateUtils.getRelativeTimeSpanString(
            epochMillis,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    val flags = when {
        DateUtils.isToday(epochMillis) ->
            DateUtils.FORMAT_SHOW_TIME

        isThisYear(epochMillis) ->
            DateUtils.FORMAT_SHOW_DATE or
                    DateUtils.FORMAT_NO_YEAR

        else ->
            DateUtils.FORMAT_SHOW_DATE or
                    DateUtils.FORMAT_SHOW_YEAR
    }

    return DateUtils.formatDateTime(
        context,
        epochMillis,
        flags
    )
}

private fun isThisYear(time: Long): Boolean {
    val cal1 = Calendar.getInstance()
    val cal2 = Calendar.getInstance()

    cal1.timeInMillis = time

    return cal1.get(Calendar.YEAR) ==
            cal2.get(Calendar.YEAR)
}

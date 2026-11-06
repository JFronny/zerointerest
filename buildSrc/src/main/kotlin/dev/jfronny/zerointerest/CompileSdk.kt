package dev.jfronny.zerointerest

import com.android.build.api.dsl.CompileSdkSpec
import com.android.build.api.dsl.CompileSdkVersion

private val COMPILE_SDK_REGEX =
    Regex("""^(\d+)(?:\.(\d+))?(?:-ext(\d+))?$""")

fun CompileSdkSpec.parse(version: String): CompileSdkVersion {
    val match = COMPILE_SDK_REGEX.matchEntire(version)
        ?: throw IllegalArgumentException("Invalid compileSdk version: '$version'. Expected format: '37', '37.1' or '37.1-ext18'.")

    val (majorString, minorString, extensionString) = match.destructured

    val major = majorString.toInt()
    val minor = minorString.takeIf { it.isNotEmpty() }?.toInt()
    val extension = extensionString.takeIf { it.isNotEmpty() }?.toInt()

    require(major > 0) { "compileSdk major version must be positive." }
    require(minor == null || minor >= 0) { "compileSdk minor version must not be negative." }
    require(extension == null || extension > 0) { "compileSdk SDK extension must be positive." }

    return release(major) {
        minor?.let { minorApiLevel = it }
        extension?.let { sdkExtension = it }
    }
}

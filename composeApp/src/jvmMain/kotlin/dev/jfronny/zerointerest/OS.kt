package dev.jfronny.zerointerest

import dev.jfronny.zerointerest.ui.appName
import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*
import java.lang.invoke.MethodHandle
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.div


object OS {
    val type by lazy {
        val osName = System.getProperty("os.name").lowercase()
        when {
            osName.contains("win") -> Type.WINDOWS
            osName.contains("mac") || osName.contains("darwin") -> Type.MAC_OS
            osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> Type.LINUX
            else -> throw UnsupportedOperationException("Unsupported OS: $osName")
        }
    }
    val stateDir by lazy {
        when (OS.type) {
            Type.WINDOWS -> Path(getDir("{3EB685DB-65F9-4CF6-A03A-E3EF65729F3D}"))
            Type.MAC_OS -> OS.userDir/"Library/Application Support/${appName}"
            Type.LINUX -> System.getenv("XDG_DATA_HOME")?.let { Path(it)/ appName }
                ?: (OS.userDir/".local/share/zerointerest")
        }
    }
    val userDir by lazy { Path(System.getProperty("user.home")).absolute().normalize() }

    enum class Type(val displayName: String) {
        WINDOWS("Windows"),
        MAC_OS("OSX"),
        LINUX("Linux");
    }
}

private fun getDir(folderId: String): String {
    Arena.ofConfined().use { arena ->
        val guidSegment: MemorySegment? = arena.allocate(GUID_LAYOUT)
        if (CLSIDFromString(createSegmentFromString(folderId, arena), guidSegment) !== 0) {
            throw java.lang.AssertionError("failed converting string $folderId to KnownFolderId")
        }
        val path = arena.allocate(C_POINTER)
        SHGetKnownFolderPath(guidSegment, 0, MemorySegment.NULL, path)
        return createStringFromSegment(path.get(C_POINTER, 0))
    }
}

private fun CLSIDFromString(lpsz: MemorySegment?, pclsid: MemorySegment?): Int {
    val handle = CLSIDFromString.HANDLE
    try {
        return handle.invokeExact(lpsz, pclsid) as Int
    } catch (throwable: Throwable) {
        throw java.lang.AssertionError("failed to invoke `CLSIDFromString`", throwable)
    }
}

private object CLSIDFromString {
    val DESC: FunctionDescriptor = FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER)

    val HANDLE: MethodHandle = Linker.nativeLinker()
        .downcallHandle(findOrThrow("CLSIDFromString"), DESC)
}

private fun SHGetKnownFolderPath(
    rfid: MemorySegment?,
    dwFlags: Int,
    hToken: MemorySegment?,
    ppszPath: MemorySegment?,
): Int {
    val handle = SHGetKnownFolderPath.HANDLE
    try {
        return handle.invokeExact(rfid, dwFlags, hToken, ppszPath) as Int
    } catch (throwable: Throwable) {
        throw AssertionError("failed to invoke `SHGetKnownFolderPath`", throwable)
    }
}

private object SHGetKnownFolderPath {
    val DESC: FunctionDescriptor = FunctionDescriptor.of(C_LONG, C_POINTER, C_LONG, C_POINTER, C_POINTER)

    val HANDLE: MethodHandle = Linker.nativeLinker()
        .downcallHandle(findOrThrow("SHGetKnownFolderPath"), DESC)
}

private val SYMBOL_LOOKUP: SymbolLookup = SymbolLookup.loaderLookup().or(Linker.nativeLinker().defaultLookup())
private fun findOrThrow(symbol: String?): MemorySegment {
    return SYMBOL_LOOKUP.find(symbol)
        .orElseThrow { UnsatisfiedLinkError("unresolved symbol: $symbol") }
}

private val C_CHAR = JAVA_BYTE
private val C_SHORT = JAVA_SHORT
private val C_POINTER = ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, JAVA_BYTE))
private val C_LONG = JAVA_INT
private val GUID_LAYOUT: GroupLayout? = MemoryLayout.structLayout(
    C_LONG.withName("Data1"),
    C_SHORT.withName("Data2"),
    C_SHORT.withName("Data3"),
    MemoryLayout.sequenceLayout(8, C_CHAR).withName("Data4")
).withName("_GUID")
private fun createSegmentFromString(str: String, arena: Arena): MemorySegment {
    // allocate segment (including space for terminating null)
    val segment = arena.allocate(JAVA_CHAR, str.length + 1L)
    // copy characters
    segment.copyFrom(MemorySegment.ofArray(str.toCharArray()))
    return segment
}
private fun createStringFromSegment(segment: MemorySegment): String {
    var len = 0L
    while (segment.get(JAVA_CHAR, len) != '\u0000') {
        len += 2
    }

    return String(segment.asSlice(0, len).toArray(JAVA_CHAR))
}

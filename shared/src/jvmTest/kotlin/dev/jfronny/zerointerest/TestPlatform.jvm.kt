package dev.jfronny.zerointerest

import androidx.room3.Room
import androidx.room3.RoomDatabase
import java.io.File

actual inline fun <reified T : RoomDatabase> Room.inMemoryDatabaseBuilder() = inMemoryDatabaseBuilder<T>()

actual fun readTestResource(path: String): String {
    return File("src/commonTest/resources/$path").readText()
}

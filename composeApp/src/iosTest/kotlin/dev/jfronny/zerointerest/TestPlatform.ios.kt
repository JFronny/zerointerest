package dev.jfronny.zerointerest

import androidx.room3.Room
import androidx.room3.RoomDatabase

actual inline fun <reified T : RoomDatabase> Room.inMemoryDatabaseBuilder() = inMemoryDatabaseBuilder<T>()

actual fun readTestResource(path: String): String {
    TODO("Not yet implemented")
}

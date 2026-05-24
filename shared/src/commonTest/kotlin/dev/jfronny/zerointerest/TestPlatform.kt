package dev.jfronny.zerointerest

import androidx.room3.Room
import androidx.room3.RoomDatabase

expect inline fun <reified T : RoomDatabase> Room.inMemoryDatabaseBuilder(): RoomDatabase.Builder<T>

expect fun readTestResource(path: String): String

package dev.jfronny.zerointerest

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL

actual suspend fun SQLiteConnection.execSQL(sql: String) = execSQL(sql)
actual fun createSQLiteDriver(): SQLiteDriver = BundledSQLiteDriver()
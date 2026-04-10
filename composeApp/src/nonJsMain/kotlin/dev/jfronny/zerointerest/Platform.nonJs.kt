package dev.jfronny.zerointerest

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

actual suspend fun SQLiteConnection.execSQL(sql: String) = execSQL(sql)
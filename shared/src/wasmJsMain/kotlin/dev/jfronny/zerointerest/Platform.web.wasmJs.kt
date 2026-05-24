package dev.jfronny.zerointerest

import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

actual fun WebWorkerSQLiteDriver(worker: Worker): WebWorkerSQLiteDriver = WebWorkerSQLiteDriver(worker)

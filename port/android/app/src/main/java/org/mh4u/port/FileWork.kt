package org.mh4u.port

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** UI operations share a gate so Play cannot start while saved files are being replaced. */
object FileWork {
    var busy by mutableStateOf(false)
        private set
    var recoveryError by mutableStateOf("")

    suspend fun <T> run(idleGame: Boolean = false, action: () -> T): T {
        check(!busy) { "Please wait for the current file operation." }
        if (idleGame) check(Native.saveDataIdle()) { "Save inside MH4U, then exit the game before managing saved files." }
        busy = true
        return try { withContext(Dispatchers.IO) { action() } } finally { busy = false }
    }
}

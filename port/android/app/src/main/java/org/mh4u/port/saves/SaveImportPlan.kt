package org.mh4u.port.saves

import java.io.File

/** Validation precedes closing the game; replacement waits for its final filesystem flush. */
class SaveImportPlan(private val repository: SaveArchive) {
    suspend fun restore(archive: File, closeAndWait: suspend () -> Unit, coreClosed: () -> Boolean) {
        repository.validate(archive)
        closeAndWait()
        check(coreClosed()) { "The game is still closing. Saved progress has not been replaced. Try again from Home." }
        repository.restore(archive)
    }
}

/** Activity-owned restart handling, kept separate from the saved-file format. */
interface SaveGameSession {
    suspend fun closeForImport()
    fun restartAfterImport()
    fun importFailed(message: String)
}

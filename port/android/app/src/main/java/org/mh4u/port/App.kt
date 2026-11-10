package org.mh4u.port

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // The restart helper never opens saves or initializes another emulator core.
        if (Application.getProcessName() == packageName + SaveRestartActivity.PROCESS_SUFFIX) return
        Storage.init(this)
        Prefs.ensureRowellDefaults()
        runCatching { org.mh4u.port.saves.SaveArchive(Storage.root).recover() }
            .onFailure { FileWork.recoveryError = it.message ?: "Save recovery could not finish. Open Settings → Saves." }
        org.mh4u.port.diagnostics.Diagnostics.retainPreviousLog(this)
        Native.init(Storage.privateDir.absolutePath)
        MH4UInput.init(Storage.root)
    }
}

package org.mh4u.port

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Storage.init(this)
        Prefs.ensureRowellDefaults()
        runCatching { org.mh4u.port.saves.SaveArchive(Storage.root).recover() }
            .onFailure { FileWork.recoveryError = it.message ?: "Save recovery could not finish. Open Settings → Saves." }
        org.mh4u.port.diagnostics.Diagnostics.retainPreviousLog(this)
        Native.init(Storage.privateDir.absolutePath)
        MH4UInput.init(Storage.root)
    }
}

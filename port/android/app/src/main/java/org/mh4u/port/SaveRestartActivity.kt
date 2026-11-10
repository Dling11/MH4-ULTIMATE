package org.mh4u.port

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.Gravity
import android.widget.TextView

/** A small isolated process restarts this app only after a confirmed import has finished. */
class SaveRestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Save imported. Restarting game…"
            textSize = 18f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(11, 20, 27))
        })
        val oldPid = intent.getIntExtra(EXTRA_OLD_PID, -1)
        val owner = getSystemService(ActivityManager::class.java)?.runningAppProcesses?.firstOrNull {
            it.pid == oldPid && it.uid == Process.myUid() && it.processName == packageName
        }
        if (owner != null && oldPid != Process.myPid()) Process.killProcess(oldPid)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(MainActivity.EXTRA_SAVE_IMPORTED, true))
            finish()
            Process.killProcess(Process.myPid())
        }, 180)
    }

    companion object {
        const val PROCESS_SUFFIX = ":save_restart"
        private const val EXTRA_OLD_PID = "old_process"
        fun restart(activity: Activity) {
            activity.startActivity(Intent(activity, SaveRestartActivity::class.java)
                .putExtra(EXTRA_OLD_PID, Process.myPid()))
        }
    }
}

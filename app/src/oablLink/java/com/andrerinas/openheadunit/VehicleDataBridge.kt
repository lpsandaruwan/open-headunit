package com.andrerinas.openheadunit

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.andrerinas.openheadunit.utils.AppLog
import com.softiva.oabllink.app.LinkService

/** The private Dongfeng build runs Link alongside projection, in a separate process. */
object VehicleDataBridge {
    fun isTelemetryProcess(context: Context): Boolean {
        val name = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName() else {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            manager.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName
        }
        return name == "${context.packageName}:oabl_link"
    }

    fun start(context: Context) {
        try {
            context.startForegroundService(Intent(context, LinkService::class.java))
        } catch (e: RuntimeException) {
            // Projection remains usable if Android refuses a service start; the Link setup screen
            // also provides a manual start, and the boot/update receiver retries independently.
            AppLog.w("OABL Link: could not start vehicle data service: $e")
        }
    }
}

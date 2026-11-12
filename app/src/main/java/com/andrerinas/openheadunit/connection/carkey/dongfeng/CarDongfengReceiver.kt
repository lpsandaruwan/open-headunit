package com.andrerinas.openheadunit.connection.carkey.dongfeng

import android.content.Context
import android.content.pm.PackageManager
import com.andrerinas.openheadunit.connection.carkey.CarKeyReceiver
import com.andrerinas.openheadunit.input.DongfengWheelKey
import com.andrerinas.openheadunit.utils.AppLog
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Steering-wheel media keys on a Dongfeng head unit, read from the input service's log.
 *
 * See [DongfengWheelKey] for why the log is the only place an ordinary app can see these keys.
 * Needs `READ_LOGS`, which `pm grant` can give once:
 * `adb shell pm grant <package> android.permission.READ_LOGS`.
 *
 * Session-scoped: the log is only read while a projection is running, so an idle unit costs nothing
 * and the stock player keeps the keys for itself otherwise. The stock media app still receives every
 * key as well; with CarPlay not connected it does nothing with them.
 */
class CarDongfengReceiver : CarKeyReceiver {

    override val isSupported = true
    override val isSUNeeded = false
    override val isSessionScoped = true

    @Volatile private var running = false
    /** Bumped on every register and unregister, so a reader from an earlier session stops by itself. */
    @Volatile private var generation = 0
    private var process: Process? = null
    private var thread: Thread? = null

    override fun register(context: Context) {
        val app = context.applicationContext
        val installed = runCatching { app.packageManager.getPackageInfo(DongfengWheelKey.INPUT_PACKAGE, 0) }.isSuccess
        if (!installed) return // not a Dongfeng unit; stay silent
        if (running) return
        running = true
        val mine = ++generation
        thread = Thread({ readLoop(app, mine) }, "dongfeng-wheel-keys").also {
            it.isDaemon = true
            it.start()
        }
        AppLog.i("CarKeyReceiver: Dongfeng wheel keys: reader started (session $mine)")
    }

    override fun unregister() {
        running = false
        generation++
        process?.destroy()
        process = null
        thread = null
    }

    private fun readLoop(context: Context, mine: Int) {
        var pressed = HashSet<Int>()
        var warnedPermission = false
        var quickFailures = 0
        while (running && generation == mine) {
            // The grant is checked on every round, not once: a unit that lost it (or got it while the session
            // was already running) is picked up without restarting anything.
            if (context.checkSelfPermission(android.Manifest.permission.READ_LOGS) != PackageManager.PERMISSION_GRANTED) {
                if (!warnedPermission) {
                    warnedPermission = true
                    AppLog.w(
                        "CarKeyReceiver: Dongfeng wheel keys need READ_LOGS and it is not granted. Grant once with: " +
                            "adb shell pm grant ${context.packageName} android.permission.READ_LOGS",
                    )
                }
                runCatching { Thread.sleep(5000) }
                continue
            }
            if (warnedPermission) {
                warnedPermission = false
                AppLog.i("CarKeyReceiver: Dongfeng wheel keys: READ_LOGS is granted now")
            }
            val startedAt = android.os.SystemClock.elapsedRealtime()
            var lines = 0
            var firstLine: String? = null
            try {
                // -T 1: start at "now", so keys pressed before the session are not replayed.
                val started = ProcessBuilder(
                    "logcat", "-v", "brief", "-T", "1", "${DongfengWheelKey.LOG_TAG}:I", "*:S",
                ).redirectErrorStream(true).start()
                process = started
                BufferedReader(InputStreamReader(started.inputStream)).use { reader ->
                    while (running && generation == mine) {
                        val line = reader.readLine() ?: break
                        lines++
                        if (firstLine == null) firstLine = line
                        val event = DongfengWheelKey.parse(line) ?: continue
                        if (event.isDown) {
                            // A repeated down without a release would stick the key in the phone.
                            if (!pressed.add(event.keyCode)) continue
                            handleKey(context, event.keyCode, true)
                        } else if (pressed.remove(event.keyCode)) {
                            handleKey(context, event.keyCode, false)
                        }
                    }
                }
            } catch (e: Exception) {
                if (running && generation == mine) AppLog.e("CarKeyReceiver: Dongfeng key reader failed", e)
            } finally {
                process?.destroy()
                // Release anything still held, then try again after a pause.
                for (key in pressed) runCatching { handleKey(context, key, false) }
                pressed = HashSet()
            }
            if (!(running && generation == mine)) break
            // A reader that ends at once is almost always a refusal ("Permission Denial", no logd): say what it said.
            val lived = android.os.SystemClock.elapsedRealtime() - startedAt
            if (lived < 2000) {
                quickFailures++
                if (quickFailures <= 3 || quickFailures % 30 == 0) {
                    AppLog.w("CarKeyReceiver: Dongfeng key reader ended after ${lived} ms ($lines lines, first: ${firstLine ?: "none"})")
                }
            } else {
                quickFailures = 0
            }
            runCatching { Thread.sleep(if (quickFailures > 3) 10_000L else 2000L) }
        }
    }
}

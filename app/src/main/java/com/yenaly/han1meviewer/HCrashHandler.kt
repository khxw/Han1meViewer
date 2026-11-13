package com.yenaly.han1meviewer

import com.yenaly.han1meviewer.logic.log.HLumberjack
import com.yenaly.yenaly_libs.ActivityManager

object HCrashHandler : Thread.UncaughtExceptionHandler {

    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        e.printStackTrace()
        runCatching {
            HLumberjack.e(
                "CRASH",
                "thread=${t.name}, type=${e.javaClass.name}, message=${e.message}",
            )
        }
        previousHandler
            ?.takeIf { it !== this }
            ?.uncaughtException(t, e)
            ?: ActivityManager.restart(killProcess = true)
    }
}

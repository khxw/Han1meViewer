package com.yenaly.han1meviewer

import android.content.Context
import com.yenaly.yenaly_libs.base.YenalyInitializer

class HInitializer : YenalyInitializer() {
    override fun create(context: Context) {
        super.create(context)
        // 设置默认未捕获异常处理器，用于本地崩溃日志记录
        Thread.setDefaultUncaughtExceptionHandler(HCrashHandler)
    }
}
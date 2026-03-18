package com.tencent.kuiklybase.adapter

import android.util.Log
import com.tencent.kuikly.core.render.android.adapter.IKRUncaughtExceptionHandlerAdapter

object KRUncaughtExceptionHandlerAdapter : IKRUncaughtExceptionHandlerAdapter {

    private const val TAG = "KRExceptionHandler"

    override fun uncaughtException(throwable: Throwable) {
        // 打印异常类型、消息及完整堆栈
        Log.e(TAG, "uncaughtException: [${throwable.javaClass.simpleName}] ${throwable.message}", throwable)

        // 打印完整堆栈信息
        val stackTrace = throwable.stackTraceToString()
        Log.e(TAG, "StackTrace:\n$stackTrace")

        // 打印 cause 链
        var cause = throwable.cause
        var depth = 1
        while (cause != null) {
            Log.e(TAG, "Caused by[$depth]: [${cause.javaClass.simpleName}] ${cause.message}")
            cause = cause.cause
            depth++
        }
    }
}
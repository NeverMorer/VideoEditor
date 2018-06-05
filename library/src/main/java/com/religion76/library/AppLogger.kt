package com.religion76.library

import android.content.Intent
import android.util.Log
import java.util.*

/**
 * Wrapper API for sending log output.
 */
object AppLogger {
    val TAG = "AppLogger"
    val TIMER_TAG = "TraceTime"


    private fun showLog() = BuildConfig.DEBUG
    /**
     * Send a VERBOSE log message.

     * @param msg The message you would like logged.
     */
    fun v(msg: String) {
        if (showLog())
            Log.v(TAG, buildMessage(msg))
    }

    /**
     * Send a VERBOSE log message and log the exception.

     * @param msg The message you would like logged.
     * *
     * @param thr An exception to log
     */
    fun v(msg: String, thr: Throwable) {
        if (showLog())
            Log.v(TAG, buildMessage(msg), thr)
    }

    /**
     * Send a DEBUG log message.

     * @param msg
     */
    fun d(msg: String) {
        if (showLog())
            Log.d(TAG, buildMessage(msg))
    }

    fun d(tag: String, msg: String) {
        if (showLog())
            Log.d(tag, msg)
        //            android.util.Log.d(tag, buildMessage(msg));
    }

    /**
     * Send a DEBUG log message and log the exception.

     * @param msg The message you would like logged.
     * *
     * @param thr An exception to log
     */
    fun d(msg: String, thr: Throwable) {
        if (showLog())
            Log.d(TAG, buildMessage(msg), thr)
    }

    /**
     * Send an INFO log message.

     * @param msg The message you would like logged.
     */
    fun i(msg: String) {
        if (showLog())
            Log.i(TAG, buildMessage(msg))
    }

    fun i(tag: String, msg: String) {
        if (showLog())
            Log.i(tag, msg)
    }

    /**
     * Send a INFO log message and log the exception.

     * @param msg The message you would like logged.
     * *
     * @param thr An exception to log
     */
    fun i(msg: String, thr: Throwable) {
        if (showLog())
            Log.i(TAG, buildMessage(msg), thr)
    }

    /**
     * Send an ERROR log message.

     * @param msg The message you would like logged.
     */
    fun e(msg: String) {
        if (showLog())
            Log.e(TAG, buildMessage(msg))
    }

    fun e(tag: String, msg: String) {
        if (showLog())
            Log.e(tag, msg)
        //            android.util.Log.e(tag, buildMessage(msg));
    }

    /**
     * Send a WARN log message

     * @param msg The message you would like logged.
     */
    fun w(msg: String) {
        if (showLog())
            Log.w(TAG, buildMessage(msg))
    }

    fun w(tag: String, msg: String) {
        if (showLog())
            Log.w(tag, msg)
    }

    /**
     * Send a WARN log message and log the exception.

     * @param msg The message you would like logged.
     * *
     * @param thr An exception to log
     */
    fun w(msg: String, thr: Throwable) {
        if (showLog())
            Log.w(TAG, buildMessage(msg), thr)
    }

    /**
     * Send an empty WARN log message and log the exception.

     * @param thr An exception to log
     */
    fun w(thr: Throwable) {
        if (showLog())
            Log.w(TAG, buildMessage(""), thr)
    }

    /**
     * Send an ERROR log message and log the exception.

     * @param msg The message you would like logged.
     * *
     * @param thr An exception to log
     */
    fun e(msg: String, thr: Throwable) {
        if (showLog())
            Log.e(TAG, buildMessage(msg), thr)
    }

    fun e(tag: String, msg: String, thr: Throwable) {
        if (showLog())
            Log.e(tag, buildMessage(msg), thr)
    }

    fun printStackTrace(e: Throwable?) {
        if (showLog())
            e?.printStackTrace()
    }

    private val traceTimeStack = Stack<Long>()

    fun resetTraceTime() {
        traceTimeStack.clear()
    }

    fun startTraceTime(msg: String) {
        traceTimeStack.push(System.currentTimeMillis())
        if (showLog()) {
            Log.d(TIMER_TAG, msg + " time = " + System.currentTimeMillis())
        }
    }

    fun stopTraceTime(msg: String) {
        if (!traceTimeStack.isEmpty()) {
            val time = traceTimeStack.pop()
            val diff = System.currentTimeMillis() - time
            if (showLog()) {
                Log.d(TIMER_TAG, "[" + diff + "]" + msg + " time = " + System.currentTimeMillis())
            }
        }
    }

    /**
     * Building Message

     * @param msg The message you would like logged.
     * *
     * @return Message String
     */
    fun buildMessage(msg: String): String {
        val caller = java.lang.Throwable().stackTrace[2]

        return StringBuilder().append(caller.className).append(".").append(caller.methodName).append("(): \n").append(msg).toString()
    }

    fun logIntent(tag: String, intent: Intent) {
        if (showLog()) {
            Log.i(tag, "-------------------- Intent --------------------")
            Log.i(tag, "action: " + intent.action)
            Log.i(tag, "data: " + intent.dataString)
            Log.i(tag, "flags: " + intent.flags)
            Log.i(tag, "class: " + intent.component.toString())
            Log.i(tag, "type: " + intent.type)
            Log.i(tag, "package: " + intent.`package`)
            Log.i(tag, "scheme: " + intent.scheme)
            Log.i(tag, "----- data ----")
            val data = intent.extras
            if (data != null) {
                for (key in data.keySet()) {
                    val value = data.get(key)
                    var valueString = ""
                    if (value != null) {
                        valueString = value.toString()
                    }
                    Log.i(tag, key + " : " + valueString)
                }
            }
            Log.i(tag, "-------------------- Intent end ----------------")
        }
    }

    fun throwOrLog(message: String) {
        if (BuildConfig.DEBUG) {
            throw IllegalStateException(message)
        } else {
            e(message)
        }
    }
}
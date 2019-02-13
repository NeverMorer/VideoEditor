package com.religion76.library.v2

/**
 * Created by SunChao on 2019/1/29.
 */
interface MediaExecuteCallback {
    fun onComplete()
    fun onError(t: Throwable)
    fun onMediaTrackReady()
}
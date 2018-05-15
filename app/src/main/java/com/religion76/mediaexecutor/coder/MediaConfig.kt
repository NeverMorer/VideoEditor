package com.religion76.mediaexecutor.coder

import android.util.Log
import java.io.File

/**
 * Created by SunChao
 * on 2018/3/3.
 */
class MediaConfig {

    val BPP = 0.3f

    val frameRate = 30
    val iFrameInterval = 1
    val numFrames = 300
    val mineType = "video/avc"
    var bitRate = 300000
    var width = 720
    var height = 1280
    var duration = 0L


    var path: String? = null


    fun getCalBitrate(): Long = (width * height * frameRate * BPP).toLong()

    fun getCompressBitrate(): Long {
        return if (path != null){
            val length = File(path).length()
            val originalBitrate = length * 8 / (duration / 1000000)
            Log.d("MediaConfig", "original bitrate: $originalBitrate")
            return (originalBitrate * 0.5).toLong()
        }else{
            getCalBitrate()
        }
    }

}
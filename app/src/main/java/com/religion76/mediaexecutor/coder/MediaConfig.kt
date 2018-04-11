package com.religion76.mediaexecutor.coder

/**
 * Created by SunChao
 * on 2018/3/3.
 */
class MediaConfig {

    val BPP = 0.3f

    val frameRate = 30
    val iFrameInterval = 10
    val numFrames = 300
    val mineType = "video/avc"
    var bitRate = 300000
    var width = 720
    var height = 1280


    fun getCalBitrate(): Int = (width * height * frameRate * BPP).toInt()
}
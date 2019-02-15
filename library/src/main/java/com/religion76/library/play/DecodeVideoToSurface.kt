package com.religion76.library.play

import android.media.*
import android.os.Handler
import android.util.Log
import android.view.Surface
import com.religion76.library.AppLogger
import com.religion76.library.MediaInfo
import com.religion76.library.codec.VideoDecoderSync2
import com.religion76.library.gles.InputSurface
import com.religion76.library.gles.OutputSurface
import com.religion76.library.v2.MediaExecuteCallback

/**
 * Created by SunChao
 * on 2018/5/24.
 */
class DecodeVideoToSurface(private val path: String, private val mediaExtractor: MediaExtractor) {

    companion object {
        const val TAG = "SeparateVideoCoder"
    }

    private var videoDecoder: VideoDecoderSync2? = null

    private lateinit var mediaInfo: MediaInfo

    private var scaleWidth: Int? = null
    private var scaleHeight: Int? = null

    private var callback: MediaExecuteCallback? = null
    private var callbackHandler: Handler? = null

    private var lastFrameTime: Long = -1
    private var lastFrameTimeTemp: Long = -1

    private var inputSurface: InputSurface? = null
    private var outputSurface: OutputSurface? = null


    fun prepare(trackFormat: MediaFormat, surface: Surface): Boolean {

        mediaInfo = MediaInfo.getMediaInfo(path)

        AppLogger.d(TAG, "original width:${mediaInfo.getWidth()}  height:${mediaInfo.getHeight()}")
        AppLogger.d(TAG, "original bitrate:${mediaInfo.getBitrate()}")

        if (scaleWidth != null && scaleHeight != null) {
            mediaInfo.setScale(scaleWidth!!, scaleHeight!!)
        }

        inputSurface = InputSurface(surface)
        inputSurface!!.makeCurrent()

        isPrepared = initDecoder(trackFormat)

        return isPrepared
    }

    fun setCallback(callback: MediaExecuteCallback, handler: Handler? = null) {
        this.callback = callback
        this.callbackHandler = handler
    }

    private fun initDecoder(mediaFormat: MediaFormat): Boolean {
        videoDecoder = VideoDecoderSync2()

        outputSurface = OutputSurface()

        if (!videoDecoder!!.prepare(mediaFormat, mediaExtractor, outputSurface!!.surface)) {
            return false
        }

        videoDecoder!!.onOutputBufferGenerate = { bufferInfo ->
            lastFrameTimeTemp = bufferInfo.presentationTimeUs

            if (inputSurface != null && outputSurface != null) {

                outputSurface!!.awaitNewImage()
                outputSurface!!.drawImage()

                inputSurface!!.setPresentationTime(bufferInfo.presentationTimeUs * 1000)
                inputSurface!!.swapBuffers()
            }
        }

        videoDecoder!!.onDecodeFinish = {
            lastFrameTime = lastFrameTimeTemp
        }

        return true
    }


    private var isPrepared = false

    fun start() {

        if (!isPrepared) {
            Log.d(TAG, "video coder not prepared")
            return
        }

        videoDecoder!!.start()

        while (true) {
            videoDecoder!!.enqueueData()

            if (!videoDecoder!!.pull()) {
                break
            }
        }
    }

    private fun releaseDecoder() {
        if (videoDecoder != null) {
//            decoder!!.stop()
            videoDecoder!!.release()
            videoDecoder = null
        }
    }

    fun release() {
        AppLogger.d(TAG, "release")
        releaseDecoder()

        inputSurface?.release()
        inputSurface = null

        outputSurface?.release()
        outputSurface = null
    }
}
package com.religion76.mediaexecutor.extractor

import android.graphics.Bitmap
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.support.annotation.RequiresApi
import android.util.Log
import com.religion76.mediaexecutor.coder.VideoDecoder

/**
 * Created by SunChao
 * on 2018/5/15.
 */

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
class FrameExtractor(private val videoPath: String, private val width: Int, private val height: Int) : Runnable {

    companion object {
        val TAG = "FrameCatcher"
    }

    private lateinit var videoDecoder: VideoDecoder

    private lateinit var outputSurface: CodecOutputSurface

    var duration: Long = 0

    var isPrepared = false

    var onExtractProgressChange: ((frame: Bitmap, timeUs: Long) -> Unit)? = null

    var onExtractFinished: (() -> Unit)? = null

    var onCatcherInit: (() -> Unit)? = null

    override fun run() {
        if (!videoPath.endsWith(".mp4")) {
            throw IllegalStateException("video file is not support")
        }

        Looper.prepare()
        init()
        handler = Handler(Looper.myLooper())
        Looper.loop()
    }

    private lateinit var handler: Handler

    private fun init() {

        Log.d(TAG, "init " + Thread.currentThread().id)

        outputSurface = CodecOutputSurface(width, height)

        videoDecoder = VideoDecoder()

        videoDecoder.onSampleFormatConfirmed = {
            duration = it.getLong(MediaFormat.KEY_DURATION)
            isPrepared = true
            onCatcherInit?.invoke()
        }

        videoDecoder.onOutputBufferGenerate = { outputBuffer, bufferInfo ->
            outputSurface.awaitNewImage()
            outputSurface.drawImage(true)
            onExtractProgressChange?.invoke(outputSurface.frame, bufferInfo.presentationTimeUs)
        }

        videoDecoder.onDecodeFinish = {
            onExtractFinished?.invoke()
        }

        videoDecoder.decode(videoPath, outputSurface.surface, null, false)

    }

    fun start() {
        val thread = Thread(this)
        thread.start()
        run()
    }


    fun requestFrame(ms: Long) {
        handler.post {
            videoDecoder.seekTo(ms * 1000)
        }
    }

    fun release() {
        handler.post {
            videoDecoder.release()
            outputSurface.release()
        }
    }
}
package com.religion76.library.extractor

import android.graphics.Bitmap
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.support.annotation.RequiresApi
import android.util.Log
import com.religion76.library.gles.CodecOutputSurface

/**
 * Created by SunChao
 * on 2018/5/15.
 */

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class FrameExtractor(private val videoPath: String, private val width: Int, private val height: Int) : Runnable {

    companion object {
        val TAG = "FrameExtractor"
    }

    private lateinit var videoDecoder: ExtractFrameDecoder

    private lateinit var outputSurface: CodecOutputSurface

    var onExtractProgressChange: ((frame: Bitmap, timeUs: Long) -> Unit)? = null

    var onExtractFinished: (() -> Unit)? = null

    var onCatcherInit: (() -> Unit)? = null

    override fun run() {
        init()
    }

    private var isReleased: Boolean = false

    private fun init() {

        Log.d(TAG, "init " + Thread.currentThread().id)

        outputSurface = CodecOutputSurface(width, height)

        videoDecoder = ExtractFrameDecoder()

        videoDecoder.onSampleFormatConfirmed = {
            Log.d(TAG, "onSampleFormatConfirmed " + Thread.currentThread().id)
            onCatcherInit?.invoke()
        }

        videoDecoder.onOutputBufferGenerate = { outputBuffer, bufferInfo ->
            Log.d(TAG, "onOutputBufferGenerate " + Thread.currentThread().id)
            //must call on the same thread which CodecOutputSurface created
            outputSurface.awaitNewImage()
            outputSurface.drawImage(true)
            onExtractProgressChange?.invoke(outputSurface.frame, bufferInfo.presentationTimeUs)
        }

        videoDecoder.onDecodeFinish = {
            release()
            onExtractFinished?.invoke()
        }

        videoDecoder.decode(videoPath, outputSurface.surface, null, false)
    }

    fun start() {
        Thread(this).start()
    }

    fun requestFrame(ms: Long) {
        Log.d(TAG, "requestFrame")
        videoDecoder.seekTo(ms)
    }

    fun release() {
        if (!isReleased) {
            videoDecoder.queueEOS()
            videoDecoder.release()
            outputSurface.release()
            isReleased = true
        }
    }
}
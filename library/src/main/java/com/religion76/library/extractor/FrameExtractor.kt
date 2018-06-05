package com.religion76.library.extractor

import android.graphics.Bitmap
import android.os.Build
import android.support.annotation.RequiresApi
import android.util.Log
import com.religion76.library.gles.CodecOutputSurface
import com.religion76.library.sync.MediaInfo

/**
 * Created by SunChao
 * on 2018/5/15.
 */

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class FrameExtractor(private val width: Int, private val height: Int, private val mediaInfo: MediaInfo) : Runnable {

    companion object {
        val TAG = "FrameExtractor"
    }

    private lateinit var videoDecoder: ExtractFrameDecoder

    private lateinit var outputSurface: CodecOutputSurface

    var onExtractProgressChange: ((frame: Bitmap, timeUs: Long) -> Unit)? = null

    var onExtractFinished: (() -> Unit)? = null

    var onCatcherInit: (() -> Unit)? = null

    var isRotate = false

    override fun run() {
        init()
    }

    private var isReleased: Boolean = false

    private fun init() {

        Log.d(TAG, "init " + Thread.currentThread().id)

        outputSurface = CodecOutputSurface(width, height)

        isRotate = mediaInfo.is3GP()

        videoDecoder = ExtractFrameDecoder()

        videoDecoder.onSampleFormatConfirmed = {
            Log.d(TAG, "onSampleFormatConfirmed " + Thread.currentThread().id)
            onCatcherInit?.invoke()
        }

        videoDecoder.onOutputBufferGenerate = { outputBuffer, bufferInfo ->
            Log.d(TAG, "onOutputBufferGenerate " + Thread.currentThread().id)
            //must call on the same thread which CodecOutputSurface created
            outputSurface.awaitNewImage()
            if (isRotate) {
                outputSurface.drawImage(true, if (isRotate) mediaInfo.getRotation() else 0)
            } else {
                outputSurface.drawImage(true)
            }

            onExtractProgressChange?.invoke(outputSurface.frame, bufferInfo.presentationTimeUs)
        }

        videoDecoder.onDecodeFinish = {
            release()
            onExtractFinished?.invoke()
        }

        videoDecoder.decode(mediaInfo.getPath(), outputSurface.surface, null, false)
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
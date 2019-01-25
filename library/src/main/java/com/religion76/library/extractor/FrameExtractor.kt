package com.religion76.library.extractor

import android.graphics.Bitmap
import android.os.Build
import android.support.annotation.RequiresApi
import com.religion76.library.AppLogger
import com.religion76.library.gles.CodecOutputSurface
import com.religion76.library.MediaInfo

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

    var isPrepared = false

    var isRotate = false

    override fun run() {
        init()
    }

    private var isReleased: Boolean = false

    private fun init() {

        AppLogger.d(TAG, "init " + Thread.currentThread().id)

        outputSurface = CodecOutputSurface(width, height)

        isRotate = mediaInfo.is3GP()

        videoDecoder = ExtractFrameDecoder()

        videoDecoder.onSampleFormatConfirmed = {
            AppLogger.d(TAG, "onSampleFormatConfirmed " + Thread.currentThread().id)
            if (!isReleased) {
                onCatcherInit?.invoke()
            }
        }

        videoDecoder.onOutputBufferGenerate = { outputBuffer, bufferInfo ->
            AppLogger.d(TAG, "onOutputBufferGenerate " + Thread.currentThread().id)
            //must call on the same thread which CodecOutputSurface created
            if (!isReleased) {
                outputSurface.awaitNewImage()
                if (isRotate) {
                    outputSurface.drawImage(true, if (isRotate) mediaInfo.getRotation() else 0)
                } else {
                    outputSurface.drawImage(true)
                }

                onExtractProgressChange?.invoke(outputSurface.frame, bufferInfo.presentationTimeUs)
            }
        }

        videoDecoder.onDecodeFinish = {
            if (!isReleased) {
                release()
                onExtractFinished?.invoke()
            }
        }

        if (videoDecoder.prepare(mediaInfo.getPath(), outputSurface.surface, null, false)) {
            isPrepared = true
            videoDecoder.startDecode()
        }
    }

    fun start() {
        Thread(this).start()
    }

    fun requestFrame(ms: Long) {
        AppLogger.d(TAG, "requestFrame")
        if (isPrepared){
            videoDecoder.seekTo(ms)
        }
    }

    fun release() {
        if (!isReleased) {
            videoDecoder.queueEOS()
            outputSurface.release()
            isReleased = true
        }
    }
}
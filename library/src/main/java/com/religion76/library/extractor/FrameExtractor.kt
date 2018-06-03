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

    var duration: Long = 0

    var isPrepared = false

    var onExtractProgressChange: ((frame: Bitmap, timeUs: Long) -> Unit)? = null

    var onExtractFinished: (() -> Unit)? = null

    var onCatcherInit: (() -> Unit)? = null

    override fun run() {
        init()
    }

    private lateinit var mediaFormat: MediaFormat

    private var isReleased: Boolean = false

    private fun init() {

        Log.d(TAG, "init " + Thread.currentThread().id)

        val retrieverSrc = MediaMetadataRetriever()
        retrieverSrc.setDataSource(videoPath)

        var height = retrieverSrc.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
        var width = retrieverSrc.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()

        val degreesString = retrieverSrc.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        if (degreesString != null) {
            val d = Integer.parseInt(degreesString)
            if (d > 0) {
                Log.d(TAG, "width: $width  height:$height  rotate:$d")

                height += width
                width = height - width
                height -= width
            }
        }

        outputSurface = CodecOutputSurface(width, height)

        videoDecoder = ExtractFrameDecoder()

        videoDecoder.onSampleFormatConfirmed = {
            Log.d(TAG, "onSampleFormatConfirmed " + Thread.currentThread().id)
            mediaFormat = it
            duration = it.getLong(MediaFormat.KEY_DURATION)
            isPrepared = true
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
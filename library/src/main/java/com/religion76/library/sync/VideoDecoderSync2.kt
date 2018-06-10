package com.religion76.library.sync

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import com.religion76.library.AppLogger
import java.nio.ByteBuffer

/**
 * Created by SunChao
 * on 2018/3/3.
 */
class VideoDecoderSync2 {

    companion object {
        const val TAG = "MediaCoder_Decoder"
        const val DEFAULT_QUEUE_TIMEOUT = 10000L
    }

    private lateinit var extractor: MediaExtractor

    private lateinit var decoder: MediaCodec

    private var isRender = false

    var isDecodeFinish = false

    var isEOSNeed = false

    var onDecodeFinish: (() -> Unit)? = null

    private fun getInputBuffer(index: Int): ByteBuffer? {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            inputBuffers?.get(index)
        } else {
            decoder.getInputBuffer(index)
        }
    }

    private fun getOutBuffer(index: Int): ByteBuffer? {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            outputBuffers?.get(index)
        } else {
            decoder.getOutputBuffer(index)
        }
    }

    private var outputBuffers: Array<ByteBuffer>? = null
    private var inputBuffers: Array<ByteBuffer>? = null

    var onOutputBufferGenerate: ((bufferInfo: MediaCodec.BufferInfo) -> Unit)? = null


    fun queueEOS() {
        if (!isDecodeFinish) {
            AppLogger.d(TAG, "------------- decoder queueEOS ------------")
            isEOSNeed = true
        }
    }

    fun prepare(mediaFormat: MediaFormat, mediaExtractor: MediaExtractor, surface: Surface): Boolean {
        AppLogger.d(TAG, "on decoder configured $mediaFormat")
        try {
            decoder = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME))
            decoder.configure(mediaFormat, surface, null, 0)
            decoder.start()
        } catch (e: Exception) {
            AppLogger.d(TAG, "decoder configure failed")
            return false
        }

        isRender = true
        extractor = mediaExtractor

        return true
    }

    fun pull(): Boolean {

        if (outputBuffers == null) {
            outputBuffers = decoder.outputBuffers
        }

        return if (isDecodeFinish) {
            false
        } else {
            val bufferInfo = MediaCodec.BufferInfo()
            val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, DEFAULT_QUEUE_TIMEOUT)

            //double check isDecodeFinish because last code is block
            when (outputBufferIndex) {
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    AppLogger.d(TAG, "decoder output INFO_OUTPUT_BUFFERS_CHANGED")
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        outputBuffers = decoder.outputBuffers
                    }
                }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    AppLogger.d(TAG, "decoder output INFO_OUTPUT_FORMAT_CHANGED")
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    AppLogger.d(TAG, "decoder output INFO_TRY_AGAIN_LATER")
                }
                else -> {
                    if (bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                        AppLogger.d(TAG, "decoder output generate sample data")
                        decoder.releaseOutputBuffer(outputBufferIndex, isRender)
                        onOutputBufferGenerate?.invoke(bufferInfo)
                    } else {
                        AppLogger.d(TAG, "=== decoder end of stream ===")
                        isDecodeFinish = true
                        onDecodeFinish?.invoke()
                    }
                }
            }

            bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0
        }
    }


    fun enqueueData() {
        if (isDecodeFinish) {
            return
        }

        val inputBufferIndex = decoder.dequeueInputBuffer(DEFAULT_QUEUE_TIMEOUT)
        //double check isDecodeFinish because last code is block
        if (inputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            AppLogger.d(TAG, "decoder input INFO_OUTPUT_BUFFERS_CHANGED")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                inputBuffers = decoder.inputBuffers
            }
        } else if (inputBufferIndex >= 0) {
            if (isEOSNeed) {
                AppLogger.d(TAG, "------------- queue EOS ------------")
                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                isEOSNeed = false
            } else {
                val inputBuffer = getInputBuffer(inputBufferIndex)
                inputBuffer?.let {
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        AppLogger.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM")
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        //here to filter sample data by limit duration
                        AppLogger.d(TAG, "InputBuffer queueInputBuffer")
                        decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
        }
    }

    fun release() {
        isDecodeFinish = true
        decoder.release()
    }

}
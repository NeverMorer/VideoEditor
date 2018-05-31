package com.religion76.library.sync

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import java.nio.ByteBuffer

/**
 * Created by SunChao
 * on 2018/3/3.
 */
class VideoDecoderSync {

    companion object {
        const val TAG = "MediaCoder_Decoder"
        const val DEFAULT_QUEUE_TIMEOUT = 10000L
    }

    private lateinit var extractor: MediaExtractor

    private lateinit var decoder: MediaCodec

    private var isRender = false

    var isDecodeFinish = false

    var isEOSNeed = false

    @Volatile
    var onDecodeFinish: (() -> Unit)? = null

    private fun getInputBuffer(index: Int): ByteBuffer {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            decoder.inputBuffers[index]
        } else {
            decoder.getInputBuffer(index)
        }
    }

    private fun getOutBuffer(index: Int): ByteBuffer {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            outputBuffers[index]
        } else {
            decoder.getOutputBuffer(index)
        }
    }

    private lateinit var outputBuffers: Array<ByteBuffer>

    var onOutputBufferGenerate: ((outputBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) -> Unit)? = null

    private fun configure(mediaFormat: MediaFormat) {
        Log.d(TAG, "on decoder configured $mediaFormat")
        decoder = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME))
        decoder.configure(mediaFormat, null, null, 0)
        decoder.start()
    }

    fun queueEOS() {
        if (!isDecodeFinish) {
            Log.d(TAG, "------------- decoder queueEOS ------------")
            isEOSNeed = true
        }
    }

    fun prepare(mediaFormat: MediaFormat, mediaExtractor: MediaExtractor) {
        configure(mediaFormat)
        extractor = mediaExtractor
    }

    fun pull(): Boolean {
        return if (isDecodeFinish) {
            false
        } else {
            val bufferInfo = MediaCodec.BufferInfo()
            val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, DEFAULT_QUEUE_TIMEOUT)

            //double check isDecodeFinish because last code is block
            when (outputBufferIndex) {
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    Log.d(TAG, "decoder output INFO_OUTPUT_BUFFERS_CHANGED")
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        outputBuffers = decoder.outputBuffers
                    }
                }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    Log.d(TAG, "decoder output INFO_OUTPUT_FORMAT_CHANGED")
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    Log.d(TAG, "decoder output INFO_TRY_AGAIN_LATER")
                }
                else -> {
                    if (bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                        Log.d(TAG, "decoder output generate sample data")
                        val outBuffer = getOutBuffer(outputBufferIndex)
                        onOutputBufferGenerate?.invoke(outBuffer, bufferInfo)
                        decoder.releaseOutputBuffer(outputBufferIndex, isRender)
                    } else {
                        Log.d(TAG, "=== decoder end of stream ===")
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
        if (inputBufferIndex >= 0) {

            if (isEOSNeed) {
                Log.d(TAG, "------------- queue EOS ------------")
                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                isEOSNeed = false
            } else {
                val inputBuffer = getInputBuffer(inputBufferIndex)
                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                if (sampleSize < 0) {
                    Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM")
                    decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                } else {
                    //here to filter sample data by limit duration
                    Log.d(TAG, "InputBuffer queueInputBuffer")
                    decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }

        }
    }

    fun release() {
        isDecodeFinish = true
        decoder.release()
    }

}
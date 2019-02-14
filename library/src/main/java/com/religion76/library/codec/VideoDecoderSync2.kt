package com.religion76.library.codec

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.support.annotation.RequiresApi
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Created by SunChao
 * on 2018/3/3.
 */
class VideoDecoderSync2 {

    companion object {
        const val TAG = "MediaCoder_Decoder"
        const val DEFAULT_QUEUE_TIMEOUT = 1000L
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
            Log.d(TAG, "------------- decoder queueEOS ------------")
            isEOSNeed = true
        }
    }

    fun prepare(mediaFormat: MediaFormat, mediaExtractor: MediaExtractor, surface: Surface): Boolean {
        Log.d(TAG, "on decoder configured $mediaFormat")
        try {
            //save frame rate data
            val frameRate = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE)

            decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                findDecoder(mediaFormat)
                        ?: MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME))
            } else {
                MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME))
            }

            //restore frame rate data
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            decoder.configure(mediaFormat, surface, null, 0)

            Log.d(TAG, "decoder: $decoder")

        } catch (e: Exception) {
            Log.d(TAG, "decoder configure failed: $e")
            return false
        }

        isRender = true
        extractor = mediaExtractor

        return true
    }

    fun start() {
        decoder.start()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun findDecoder(mediaFormat: MediaFormat): MediaCodec? {
        mediaFormat.setString(MediaFormat.KEY_FRAME_RATE, null)
        val codecName = MediaCodecList(1).findDecoderForFormat(mediaFormat)
        if (!codecName.isNullOrEmpty()) {
            return MediaCodec.createByCodecName(codecName)
        }

        return null
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
                        if (bufferInfo.size > 0) {
                            decoder.releaseOutputBuffer(outputBufferIndex, isRender)
                            onOutputBufferGenerate?.invoke(bufferInfo)
                        } else {
                            decoder.releaseOutputBuffer(outputBufferIndex, false)
                        }
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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (inputBuffers == null) {
                inputBuffers = decoder.inputBuffers
            }
        }

        val inputBufferIndex = decoder.dequeueInputBuffer(DEFAULT_QUEUE_TIMEOUT)
        //double check isDecodeFinish because last code is block
        if (inputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            Log.d(TAG, "decoder input INFO_OUTPUT_BUFFERS_CHANGED")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                inputBuffers = decoder.inputBuffers
            }
        } else if (inputBufferIndex >= 0) {

            if (isEOSNeed) {
                Log.d(TAG, "------------- queue EOS ------------")
                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                isEOSNeed = false
            } else {
                val inputBuffer = getInputBuffer(inputBufferIndex)
                inputBuffer?.let {
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM")
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        //here to filter sample data by limit duration
                        Log.d(TAG, "InputBuffer queueInputBuffer")
                        decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                        if (!extractor.advance()) {
                            isEOSNeed = true
                        }
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
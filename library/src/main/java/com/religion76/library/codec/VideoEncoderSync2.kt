package com.religion76.library.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import com.religion76.library.AppLogger
import com.religion76.library.MediaInfo
import java.nio.ByteBuffer

/**
 * Created by SunChao
 * on 2018/3/3.
 */
class VideoEncoderSync2 {

    companion object {
        private val TAG = "MediaCoder_Encoder2"
        const val DEFAULT_QUEUE_TIMEOUT = 10000L
    }

    private lateinit var encoder: MediaCodec

    lateinit var surface: Surface

    var isEncodeFinish = false

    var isEOSNeed = false

    fun prepare(mimeType: String, mediaInfo: MediaInfo, bitrate: Int? = null) {
        AppLogger.d(TAG, "prepare")

        encoder = MediaCodec.createEncoderByType(mimeType)

        val videoFormat: MediaFormat = if (mediaInfo.getRotation() > 0) {
            MediaFormat.createVideoFormat(mimeType, mediaInfo.getHeight(), mediaInfo.getWidth())
        } else {
            MediaFormat.createVideoFormat(mimeType, mediaInfo.getWidth(), mediaInfo.getHeight())
        }

        //ColorFormat should be MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate ?: mediaInfo.getBitrate())
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, MediaInfo.FRAME_RATE)
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, MediaInfo.IFRAMEINTERVAL)

        AppLogger.d(TAG, "on encoder configured $videoFormat")

        encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        surface = encoder.createInputSurface()

        encoder.start()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            inputBuffers = encoder.inputBuffers
            outputBuffers = encoder.outputBuffers
        }
    }

    fun prepare(mediaFormat: MediaFormat) {

        encoder = MediaCodec.createEncoderByType("video/avc")
        encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            inputBuffers = encoder.inputBuffers
            outputBuffers = encoder.outputBuffers
        }
    }

    fun getOutputFormat() = encoder.outputFormat

    lateinit var inputBuffers: Array<ByteBuffer>
    lateinit var outputBuffers: Array<ByteBuffer>

    var onSampleEncode: ((ByteBuffer, MediaCodec.BufferInfo) -> Unit)? = null

    var onEncoderCompleted: (() -> Unit)? = null

    var onOutputFormatChanged: ((MediaFormat) -> Unit)? = null

    private fun getInputBuffer(index: Int): ByteBuffer {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            inputBuffers[index]
        } else {
            encoder.getInputBuffer(index)
        }
    }

    private fun getOutputBuffer(index: Int): ByteBuffer {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            outputBuffers[index]
        } else {
            encoder.getOutputBuffer(index)
        }
    }

    private val bufferInfo by lazy {
        MediaCodec.BufferInfo()
    }

    fun pull(): Boolean {
        val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
        if (outputBufferIndex > 0) {
            AppLogger.d(TAG, "encoder output data index:$outputBufferIndex")
            val outputBuffer = getOutputBuffer(outputBufferIndex)
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> AppLogger.d(TAG, "encoder output try again later")
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    AppLogger.d(TAG, "encoder output format changed")
                    onOutputFormatChanged?.invoke(encoder.outputFormat)
                }
                bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0 -> {
                    AppLogger.d(TAG, "encoder buffer output ")
                    onSampleEncode?.invoke(outputBuffer, bufferInfo)
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                }
                else -> {
                    AppLogger.d(TAG, "=== encoder buffer end of stream ===")
                    isEncodeFinish = true
                    return false
                }
            }
        }

        return true
    }


    fun drain() {
        while (true){
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
            if (outputBufferIndex > 0) {
                AppLogger.d(TAG, "encoder output data index:$outputBufferIndex")
                val outputBuffer = getOutputBuffer(outputBufferIndex)
                when {
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        AppLogger.d(TAG, "encoder output try again later")
                        return
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        AppLogger.d(TAG, "encoder output format changed")
                        onOutputFormatChanged?.invoke(encoder.outputFormat)
                    }
                    bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0 -> {
                        AppLogger.d(TAG, "encoder buffer output ")
                        onSampleEncode?.invoke(outputBuffer, bufferInfo)
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                        return
                    }
                    else -> {
                        AppLogger.d(TAG, "=== encoder buffer end of stream ===")
                        isEncodeFinish = true
                    }
                }
            }
            break
        }
    }

    fun queueEOS() {
        isEncodeFinish = true
    }

    fun signEOS() {
        val inputBufferIndex = encoder.dequeueInputBuffer(DEFAULT_QUEUE_TIMEOUT)
        if (inputBufferIndex > 0) {
            encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            isEOSNeed = false
        }
    }

    fun release() {
        isEncodeFinish = true
        encoder.release()
    }

}
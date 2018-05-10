package com.religion76.mediaexecutor.coder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import java.nio.ByteBuffer

/**
 * Created by SunChao
 * on 2018/3/3.
 */
class VideoEncoder {

    companion object {
        private val TAG = "MediaCoder_Encoder"
    }

    private lateinit var encoder: MediaCodec

    fun prepare(mediaConfig: MediaConfig) {

        encoder = MediaCodec.createEncoderByType(mediaConfig.mineType)

        val videoFormat = MediaFormat.createVideoFormat(mediaConfig.mineType, mediaConfig.width, mediaConfig.height)
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecFormatUtils.getVideoCompatibilityColorFormat(encoder.codecInfo, mediaConfig.mineType))
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, mediaConfig.getCompressBitrate().toInt())
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mediaConfig.frameRate)
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mediaConfig.iFrameInterval)
        Log.d(TAG, "on encoder configured $videoFormat")

        encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            inputBuffers = encoder.inputBuffers
            outputBuffers = encoder.outputBuffers
        }

        loopEncoder()
    }

    fun prepare(mediaFormat: MediaFormat) {

        encoder = MediaCodec.createEncoderByType(MediaConfig().mineType)
        encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            inputBuffers = encoder.inputBuffers
            outputBuffers = encoder.outputBuffers
        }

        loopEncoder()
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

    private val bufferInfo = MediaCodec.BufferInfo()

    private fun loopEncoder() {
        Observable.create<Boolean> {
            while (true) {
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                if (outputBufferIndex > 0) {
                    Log.d(TAG, "encoder output data index:$outputBufferIndex")
                    val outputBuffer = getOutputBuffer(outputBufferIndex)
                    if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        Log.d(TAG, "encoder output try again later")
                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.d(TAG, "encoder output format changed")
                        onOutputFormatChanged?.invoke(encoder.outputFormat)
                    } else if (bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                        Log.d(TAG, "encoder buffer output ")
                        onSampleEncode?.invoke(outputBuffer, bufferInfo)
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                    } else {
                        Log.d(TAG, "encoder buffer end of stream")
                        it.onNext(true)
                        it.onComplete()
                        break
                    }
                }
            }
        }.subscribeOn(Schedulers.computation())
                .subscribe {
                    onEncoderCompleted?.invoke()
                }
    }

    fun offerData(data: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        val inputBufferIndex = encoder.dequeueInputBuffer(-1)
        if (inputBufferIndex > 0) {
            val inputBuffer = getInputBuffer(inputBufferIndex)

            inputBuffer.clear()
            inputBuffer.put(data)
            if (bufferInfo.size < 0) {
                encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } else {
                encoder.queueInputBuffer(inputBufferIndex, bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)

            }
        }
    }

    fun release() {
        encoder.release()
    }


}
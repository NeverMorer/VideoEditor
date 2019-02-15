package com.religion76.library.codec

import android.media.MediaCodec
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.nio.ByteBuffer

/**
 * Created by SunChao on 2019/1/7.
 */
class MediaCodecCompat(val codec: MediaCodec) {

    companion object {
        const val TIME_OUT = 1000L

        const val MSG_DRAIN_INPUT = 1
        const val MSG_DRAIN_OUTPUT = 2
        const val MSG_DRAIN_INPUT_OUTPUT = 3
        const val MSG_END = 4

        const val TAG = "MediaCodecCompat"
    }

    private lateinit var inputBuffers: Array<ByteBuffer>
    private lateinit var outputBuffers: Array<ByteBuffer>

    var handler: Handler? = null
        private set (value) {
            field = value
        }

    private var callback: MediaCodecCallback? = null

    private var callbackHandler: Handler? = null

    private var isStop = false

    private val bufferInfo by lazy {
        MediaCodec.BufferInfo()
    }

    fun start(type: Int) {
        codec.start()

        inputBuffers = codec.inputBuffers
        outputBuffers = codec.outputBuffers

        val handlerThread = HandlerThread("VideoEncoderCompat")
        handlerThread.start()

        val handler = Handler(handlerThread.looper) { message ->
            if (!isStop){
                when (message.what) {
                    MSG_DRAIN_INPUT -> {
                        drainInput()
                        handler?.sendEmptyMessage(MSG_DRAIN_INPUT)
                    }
                    MSG_DRAIN_OUTPUT -> {
                        drainOutput()
                        handler?.sendEmptyMessage(MSG_DRAIN_OUTPUT)
                    }

                    MSG_DRAIN_INPUT_OUTPUT -> {
                        drain()
                        handler?.sendEmptyMessage(MSG_DRAIN_INPUT_OUTPUT)
                    }
                    MSG_END -> {
                        handler?.looper?.quit()
                    }
                    else -> {
                    }
                }
            }

            true
        }

        this.handler = handler
        handler.sendEmptyMessage(type)
    }


    fun setCallback(callback: MediaCodecCallback, handler: Handler? = null) {
        this.callback = callback
        this.callbackHandler = handler
    }

    fun getInputBuffer(index: Int): ByteBuffer? {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            inputBuffers[index]
        } else {
            codec.getInputBuffer(index)
        }
    }

    fun getOutputBuffer(index: Int): ByteBuffer? {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            outputBuffers[index]
        } else {
            codec.getOutputBuffer(index)
        }
    }

    fun stop(){
        isStop = true
    }

    private fun drain() {
        drainInput()
        drainOutput()
    }

    private fun drainInput() {
        val inputBufferIndex = codec.dequeueInputBuffer(TIME_OUT)
        if (inputBufferIndex > 0) {
            callbackHandler?.post {
                callback?.onInputBufferAvailable(codec, inputBufferIndex, inputBuffers[inputBufferIndex])
            }
                    ?: callback?.onInputBufferAvailable(codec, inputBufferIndex, inputBuffers[inputBufferIndex])
        }
    }

    private fun drainOutput() {

        val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIME_OUT)
        if (outputBufferIndex >= 0) {
            Log.d(TAG, "encoder output data index:$outputBufferIndex")
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    Log.d(TAG, "encoder output try again later")
                    return
                }

                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    Log.d(TAG, "encoder output format changed")
                    callbackHandler?.post {
                        callback?.onOutputFormatChanged(codec, codec.outputFormat)
                    } ?: callback?.onOutputFormatChanged(codec, codec.outputFormat)
                }

                outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    Log.d(TAG, "encoder output buffers changed")
                    outputBuffers = codec.outputBuffers
                }

                bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0 -> {
                    Log.d(TAG, "encoder buffer output ")
                    callbackHandler?.post {
                        callback?.onOutputBufferAvailable(codec, outputBufferIndex, bufferInfo, outputBuffers[outputBufferIndex])
                    }
                            ?: callback?.onOutputBufferAvailable(codec, outputBufferIndex, bufferInfo, outputBuffers[outputBufferIndex])
//                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                    return
                }

                else -> {
                    Log.d(TAG, "=== encoder buffer end of stream ===")
                    handler?.sendEmptyMessage(MSG_END)
                    return
                }
            }
        }
    }

    fun release() {
        codec.stop()
        codec.release()
    }
}
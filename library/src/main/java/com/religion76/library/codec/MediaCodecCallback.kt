package com.religion76.library.codec

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * Created by SunChao on 2019/1/7.
 */
interface MediaCodecCallback {

    fun onInputBufferAvailable(codec: MediaCodec, index: Int, inputBuffer: ByteBuffer?)

    fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo, outputBuffer: ByteBuffer?)

    fun onError(codec: MediaCodec, e: MediaCodec.CodecException)

    fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat)
}
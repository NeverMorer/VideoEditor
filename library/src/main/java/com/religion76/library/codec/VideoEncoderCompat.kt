package com.religion76.library.codec

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.support.annotation.RequiresApi
import android.view.Surface
import com.religion76.library.AppLogger
import com.religion76.library.MediaInfo
import java.lang.Exception
import java.lang.IllegalStateException
import java.nio.ByteBuffer

/**
 * Created by SunChao on 2019/1/7.
 */
class VideoEncoderCompat(private val callback: MediaCodecCallback) {

    private var encoder: MediaCodec? = null

    private var encoderCompat: MediaCodecCompat? = null

    var handler: Handler? = null

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun findEncoder(mediaFormat: MediaFormat): MediaCodec? {
        mediaFormat.setString(MediaFormat.KEY_FRAME_RATE, null)
        val codecName = MediaCodecList(0).findEncoderForFormat(mediaFormat)
        if (!codecName.isNullOrEmpty()) {
            return MediaCodec.createByCodecName(codecName)
        }

        return null
    }

    fun configure(inputFormat: MediaFormat, outputFormat:MediaFormat): Surface? {
        initCodec(inputFormat)
        val codec = encoder ?: encoderCompat?.codec ?: throw IllegalStateException("no codec")
        try {
            codec.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: Exception) {
            AppLogger.d("VideoEncoderCompat", e)
            return null
        }
        return codec.createInputSurface()
    }


    private fun initCodec(mediaFormat: MediaFormat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //save frame rate
            var frameRate = MediaInfo.FRAME_RATE

            try {
                frameRate = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            encoder = findEncoder(mediaFormat)

            //restore frame rate
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)

            val callback = object : MediaCodec.Callback() {
                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    callback.onOutputBufferAvailable(codec, index, info, codec.getOutputBuffer(index))
                }

                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                    callback.onInputBufferAvailable(codec, index, codec.getInputBuffer(index))
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    callback.onOutputFormatChanged(codec, format)
                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    callback.onError(codec, e)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                encoder?.setCallback(callback, handler)
            } else {
                encoder?.setCallback(callback)
            }
        }

        if (encoder == null) {

            val encoder = MediaCodec.createEncoderByType(mediaFormat.getString(MediaFormat.KEY_MIME))
            val mediaCodecCompat = MediaCodecCompat(encoder)
            callback.let {
                mediaCodecCompat.setCallback(it, handler)
            }
            this.encoderCompat = mediaCodecCompat
        }
    }


    fun getInputBuffer(index: Int): ByteBuffer? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            encoder?.getInputBuffer(index)
        } else {
            encoderCompat?.getInputBuffer(index)
        }
    }

    fun getOutputBuffer(index: Int): ByteBuffer? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            encoder?.getOutputBuffer(index)
        } else {
            encoderCompat?.getOutputBuffer(index)
        }
    }

    fun start() {
        if (encoder != null) {
            encoder?.start()
        } else if (encoderCompat != null) {
            encoderCompat?.start(MediaCodecCompat.MSG_DRAIN_OUTPUT)
        }
    }

    fun release() {
        if (encoder != null) {
            encoder!!.stop()
            encoder!!.release()
            encoder = null
        }

        if (encoderCompat != null) {
            encoderCompat!!.release()
            encoderCompat = null
        }
    }
}
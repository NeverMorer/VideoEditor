package com.religion76.library.v2

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.util.Log
import com.religion76.library.AppLogger
import com.religion76.library.gles.*
import com.religion76.library.MediaInfo
import com.religion76.library.codec.MediaCodecCallback
import com.religion76.library.codec.VideoDecoderSync2
import com.religion76.library.codec.VideoEncoderCompat
import java.lang.Exception
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ArrayBlockingQueue

/**
 * Created by SunChao
 * on 2018/5/24.
 */
class SeparateVideoCoder2(private val path: String, private val mediaMuxer: MediaMuxer, private val mediaExtractor: MediaExtractor) {

    companion object {
        const val TAG = "SeparateVideoCoder"
    }

    private var videoEncoder: VideoEncoderCompat? = null

    private var videoDecoder: VideoDecoderSync2? = null

    private var frameRender: FrameRender? = null

    private lateinit var mediaInfo: MediaInfo

    private var muxTrackIndex: Int = -1

    private var startMs: Long? = null
    private var endMs: Long? = null

    private var bitrate: Int? = null

    private var scaleWidth: Int? = null
    private var scaleHeight: Int? = null

    private var isRotate = false

    private var callback: MediaExecuteCallback? = null
    private var callbackHandler: Handler? = null

    private var lastFrameTime: Long = -1
    private var lastFrameTimeTemp: Long = -1

    //it's should be support via use GLES on the way to encoder
    fun withScale(width: Int? = null, height: Int? = null) {
        scaleWidth = width
        scaleHeight = height
        AppLogger.d(TAG, "setting scale width:$width  height:$height")
    }

    fun withTrim(startMs: Long? = null, endMs: Long? = null) {
        this.startMs = startMs
        this.endMs = endMs
        AppLogger.d(TAG, "setting trim start:$startMs  end:$endMs")
    }

    fun setBitrate(bitrate: Int) {
        this.bitrate = bitrate
        AppLogger.d(TAG, "setting bitrate:$bitrate")
    }

    fun setRotate(rotate: Boolean) {
        this.isRotate = rotate
    }

    fun prepare(trackFormat: MediaFormat): Boolean {

        mediaInfo = MediaInfo.getMediaInfo(path)

        AppLogger.d(TAG, "original width:${mediaInfo.getWidth()}  height:${mediaInfo.getHeight()}")
        AppLogger.d(TAG, "original bitrate:${mediaInfo.getBitrate()}")

        if (scaleWidth != null && scaleHeight != null) {
            mediaInfo.setScale(scaleWidth!!, scaleHeight!!)
        }

        isPrepared = initEncoder(trackFormat) && initDecoder(trackFormat)

        return isPrepared
    }

    fun setCallback(callback: MediaExecuteCallback, handler: Handler? = null) {
        this.callback = callback
        this.callbackHandler = handler
    }

    private fun initDecoder(mediaFormat: MediaFormat): Boolean {
        videoDecoder = VideoDecoderSync2()

        if (!videoDecoder!!.prepare(mediaFormat, mediaExtractor, frameRender!!.getDecodeOutputSurface())) {
            return false
        }

        videoDecoder!!.onOutputBufferGenerate = { bufferInfo ->
            AppLogger.d(TAG, "decode_buffer_timeUs: ${bufferInfo.presentationTimeUs}")
            AppLogger.d(TAG, "decode_buffer_size: ${bufferInfo.size}")
            AppLogger.d(TAG, "decode_buffer_offset: ${bufferInfo.offset}")
            AppLogger.d(TAG, "decode_buffer_flag: ${bufferInfo.flags}")
            lastFrameTimeTemp = bufferInfo.presentationTimeUs
            frameRender?.draw(bufferInfo.presentationTimeUs)
        }

        videoDecoder!!.onDecodeFinish = {
            lastFrameTime = lastFrameTimeTemp
        }

        return true
    }

    private fun initEncoder(mediaFormat: MediaFormat): Boolean {
        videoEncoder = VideoEncoderCompat(object : MediaCodecCallback {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int, inputBuffer: ByteBuffer?) {
                Log.d(TAG, "encoder_onInputBufferAvailable")
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo, outputBuffer: ByteBuffer?) {
                Log.d(TAG, "encoder_onOutputBufferAvailable")
                if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM || (lastFrameTime > 0 && info.presentationTimeUs >= lastFrameTime)) {
                    Log.d(TAG, "encoder_lastFrameTime: $lastFrameTime")
                    Log.d(TAG, "encoder_bufferTime: ${info.presentationTimeUs}")
                    Log.d(TAG, "encoder_receive end")
                    releaseEncoder()

                    callback?.let {
                        callbackHandler?.post {
                            it.onComplete()
                        } ?: it.onComplete()
                    }

                } else {
                    if (info.size > 0) {
                        if (muxTrackIndex == -1) {
                            sampleIndexQueue.offer(index)
                            sampleInfoQueue.offer(info)
                            AppLogger.d(TAG, "writeSample queue")
                        } else {
                            AppLogger.d(TAG, "writeSample execute")
                            flushSampleQueue(codec)

                            videoEncoder?.getOutputBuffer(index)?.let { outputBuffer ->
                                mediaMuxer.writeSampleData(muxTrackIndex, outputBuffer, info)
                            }

                            codec.releaseOutputBuffer(index, false)
                        }

                    } else {
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.d(TAG, "encoder_onError")
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                Log.d(TAG, "encoder_onOutputFormatChanged")
                muxTrackIndex = mediaMuxer.addTrack(format)

                callback?.let {
                    callbackHandler?.post {
                        it.onMediaTrackReady()
                    } ?: it.onMediaTrackReady()
                }

                if (muxTrackIndex > -1) {
                    mediaMuxer.start()
                    flushSampleQueue(codec)
                } else {
                    codec.signalEndOfInputStream()

                    callback?.let {
                        callbackHandler?.post {
                            it.onError(Exception("muxer add track failed ..."))
                        } ?: it.onError(Exception("muxer add track failed ..."))
                    }
                }
            }

        })

        val surface = videoEncoder!!.configure(mediaFormat, mediaInfo, bitrate) ?: return false

        //todo try catch error when device not support egl
        frameRender = FrameRender()
        frameRender!!.init(surface)

        return true
    }

    private fun flushSampleQueue(encoder: MediaCodec) {
        while (sampleIndexQueue.size > 0) {
            val nextBufferIndex = sampleIndexQueue.poll()
            val nextBufferInfo = sampleInfoQueue.poll()
            videoEncoder?.getOutputBuffer(nextBufferIndex)?.let { outputBuffer ->
                mediaMuxer.writeSampleData(muxTrackIndex, outputBuffer, nextBufferInfo)
            }

            encoder.releaseOutputBuffer(nextBufferIndex, false)
        }
    }


    private val sampleIndexQueue: Queue<Int> = ArrayBlockingQueue<Int>(5)
    private val sampleInfoQueue: Queue<MediaCodec.BufferInfo> = ArrayBlockingQueue<MediaCodec.BufferInfo>(5)

    private var isPrepared = false

    var isCoderDone = false

    fun start() {

        if (!isPrepared) {
            Log.d(TAG, "video coder not prepared")
            return
        }

        videoEncoder!!.start()
        videoDecoder!!.start()

        while (true) {
            videoDecoder!!.enqueueData()

            if (!videoDecoder!!.pull()) {
                break
            }
        }
    }

    private fun releaseEncoder() {
        if (videoEncoder != null) {
            videoEncoder!!.release()
            videoEncoder = null
        }
    }

    private fun releaseDecoder() {
        if (videoDecoder != null) {
//            decoder!!.stop()
            videoDecoder!!.release()
            videoDecoder = null
        }
    }

    fun release() {
        AppLogger.d(TAG, "release")
        releaseEncoder()
        releaseDecoder()

        if (frameRender != null) {
            frameRender!!.release()
            frameRender = null
        }

    }
}
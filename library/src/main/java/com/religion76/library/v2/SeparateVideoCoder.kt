package com.religion76.library.v2

import android.media.*
import android.os.Handler
import android.util.Log
import com.religion76.library.AppLogger
import com.religion76.library.MediaInfo
import com.religion76.library.codec.MediaCodecCallback
import com.religion76.library.codec.VideoDecoderSync2
import com.religion76.library.codec.VideoEncoderCompat
import com.religion76.library.gles.*
import java.lang.Exception
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ArrayBlockingQueue

/**
 * Created by SunChao
 * on 2018/5/24.
 */
class SeparateVideoCoder(private val path: String, private val mediaMuxer: MediaMuxer, private val mediaExtractor: MediaExtractor) {

    companion object {
        const val TAG = "SeparateVideoCoder"
    }

    private var videoEncoder: VideoEncoderCompat? = null

    private var videoDecoder: VideoDecoderSync2? = null

    private lateinit var mediaInfo: MediaInfo

    private var muxTrackIndex: Int = -1

    private var startMs: Long? = null
    private var endMs: Long? = null

    private var bitrate: Int? = null

    private var scaleWidth: Int? = null
    private var scaleHeight: Int? = null

    private var callback: MediaExecuteCallback? = null
    private var callbackHandler: Handler? = null

    private var lastFrameTime: Long = -1
    private var lastFrameTimeTemp: Long = -1

    private var inputSurface: WindowSurface? = null
    private var outputSurface: CodecOutputSurface2? = null

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

    fun prepare(trackFormat: MediaFormat): Boolean {

        mediaInfo = MediaInfo.getMediaInfo(path)

        AppLogger.d(TAG, "original width:${mediaInfo.getWidth()}  height:${mediaInfo.getHeight()}")
        AppLogger.d(TAG, "original bitrate:${mediaInfo.getBitrate()}")
        AppLogger.d(TAG, "original rotation:${mediaInfo.getRotation()}")

        if (scaleWidth != null && scaleHeight != null) {
            mediaInfo.setScale(scaleWidth!!, scaleHeight!!)
        }

        mediaMuxer.setOrientationHint(mediaInfo.getRotation())

        isPrepared = initEncoder(trackFormat) && initDecoder(trackFormat)

        return isPrepared
    }

    fun setCallback(callback: MediaExecuteCallback, handler: Handler? = null) {
        this.callback = callback
        this.callbackHandler = handler
    }

    private fun initDecoder(mediaFormat: MediaFormat): Boolean {
        videoDecoder = VideoDecoderSync2()

        outputSurface = CodecOutputSurface2()

        //compatible setting,codec not support rotation format before android 21
        mediaFormat.setInteger("rotation", 0)
        mediaFormat.setInteger("rotation-degrees", 0)

        if (!videoDecoder!!.prepare(mediaFormat, mediaExtractor, outputSurface!!.surface)) {
            return false
        }

        videoDecoder!!.onOutputBufferGenerate = { bufferInfo ->

            if (endMs != null && lastFrameTimeTemp > endMs!! * 1000) {
                videoDecoder!!.isDecodeFinish = true
            } else {
                lastFrameTimeTemp = bufferInfo.presentationTimeUs

                if (inputSurface != null && outputSurface != null) {

                    outputSurface!!.awaitNewImage()
                    outputSurface!!.drawImage(false)

                    inputSurface!!.setPresentationTime(bufferInfo.presentationTimeUs * 1000)
                    inputSurface!!.swapBuffers()
                }
            }
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

                    Log.d(TAG, "encoder_receive end")
                    videoEncoder?.stop()
                    releaseEncoder()

                    callback?.let {
                        callbackHandler?.post {
                            it.onComplete()
                        } ?: it.onComplete()
                    }

                } else {
                    if (info.size > 0) {
                        prepareMuxer(codec.outputFormat, codec)

                        if (muxTrackIndex == -1) {
                            //todo add error callback
                        } else {
                            videoEncoder?.getOutputBuffer(index)?.let { outputBuffer ->

                                //todo fix bug
                                if (startMs != null) {
                                    val startTimeUs = startMs!! * 1000
                                    if (info.presentationTimeUs >= startTimeUs) {
                                        info.presentationTimeUs = info.presentationTimeUs - startTimeUs
                                    }
                                }

                                mediaMuxer.writeSampleData(muxTrackIndex, outputBuffer, info)
                            }
                        }
                    }

                    codec.releaseOutputBuffer(index, false)

                }
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.d(TAG, "encoder_onError")
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                Log.d(TAG, "encoder_onOutputFormatChanged")
                prepareMuxer(format, codec)
            }

        })

        val encoderSurface = videoEncoder!!.configure(mediaFormat.getString(MediaFormat.KEY_MIME), buildEncodeOutputMediaFormat(mediaFormat, mediaInfo, bitrate))
                ?: return false

        inputSurface = WindowSurface(EglCore(null, EglCore.FLAG_RECORDABLE), encoderSurface, true)
        inputSurface!!.makeCurrent()

        return true
    }

    private fun prepareMuxer(mediaFormat: MediaFormat, encoder: MediaCodec) {
        if (muxTrackIndex >= 0) {
            return
        }

        AppLogger.d("ddd", "muxer format: $mediaFormat")

        muxTrackIndex = mediaMuxer.addTrack(mediaFormat)

        callback?.let {
            callbackHandler?.post {
                it.onMediaTrackReady()
            } ?: it.onMediaTrackReady()
        }

        if (muxTrackIndex > -1) {
            mediaMuxer.start()
            flushSampleQueue(encoder)
        } else {
            encoder.signalEndOfInputStream()

            callback?.let {
                callbackHandler?.post {
                    it.onError(Exception("muxer add track failed ..."))
                } ?: it.onError(Exception("muxer add track failed ..."))
            }
        }
    }

    private fun buildEncodeOutputMediaFormat(inputFormat: MediaFormat, mediaInfo: MediaInfo?, bitRate: Int?): MediaFormat {

        val rotate = mediaInfo?.getRotation() ?: 0

        val originWidth = mediaInfo?.getWidth() ?: inputFormat.getInteger(MediaFormat.KEY_WIDTH)
        val originHeight = mediaInfo?.getHeight() ?: inputFormat.getInteger(MediaFormat.KEY_HEIGHT)

        val width = if (rotate == 90 || rotate == 270) originHeight else originWidth
        val height = if (rotate == 90 || rotate == 270) originWidth else originHeight

//        val width = mediaInfo?.getWidth() ?: inputFormat.getInteger(MediaFormat.KEY_WIDTH)
//        val height = mediaInfo?.getHeight() ?: inputFormat.getInteger(MediaFormat.KEY_HEIGHT)

        AppLogger.d("ddd", "info width: ${mediaInfo?.getWidth()}, height: ${mediaInfo?.getHeight()}")

        val mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height)

        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, MediaInfo.FRAME_RATE)
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, MediaInfo.IFRAMEINTERVAL)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate
                ?: (width * height * 30 * 0.3).toInt())

//        mediaFormat.setInteger(MediaFormat.KEY_ROTATION, rotate)
        return mediaFormat
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

        inputSurface?.release()
        inputSurface = null

        outputSurface?.release()
        outputSurface = null
    }
}
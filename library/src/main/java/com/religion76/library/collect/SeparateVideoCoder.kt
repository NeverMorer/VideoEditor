package com.religion76.library.collect

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.religion76.library.AppLogger
import com.religion76.library.gles.*
import com.religion76.library.MediaInfo
import com.religion76.library.codec.VideoDecoderSync2

/**
 * Created by SunChao
 * on 2018/5/24.
 */
class SeparateVideoCoder(private val path: String, private val mediaMuxer: MediaMuxer, private val mediaExtractor: MediaExtractor) {

    companion object {
        const val TAG = "SeparateVideoCoder"
    }

    private lateinit var videoEncoder: VideoEncoderSync

    private lateinit var videoDecoder: VideoDecoderSync2

    private lateinit var mediaInfo: MediaInfo

    private val frameRender = FrameRender()

    private var muxTrackIndex: Int = -1

    private var startMs: Long? = null
    private var endMs: Long? = null

    private var bitrate: Int? = null

    private var scaleWidth: Int? = null
    private var scaleHeight: Int? = null

    private var isRotate = false

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

        return initEncoder(trackFormat.getString(MediaFormat.KEY_MIME)) && initDecoder(trackFormat)
    }


    @Volatile
    private var isNewFrameAvailable: Boolean = false

    private fun initDecoder(mediaFormat: MediaFormat): Boolean {
        videoDecoder = VideoDecoderSync2()

        if (!videoDecoder.prepare(mediaFormat, mediaExtractor, frameRender.getDecodeOutputSurface())) {
            return false
        }

        videoDecoder.onOutputBufferGenerate = { bufferInfo ->
            AppLogger.d(TAG, "onOutputBufferGenerate")
            AppLogger.d(TAG, "decode_presentationTimeUs: ${bufferInfo.presentationTimeUs}")

            frameRender.draw(bufferInfo.presentationTimeUs)
            isNewFrameAvailable = true
        }

        videoDecoder.onDecodeFinish = {
            AppLogger.d(TAG, "onDecodeFinish")
            videoEncoder.queueEOS()
        }

        return true
    }

    private fun initEncoder(mimeType: String): Boolean {
        videoEncoder = VideoEncoderSync()

        if (!videoEncoder.prepare(mimeType, mediaInfo, bitrate)) {
            return false
        }

        //todo try catch error when device not support egl
        frameRender.init(videoEncoder.getSurface())

        videoEncoder.onSampleEncode = { dataBuffer, bufferInfo ->
            AppLogger.d(TAG, "onSampleEncode")

            //video encode need format include csd-0 & csd-1 data
            if (muxTrackIndex == -1) {
                AppLogger.d(TAG, "video encode outputFormat:${videoEncoder.getOutputFormat()}")
                muxTrackIndex = mediaMuxer.addTrack(videoEncoder.getOutputFormat())
                AppLogger.d(TAG, "video encode muxer track index:$muxTrackIndex")
                mediaMuxer.start()
            }

            AppLogger.d(TAG, "encode_presentationTimeUs: ${bufferInfo.presentationTimeUs}")

            if (endMs != null && bufferInfo.presentationTimeUs > endMs!! * 1000 && !videoDecoder.isDecodeFinish) {
                AppLogger.d(TAG, "------------- end trim ------------")
                videoDecoder.queueEOS()
                videoEncoder.queueEOS()
            } else {
                mediaMuxer.writeSampleData(muxTrackIndex, dataBuffer, bufferInfo)
            }
        }

        return true
    }


    var isCoderDone = false

    fun drain() {
        if (!isCoderDone) {
            if (videoDecoder.isDecodeFinish && videoEncoder.isEncodeFinish) {
                AppLogger.d(TAG, "end")
                isCoderDone = true
                release()
            } else {
                if (isNewFrameAvailable) {
                    isNewFrameAvailable = false
                    videoEncoder.drain()
                }

                videoDecoder.enqueueData()
                videoDecoder.pull()

                if (videoEncoder.isEOSNeed) {
                    videoEncoder.signEOS()
                }

                if (videoEncoder.isEOSQueue) {
                    AppLogger.d(TAG, "drain from ")
                    videoEncoder.drain()
                }
            }
        }
    }

    fun release() {
        AppLogger.d(TAG, "release")
        frameRender.release()
        videoDecoder.release()
        videoEncoder.release()
    }
}
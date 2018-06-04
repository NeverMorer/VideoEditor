package com.religion76.library.sync

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.religion76.library.gles.*
import java.util.*

/**
 * Created by SunChao
 * on 2018/5/24.
 */
class SeparateVideoCoder(private val path: String, private val mediaMuxer: MediaMuxer, private val mediaExtractor: MediaExtractor) {

    companion object {
        const val TAG = "SeparateVideoCoder"
    }

    private lateinit var videoEncoder: VideoEncoderSync2

    private lateinit var videoDecoder: VideoDecoderSync2

    private lateinit var encodeSurface: WindowSurface

    private lateinit var outputSurface: CodecOutputSurface2

    private lateinit var mediaInfo: MediaInfo

    private lateinit var eglCore: EglCore

    private var muxTrackIndex: Int = -1

    private var startMs: Long? = null
    private var endMs: Long? = null

    private var bitrate: Int? = null

    private var scaleWidth: Int? = null
    private var scaleHeight: Int? = null


    //it's should be support via use GLES on the way to encoder
    fun withScale(width: Int? = null, height: Int? = null) {
        scaleWidth = width
        scaleHeight = height
        Log.d(TAG, "setting scale width:$width  height:$height")
    }

    fun withTrim(startMs: Long? = null, endMs: Long? = null) {
        this.startMs = startMs
        this.endMs = endMs
        Log.d(TAG, "setting trim start:$startMs  end:$endMs")
    }

    fun setBitrate(bitrate: Int) {
        this.bitrate = bitrate
        Log.d(TAG, "setting bitrate:$bitrate")
    }

    fun prepare(trackFormat: MediaFormat) {

        mediaInfo = MediaInfo.getMediaInfo(path)

        Log.d(TAG, "original width:${mediaInfo.getWidth()}  height:${mediaInfo.getHeight()}")
        Log.d(TAG, "original bitrate:${mediaInfo.getBitrate()}")

        if (scaleWidth != null && scaleHeight != null) {
            mediaInfo.setScale(scaleWidth!!, scaleHeight!!)
        }

        initEncoder(trackFormat.getString(MediaFormat.KEY_MIME))
        initDecoder(trackFormat)

        muxTrackIndex = mediaMuxer.addTrack(videoEncoder.getOutputFormat())

        mediaMuxer.start()

        Log.d(VideoAudioCoder.TAG, "video muxer track index:$muxTrackIndex")
    }

    private fun initTexture() {

        eglCore = EglCore(null, EglCore.FLAG_RECORDABLE)

        encodeSurface = WindowSurface(eglCore, videoEncoder.surface, true)

        encodeSurface.makeCurrent()

        outputSurface = CodecOutputSurface2()
    }

    @Transient
    private var isNewFrameAvailable: Boolean = false

    private var bufferTimeQueue: Queue<Long> = ArrayDeque()

    private fun draw() {
        Log.d(TAG, "draw")

        outputSurface.awaitNewImage()

        encodeSurface.makeCurrent()

        outputSurface.drawImage(false)

        if (bufferTimeQueue.isNotEmpty()) {
            encodeSurface.setPresentationTime(bufferTimeQueue.poll() * 1000)
        }
        encodeSurface.swapBuffers()

        isNewFrameAvailable = true
    }

    private fun initDecoder(mediaFormat: MediaFormat) {
        videoDecoder = VideoDecoderSync2()

        videoDecoder.prepare(mediaFormat, mediaExtractor, outputSurface.surface)

        videoDecoder.onOutputBufferGenerate = { dataBuffer, bufferInfo ->
            Log.d(TAG, "onOutputBufferGenerate")
            Log.d(TAG, "decode_presentationTimeUs: ${bufferInfo.presentationTimeUs}")

            bufferTimeQueue.offer(bufferInfo.presentationTimeUs)
            draw()
        }

        videoDecoder.onDecodeFinish = {
            Log.d(TAG, "onDecodeFinish")
            encodeSurface.release()
            videoEncoder.queueEOS()
        }
    }

    private fun initEncoder(mimeType: String) {
        videoEncoder = VideoEncoderSync2()

        videoEncoder.prepare(mimeType, mediaInfo, bitrate)

        initTexture()

        videoEncoder.onSampleEncode = { dataBuffer, bufferInfo ->
            Log.d(TAG, "onSampleEncode")
//            if (muxTrackIndex == -1) {
//                muxTrackIndex = mediaMuxer!!.addTrack(videoEncoder.getOutputFormat())
//                isMuxTrackAdded = true
//            }

            Log.d(TAG, "encode_presentationTimeUs: ${bufferInfo.presentationTimeUs}")

            if (endMs != null && bufferInfo.presentationTimeUs > endMs!! * 1000 && !videoDecoder.isDecodeFinish) {
                Log.d(TAG, "------------- end trim ------------")
                videoDecoder.queueEOS()
                videoEncoder.queueEOS()
            } else {
                mediaMuxer.writeSampleData(muxTrackIndex, dataBuffer, bufferInfo)
            }
        }
    }

    private var isLooping = false

    var isCoderDone = false

    fun drain() {

//        if (!isCoderDone) {
//            if (videoDecoder.isDecodeFinish && videoEncoder.isEncodeFinish) {
//                Log.d(TAG, "end")
//                isCoderDone = true
//                release()
//            } else {
//                if (!isLooping) {
//                    videoDecoder.enqueueData()
//                    videoDecoder.pull()
//                }
//
//                if (isNewFrameAvailable) {
//
//                    isLooping = true
//                    isNewFrameAvailable = false
//
//                    videoEncoder.pull()
//                    videoDecoder.enqueueData()
//                    videoDecoder.pull()
//
//                    if (videoEncoder.isEOSNeed) {
//                        videoEncoder.signEOS()
//                    }
//                }
//            }
//        }

        while (true) {

            if (videoDecoder.isDecodeFinish && videoEncoder.isEncodeFinish) {
                Log.d(TAG, "end")
                isCoderDone = true
                release()
                break
            }

            if (!isLooping) {
                videoDecoder.enqueueData()
                videoDecoder.pull()
            }

            if (isNewFrameAvailable) {

                isLooping = true
                isNewFrameAvailable = false

                videoEncoder.pull()
                videoDecoder.enqueueData()
                videoDecoder.pull()

                if (videoEncoder.isEOSNeed) {
                    videoEncoder.signEOS()
                }
            }
        }
    }

    fun release() {
        Log.d(TAG, "release")
        eglCore.release()
        encodeSurface.release()
        outputSurface.release()

        videoDecoder.release()
        videoEncoder.release()
    }
}
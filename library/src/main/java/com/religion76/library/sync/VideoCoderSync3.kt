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
class VideoCoderSync3(private val path: String, private val dest: String) : Runnable{

    companion object {
        const val TAG = "VideoCoderSync2"
    }

    private lateinit var videoEncoder: VideoEncoderSync2

    private lateinit var videoDecoder: VideoDecoderSync2

    private lateinit var mediaExtractor: MediaExtractor

    private lateinit var encodeSurface: WindowSurface

    private lateinit var outputSurface: CodecOutputSurface2

    private lateinit var mediaInfo: MediaInfo

    private lateinit var eglCore: EglCore

    private var mediaMuxer: MediaMuxer? = null

    private var muxTrackIndex: Int = -1

    private var startMs: Long? = null
    private var endMs: Long? = null

    private var bitrate: Int? = null

    override fun run() {

        if (prepare()) {
            loop()
        }
    }

    //it's should be support via use GLES on the way to encoder
    private fun withScale(width: Int, height: Int) {

    }

    fun withTrim(startMs: Long? = null, endMs: Long? = null) {
        this.startMs = startMs
        this.endMs = endMs
    }

    fun setBitrate(bitrate: Int) {
        this.bitrate = bitrate
    }

    private fun prepare(): Boolean {

        mediaInfo = MediaInfo.getMediaInfo(path)

        mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(path)

        for (i in 0..mediaExtractor.trackCount) {
            val trackFormat = mediaExtractor.getTrackFormat(i)
            val mimeType = trackFormat.getString(MediaFormat.KEY_MIME)
            if (mimeType.startsWith("video")) {
                mediaExtractor.selectTrack(i)

                //Encoder must be init first
                initEncoder(mimeType)
                initDecoder(trackFormat)

                return true
            }
        }

        return false
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

    private fun initEncoder(mimeType:String) {
        videoEncoder = VideoEncoderSync2()

        videoEncoder.prepare(mimeType, mediaInfo, bitrate)

        initTexture()

        videoEncoder.onSampleEncode = { dataBuffer, bufferInfo ->
            Log.d(TAG, "onSampleEncode")
            if (mediaMuxer == null) {
                initMediaMuxer()
            }

            Log.d(TAG, "encode_presentationTimeUs: ${bufferInfo.presentationTimeUs}")

            if (endMs != null && bufferInfo.presentationTimeUs > endMs!! * 1000 && !videoDecoder.isDecodeFinish) {
                Log.d(TAG, "------------- end trim ------------")
                videoDecoder.queueEOS()
                videoEncoder.queueEOS()
            } else {
                mediaMuxer?.writeSampleData(muxTrackIndex, dataBuffer, bufferInfo)
            }
        }
    }

    private fun initMediaMuxer() {

        mediaMuxer = MediaMuxer(dest, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxTrackIndex = mediaMuxer!!.addTrack(videoEncoder.getOutputFormat())

        mediaMuxer!!.start()
    }

    private var isLooping = false

    private fun loop() {
        startMs?.let {
            mediaExtractor.seekTo(it * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        }

        while (true) {

            if (videoDecoder.isDecodeFinish && videoEncoder.isEncodeFinish) {
                Log.d(TAG, "there")
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
        videoDecoder.release()
        videoEncoder.release()
        mediaExtractor.release()
        mediaMuxer?.release()

        eglCore.release()
        encodeSurface.release()
        outputSurface.release()

    }

}
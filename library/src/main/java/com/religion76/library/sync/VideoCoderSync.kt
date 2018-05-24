package com.religion76.library.sync

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.religion76.library.coder.MediaConfig

/**
 * Created by SunChao
 * on 2018/5/24.
 */
class VideoCoderSync(private val path: String, private val dest: String) : Runnable {

    companion object {
        const val TAG = "VideoCoderSync"
    }

    private lateinit var videoEncoder: VideoEncoderSync

    private lateinit var videoDecoder: VideoDecoderSync

    private lateinit var mediaExtractor: MediaExtractor

    private var mediaMuxer: MediaMuxer? = null

    private var muxTrackIndex: Int = -1

    private var height: Int? = null
    private var width: Int? = null

    private var startMs: Long? = null
    private var endMs: Long? = null

    override fun run() {
        if (prepare()) {
            loop()
        }
    }

    fun withScale(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    fun withTrim(startMs: Long? = null, endMs: Long? = null) {
        this.startMs = startMs
        this.endMs = endMs
    }

    private fun prepare(): Boolean {
        mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(path)

        for (i in 0..mediaExtractor.trackCount) {
            val trackFormat = mediaExtractor.getTrackFormat(i)
            if (trackFormat.getString(MediaFormat.KEY_MIME).startsWith("video")) {
                mediaExtractor.selectTrack(i)

                initDecoder(trackFormat)
                initEncoder(trackFormat)

                return true
            }
        }

        return false
    }

    private fun initDecoder(mediaFormat: MediaFormat) {
        videoDecoder = VideoDecoderSync()

        videoDecoder.prepare(mediaFormat, mediaExtractor)

        videoDecoder.onOutputBufferGenerate = { dataBuffer, bufferInfo ->
            videoEncoder.offerData(dataBuffer, bufferInfo)
        }
        videoDecoder.onDecodeFinish = {
            videoEncoder.queueEOS()
        }
    }

    private fun initEncoder(mediaFormat: MediaFormat) {
        videoEncoder = VideoEncoderSync()
        val mediaConfig = MediaConfig()
        mediaConfig.width = width ?: mediaFormat.getInteger(MediaFormat.KEY_WIDTH)
        mediaConfig.height = height ?: mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
        mediaConfig.duration = mediaFormat.getLong(MediaFormat.KEY_DURATION)
        mediaConfig.path = path
        videoEncoder.prepare(mediaConfig)

        videoEncoder.onSampleEncode = { dataBuffer, bufferInfo ->
            if (mediaMuxer == null) {
                mediaMuxer = MediaMuxer(dest, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxTrackIndex = mediaMuxer!!.addTrack(videoEncoder.getOutputFormat())
                mediaMuxer!!.start()
            }

            if (endMs != null && bufferInfo.presentationTimeUs > endMs!! * 1000) {
                videoDecoder.queueEOS()
                videoEncoder.queueEOS()
            } else {
                mediaMuxer?.writeSampleData(muxTrackIndex, dataBuffer, bufferInfo)
            }
        }
    }

    private fun loop() {
        startMs?.let {
            mediaExtractor.seekTo(it * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        }

        while (true) {
            videoDecoder.enqueueData()

            videoDecoder.pull()

            videoEncoder.pull()

            if (videoDecoder.isDecodeFinish && videoEncoder.isEncodeFinish) {
                release()
                break
            }
        }
    }

    fun release() {
        videoDecoder.release()
        videoEncoder.release()
        mediaExtractor.release()
        mediaMuxer?.release()
    }

}
package com.religion76.library.sync

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
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

    //it's should be support via use GLES on the way to encoder
    private fun withScale(width: Int, height: Int) {
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
            Log.d(TAG, "onOutputBufferGenerate")
            videoEncoder.offerData(dataBuffer, bufferInfo)
        }
        videoDecoder.onDecodeFinish = {
            Log.d(TAG, "onDecodeFinish")
            videoEncoder.queueEOS()
        }
    }

    private fun initEncoder(mediaFormat: MediaFormat) {
        videoEncoder = VideoEncoderSync()
        val mediaConfig = MediaConfig()
        if (width != null && height != null) {
            mediaConfig.originalWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH)
            mediaConfig.originalHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
            mediaConfig.width = width!!
            mediaConfig.height = height!!
        } else {
            mediaConfig.width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH)
            mediaConfig.height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
        }

        mediaConfig.duration = mediaFormat.getLong(MediaFormat.KEY_DURATION)
        mediaConfig.path = path
        videoEncoder.prepare(mediaConfig)

        videoEncoder.onSampleEncode = { dataBuffer, bufferInfo ->
            Log.d(TAG, "onSampleEncode")
            if (mediaMuxer == null) {
                initMediaMuxer()
            }

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

        // Set up the orientation and starting time for extractor.
//        val retriever = MediaMetadataRetriever()
//        retriever.setDataSource(path)
//        val degreesString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
//        if (degreesString != null) {
//            val degrees = Integer.parseInt(degreesString)
//            if (degrees >= 0) {
//                mediaMuxer!!.setOrientationHint(degrees)
//            }
//        }

        mediaMuxer!!.start()
    }


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

            videoDecoder.enqueueData()

            videoDecoder.pull()

            videoEncoder.pull()
        }
    }

    fun release() {
        videoDecoder.release()
        videoEncoder.release()
        mediaExtractor.release()
        mediaMuxer?.release()
    }

}
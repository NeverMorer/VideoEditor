package com.religion76.library.sync

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log

/**
 * Created by SunChao
 * on 2018/5/24.
 */
class VideoAudioCoder(private val path: String, private val dest: String) : Runnable {

    companion object {
        const val TAG = "VideoAudioCoder"
    }

    private lateinit var videoCoder: SeparateVideoCoder
    private lateinit var audioCoder: SeparateAudioCoder

    private lateinit var mediaMuxer: MediaMuxer

    private lateinit var mediaExtractor: MediaExtractor

    private var startMs: Long? = null
    private var endMs: Long? = null

    private var bitrate: Int? = null

    private var scaleWidth: Int? = null
    private var scaleHeight: Int? = null

    private var videoExtractTrackIndex = -1
    private var audioExtractTrackIndex = -1

    private var isVideoTrackSelect = false
    private var isAudioTrackSelect = false

    override fun run() {
        if (prepare()) {
            loop()
        }
    }

    fun withTrim(startMs: Long? = null, endMs: Long? = null) {
        this.startMs = startMs
        this.endMs = endMs
        Log.d(TAG, "setting trim start:$startMs  end:$endMs")
    }

    fun setVideoBitrate(bitrate: Int) {
        this.bitrate = bitrate
        Log.d(TAG, "setting bitrate:$bitrate")
    }

    private fun prepare(): Boolean {
        mediaMuxer = MediaMuxer(dest, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(path)

        for (i in 0..mediaExtractor.trackCount) {
            val trackFormat = mediaExtractor.getTrackFormat(i)
            val mimeType = trackFormat.getString(MediaFormat.KEY_MIME)
            if (mimeType.startsWith("video")) {
                videoExtractTrackIndex = i
                initVideoCoder(trackFormat)
            } else if (mimeType.startsWith("audio")) {
                audioExtractTrackIndex = i
                initAudioCoder(trackFormat)
            }
            if (videoExtractTrackIndex != -1 && audioExtractTrackIndex != -1) {
                break
            }
        }

//        mediaMuxer.start()

        return videoExtractTrackIndex != -1
    }

    private fun initVideoCoder(trackFormat: MediaFormat) {
        videoCoder = SeparateVideoCoder(path, mediaMuxer, mediaExtractor)
        videoCoder.withScale(scaleWidth, scaleHeight)
        videoCoder.withTrim(startMs, endMs)
        bitrate?.let {
            videoCoder.setBitrate(it)
        }
        videoCoder.prepare(trackFormat)
    }

    private fun initAudioCoder(trackFormat: MediaFormat) {
        audioCoder = SeparateAudioCoder(mediaMuxer, mediaExtractor)
        audioCoder.withTrim(startMs, endMs)
        audioCoder.prepare(trackFormat)
    }

    private fun loop() {
        while (true) {
//            if (videoCoder.isCoderDone && audioCoder.isCoderDone) {
//                release()
//                return
//            } else {
//                if (!isMuxerStart) {
//                    mediaMuxer.start()
//                    isMuxerStart = true
//                }
//                videoCoder.drain()
//                audioCoder.drain()
//            }
            if (!videoCoder.isCoderDone) {
                if (!isVideoTrackSelect) {
                    mediaExtractor.selectTrack(videoExtractTrackIndex)
                    startMs?.let {
                        mediaExtractor.seekTo(it * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    } ?: mediaExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                    isVideoTrackSelect = true
                }
                videoCoder.drain()

            } else if (audioExtractTrackIndex != -1 && !audioCoder.isCoderDone) {
                if (!isAudioTrackSelect) {
                    mediaExtractor.unselectTrack(videoExtractTrackIndex)
                    mediaExtractor.selectTrack(audioExtractTrackIndex)
                    startMs?.let {
                        mediaExtractor.seekTo(it * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    } ?: mediaExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                    isAudioTrackSelect = true
                }
                audioCoder.drain()
            } else {
                Log.d(TAG, "---------- ending ----------")
                release()
                break
            }
        }
    }

    fun release() {
        Log.d(TAG, "release")
        mediaMuxer.stop()
        mediaMuxer.release()
    }
}
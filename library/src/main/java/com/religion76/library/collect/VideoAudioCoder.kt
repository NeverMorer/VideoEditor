package com.religion76.library.collect

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.Looper
import com.religion76.library.AppLogger
import kotlin.Exception

/**
 * Created by SunChao
 * on 2018/5/24.
 */
class VideoAudioCoder(private val path: String, private val dest: String) : Runnable {

    companion object {
        const val TAG = "VideoAudioCoder_"
    }

    private lateinit var videoCoder: SeparateVideoCoder
    private lateinit var audioCoder: SeparateAudioWriter

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

    private var isRotate = false

    private var callback: ResultCallback? = null
    private var callbackHandler: Handler? = null

    private var isReleased = false

    var isSucceed = false
        private set(value) {
            field = value
        }

    override fun run() {
        if (prepare()) {
            loop()
            callback?.let {
                val handler = callbackHandler ?: Handler(Looper.getMainLooper())
                handler.post {
                    if (isSucceed) {
                        it.onSucceed()
                    } else {
                        it.onFailed("execute error...")
                        release()
                    }
                }
            }
        } else {
            callback?.let {
                val handler = callbackHandler ?: Handler(Looper.getMainLooper())
                handler.post {
                    it.onFailed("codec prepare error...")
                    release()
                }
            }
        }
    }

    //if handler not set, will post callback to UiThread
    fun setCallback(callback: ResultCallback, callbackHandler: Handler? = null) {
        this.callback = callback
        this.callbackHandler = callbackHandler
    }

    fun withTrim(startMs: Long? = null, endMs: Long? = null) {
        this.startMs = startMs
        this.endMs = endMs
        AppLogger.d(TAG, "setting trim start:$startMs  end:$endMs")
    }

    fun setVideoBitrate(bitrate: Int) {
        this.bitrate = bitrate
        AppLogger.d(TAG, "setting bitrate:$bitrate")
    }

    //it's should be support via use GLES on the way to encoder
    fun withScale(width: Int, height: Int) {
        scaleWidth = width
        scaleHeight = height
        AppLogger.d(TAG, "setting scale width:$width  height:$height")
    }

    fun setRotate(rotate: Boolean) {
        this.isRotate = rotate
    }

    private fun prepare(): Boolean {
        mediaMuxer = MediaMuxer(dest, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        mediaMuxer.setOrientationHint(90)

        mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(path)


        for (i in 0 until mediaExtractor.trackCount) {
            val trackFormat = mediaExtractor.getTrackFormat(i)
            val mimeType = trackFormat.getString(MediaFormat.KEY_MIME)
            if (mimeType.startsWith("video") && videoExtractTrackIndex < 0) {
                videoExtractTrackIndex = i
            } else if (mimeType.startsWith("audio") && audioExtractTrackIndex < 0) {
                audioExtractTrackIndex = i
            }

            AppLogger.d("ddd", "mediaFormat: $trackFormat")
        }

        if (videoExtractTrackIndex < 0) {
            return false
        }

        if (audioExtractTrackIndex >= 0) {
            val audioTrackFormat = mediaExtractor.getTrackFormat(audioExtractTrackIndex)
            initAudioCoder(audioTrackFormat)
        }

        val videoTrackFormat = mediaExtractor.getTrackFormat(videoExtractTrackIndex)
        if (!initVideoCoder(videoTrackFormat)) {
            return false
        }

        return true
    }

    private fun initVideoCoder(trackFormat: MediaFormat): Boolean {
        videoCoder = SeparateVideoCoder(path, mediaMuxer, mediaExtractor)
        videoCoder.withScale(scaleWidth, scaleHeight)
        videoCoder.withTrim(startMs, endMs)
        videoCoder.setRotate(isRotate)
        bitrate?.let {
            videoCoder.setBitrate(it)
        }
        return videoCoder.prepare(trackFormat)
    }

    private fun initAudioCoder(trackFormat: MediaFormat) {
        audioCoder = SeparateAudioWriter(mediaMuxer, mediaExtractor)
        audioCoder.withTrim(startMs, endMs)
        audioCoder.prepare(trackFormat)
    }

    private fun loop() {
        Thread {
            videoCoder.drainEncoder()
        }.start()

        while (true) {
            if (!videoCoder.isCoderDone) {
                if (!isVideoTrackSelect) {
                    mediaExtractor.selectTrack(videoExtractTrackIndex)
                    startMs?.let {
                        mediaExtractor.seekTo(it * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
//                        videoCoder.offset = mediaExtractor.sampleTime
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
//                        audioCoder.offset = mediaExtractor.sampleTime
                    } ?: mediaExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                    isAudioTrackSelect = true
                }
                audioCoder.drain()
            } else {
                AppLogger.d(TAG, "---------- ending ----------")
                release()
                isSucceed = true
                break
            }
        }
    }

    fun release() {
        if (!isReleased) {
            try {
                AppLogger.d(TAG, "release")
                mediaExtractor.release()
                mediaMuxer.stop()
                mediaMuxer.release()

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isReleased = true
            }
        }
    }

    interface ResultCallback {
        fun onSucceed()

        fun onFailed(errorMessage: String)
    }
}
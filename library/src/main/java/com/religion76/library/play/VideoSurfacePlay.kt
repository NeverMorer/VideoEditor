package com.religion76.library.play

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.religion76.library.AppLogger
import com.religion76.library.v2.MediaExecuteCallback
import kotlin.Exception

/**
 * Created by SunChao
 * on 2018/5/24.
 */
class VideoSurfacePlay(private val srcPath: String, private val surface: Surface) : Runnable {

    companion object {
        const val TAG = "VideoAudioCoder"
    }

    private lateinit var videoCoder: DecodeVideoToSurface

    private lateinit var mediaExtractor: MediaExtractor

    private var callback: ResultCallback? = null
    private var callbackHandler: Handler? = null

    private var isReleased = false

    private var videoExtractTrackIndex = -1

    override fun run() {
        if (prepare()) {

            videoCoder.setCallback(object : MediaExecuteCallback {
                override fun onMediaTrackReady() {

                }

                override fun onComplete() {
                    videoCoder.release()

                    callback?.let {
                        val handler = callbackHandler ?: Handler(Looper.getMainLooper())
                        handler.post {
                            it.onSucceed()
                        }
                    }

                    release()
                }

                override fun onError(t: Throwable) {
                    release()
                    callback?.let {
                        val handler = callbackHandler ?: Handler(Looper.getMainLooper())
                        handler.post {
                            it.onFailed("video execute failed...")
                        }
                    }
                }
            })

            executeVideo()

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

    fun startAsync(){
        Thread(this).start()
    }

    fun startSync(){
        run()
    }

    private fun executeVideo() {
        mediaExtractor.selectTrack(videoExtractTrackIndex)
        videoCoder.start()
    }


    private fun prepare(): Boolean {

        mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(srcPath)

        for (i in 0 until mediaExtractor.trackCount) {
            val trackFormat = mediaExtractor.getTrackFormat(i)
            val mimeType = trackFormat.getString(MediaFormat.KEY_MIME)
            if (mimeType.startsWith("video") && videoExtractTrackIndex < 0) {
                videoExtractTrackIndex = i
            }

            AppLogger.d(TAG, "mediaFormat: $trackFormat")
        }

        if (videoExtractTrackIndex < 0) {
            return false
        }

        val videoTrackFormat = mediaExtractor.getTrackFormat(videoExtractTrackIndex)
        if (!initVideoCoder(videoTrackFormat)) {
            return false
        }


        return true
    }

    private fun initVideoCoder(trackFormat: MediaFormat): Boolean {
        videoCoder = DecodeVideoToSurface(srcPath, mediaExtractor)
        return videoCoder.prepare(trackFormat, surface)
    }

    fun release() {
        if (!isReleased) {
            try {
                AppLogger.d(TAG, "release")
                mediaExtractor.release()

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
package com.religion76.library.executor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.nio.ByteBuffer

/**
 * Created by SunChao
 * on 2018/5/11.
 */
class VideoFrameExtractor(videoPath: String) {

    companion object {
        const val DEFAULT_BUFFER_SIZE: Int = 8 * 1024
    }

    private var mediaExtractor: MediaExtractor


    private lateinit var buffer: ByteBuffer

    init {
        if (!videoPath.endsWith(".mp4")) {
            throw IllegalStateException("video file is not support")
        }
        mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(videoPath)
        for (i in 0 until mediaExtractor.trackCount) {
            val trackFormat = mediaExtractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
            if (mime.startsWith("video/")) {
                mediaExtractor.selectTrack(i)
                if (trackFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    val bufferSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    buffer = ByteBuffer.allocate(if (bufferSize > 0) bufferSize else DEFAULT_BUFFER_SIZE)
                }
                break
            }
        }
    }

    private fun seekTo(ms: Long) {
        mediaExtractor.seekTo(ms * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
    }

    fun getFrame(ms: Long): Bitmap? {
        seekTo(ms)
        val dataSize = mediaExtractor.readSampleData(buffer, 0)
        if (dataSize > 0) {
            return BitmapFactory.decodeByteArray(buffer.array(), 0, dataSize)
        }
        return null
    }

    fun getFrameAsync(ms: Long, onFrameGet: ((bitmap: Bitmap) -> Unit)) {
        Observable.create<Bitmap> {
            seekTo(ms)
            val dataSize = mediaExtractor.readSampleData(buffer, 0)
            if (dataSize > 0) {
                it.onNext(BitmapFactory.decodeByteArray(buffer.array(), 0, dataSize))
            }
            it.onError(Throwable("get bitmap failed"))
            it.onComplete()
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    onFrameGet.invoke(it)
                }
    }



}
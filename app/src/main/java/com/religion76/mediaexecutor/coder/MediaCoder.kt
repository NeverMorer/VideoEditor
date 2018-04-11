package com.religion76.mediaexecutor.coder

import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.reactivex.schedulers.Schedulers
import java.io.File

/**
 * Created by SunChao
 * on 2018/3/3.
 */
class MediaCoder {

    companion object {
        val TAG = "MediaCoder"
    }

    private val videoEncoder = VideoEncoder()
    private val videoDecoder = VideoDecoder()

    private lateinit var filePath: String

    private var trackIndex: Int = -1

    private var isMuxerReady = false

    private val handler = Handler(Looper.myLooper())

    val FILE_PATH = Environment.getExternalStorageDirectory().absolutePath.plus(File.separator).plus(System.currentTimeMillis().toString() + "hhhh.mp4")

    private val muxer = MediaMuxer(FILE_PATH, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    fun start(path: String) {
        filePath = path

        videoDecoder.onOutputBufferGenerate = { buffer, bufferInfo ->
            Log.d(TAG, "videoDecoder onOutputBufferGenerate")
            videoEncoder.offerData(buffer, bufferInfo)
        }

        videoDecoder.onSampleFormatConfirmed = { mediaFormat ->
            handler.post {
                val mediaConfig = MediaConfig()
                mediaConfig.width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH)
                mediaConfig.height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
                videoEncoder.prepare(mediaConfig)
            }
        }

        videoEncoder.onSampleEncode = { byteBuffer, bufferInfo ->
            Log.d(TAG, "videoEncoder onSampleEncode  trackIndex$trackIndex")
            if (!isMuxerReady){
                trackIndex = muxer.addTrack(videoEncoder.getOutputFormat())
                muxer.start()
                isMuxerReady = true
            }
            muxer.writeSampleData(trackIndex,  byteBuffer, bufferInfo)
        }

        videoEncoder.onEncoderCompleted = {
            Log.d(TAG, "videoEncoder onEncoderCompleted")
            release()
        }

        videoEncoder.onOutputFormatChanged = {
            Log.d(TAG, "encoder output format" + it)
        }

        videoDecoder.decode(filePath)
    }

    fun release() {
        muxer.stop()
        muxer.release()
        videoDecoder.release()
        videoEncoder.release()
//        muxer.release()
    }

}
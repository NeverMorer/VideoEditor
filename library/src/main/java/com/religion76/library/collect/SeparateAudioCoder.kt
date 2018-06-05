package com.religion76.library.collect

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.religion76.library.AppLogger
import com.religion76.library.sync.VideoDecoderSync

/**
 * Created by SunChao
 * on 2018/5/24.
 */
class SeparateAudioCoder(private val mediaMuxer: MediaMuxer, private val mediaExtractor: MediaExtractor) {

    companion object {
        const val TAG = "AudioCoderSync"
    }

    private lateinit var audioEncoder: AudioEncoderSync

    private lateinit var videoDecoder: VideoDecoderSync

    private var muxTrackIndex: Int = -1

    private var startMs: Long? = null
    private var endMs: Long? = null

    fun withTrim(startMs: Long? = null, endMs: Long? = null) {
        this.startMs = startMs
        this.endMs = endMs
    }

    fun prepare(trackFormat:MediaFormat) {
        //Encoder must be init first
        initEncoder(trackFormat)
        initDecoder(trackFormat)

        //audio add muxer track here because this mediaFormat form MediaExtractor include csd-0
        muxTrackIndex = mediaMuxer.addTrack(audioEncoder.getOutputFormat())
    }

    private fun initDecoder(mediaFormat: MediaFormat) {
        videoDecoder = VideoDecoderSync()

        videoDecoder.prepare(mediaFormat, mediaExtractor)

        videoDecoder.onOutputBufferGenerate = { dataBuffer, bufferInfo ->
            AppLogger.d(TAG, "onOutputBufferGenerate")
            AppLogger.d(TAG, "decode_presentationTimeUs: ${bufferInfo.presentationTimeUs}")
            audioEncoder.offerData(dataBuffer, bufferInfo)
        }
        videoDecoder.onDecodeFinish = {
            AppLogger.d(TAG, "onDecodeFinish")
            audioEncoder.queueEOS()
        }
    }

    private fun initEncoder(mediaFormat: MediaFormat) {

        audioEncoder = AudioEncoderSync()

        audioEncoder.prepare(mediaFormat)

        audioEncoder.onSampleEncode = { dataBuffer, bufferInfo ->
            AppLogger.d(TAG, "onSampleEncode")

            AppLogger.d(TAG, "encode_presentationTimeUs: ${bufferInfo.presentationTimeUs}")

            if (endMs != null && bufferInfo.presentationTimeUs > endMs!! * 1000 && !videoDecoder.isDecodeFinish) {
                AppLogger.d(TAG, "------------- end trim ------------")
                videoDecoder.queueEOS()
                audioEncoder.queueEOS()
            } else {
                mediaMuxer.writeSampleData(muxTrackIndex, dataBuffer, bufferInfo)
            }
        }
    }

    var isCoderDone = false

    fun drain() {
        if (!isCoderDone) {
            if (videoDecoder.isDecodeFinish && audioEncoder.isEncodeFinish) {
                isCoderDone = true
                release()
            } else {
                videoDecoder.enqueueData()

                videoDecoder.pull()

                audioEncoder.pull()

                if (audioEncoder.isEOSNeed) {
                    audioEncoder.signEOS()
                }
            }
        }
    }

    fun release() {
        videoDecoder.release()
        audioEncoder.release()
    }

}
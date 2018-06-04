package com.religion76.library.sync

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log

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

//        muxTrackIndex = mediaMuxer.addTrack(audioEncoder.getOutputFormat())
        Log.d("zzz", "audio mux track index:$muxTrackIndex")
    }

    private fun initDecoder(mediaFormat: MediaFormat) {
        videoDecoder = VideoDecoderSync()

        videoDecoder.prepare(mediaFormat, mediaExtractor)

        videoDecoder.onOutputBufferGenerate = { dataBuffer, bufferInfo ->
            Log.d(TAG, "onOutputBufferGenerate")
            Log.d(TAG, "decode_presentationTimeUs: ${bufferInfo.presentationTimeUs}")
            audioEncoder.offerData(dataBuffer, bufferInfo)
        }
        videoDecoder.onDecodeFinish = {
            Log.d(TAG, "onDecodeFinish")
            audioEncoder.queueEOS()
        }
    }

    private fun initEncoder(mediaFormat: MediaFormat) {

        audioEncoder = AudioEncoderSync()

        audioEncoder.prepare(mediaFormat)

        audioEncoder.onSampleEncode = { dataBuffer, bufferInfo ->
            Log.d(TAG, "onSampleEncode")
//            if (muxTrackIndex == -1) {
//                muxTrackIndex = mediaMuxer.addTrack(audioEncoder.getOutputFormat())
//                isMuxTrackAdded = true
//            }

            Log.d(TAG, "encode_presentationTimeUs: ${bufferInfo.presentationTimeUs}")

            if (endMs != null && bufferInfo.presentationTimeUs > endMs!! * 1000 && !videoDecoder.isDecodeFinish) {
                Log.d(TAG, "------------- end trim ------------")
                videoDecoder.queueEOS()
                audioEncoder.queueEOS()
            } else {
//                mediaMuxer.writeSampleData(muxTrackIndex, dataBuffer, bufferInfo)
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
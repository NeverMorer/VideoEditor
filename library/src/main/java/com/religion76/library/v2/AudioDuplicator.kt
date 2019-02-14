package com.religion76.library.v2

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.religion76.library.AppLogger
import java.nio.ByteBuffer

/**
 * Created by SunChao
 * on 2018/5/24.
 */
class AudioDuplicator(private val mediaMuxer: MediaMuxer, private val mediaExtractor: MediaExtractor) {

    companion object {
        const val TAG = "SeparateAudioWriter"
        const val MAX_SAMPLE_SIZE = 1024 * 256
    }

    private var muxTrackIndex: Int = -1

    private var startMs: Long? = null
    private var endMs: Long? = null

    private lateinit var dataBuffer: ByteBuffer


    fun withTrim(startMs: Long? = null, endMs: Long? = null) {
        this.startMs = startMs
        this.endMs = endMs
    }

    fun prepare(trackFormat: MediaFormat) {

        //Encoder must be init first

        //audio add muxer track here because this mediaFormat form MediaExtractor include csd-0
        muxTrackIndex = mediaMuxer.addTrack(trackFormat)

        dataBuffer = ByteBuffer.allocate(MAX_SAMPLE_SIZE)
    }

    var isCoderDone = false

    val bufferInfo = MediaCodec.BufferInfo()

    fun copyAudio() {
        while (!isCoderDone) {

            bufferInfo.size = mediaExtractor.readSampleData(dataBuffer, 0)

            if (bufferInfo.size > 0) {
                AppLogger.d(TAG, "write audio data to file")
                bufferInfo.offset = 0
                bufferInfo.presentationTimeUs = mediaExtractor.sampleTime
                bufferInfo.flags = mediaExtractor.sampleFlags

                if (startMs != null) {
                    val startTimeUs = startMs!! * 1000
                    if (bufferInfo.presentationTimeUs >= startTimeUs) {
                        bufferInfo.presentationTimeUs = bufferInfo.presentationTimeUs - startTimeUs
                        mediaMuxer.writeSampleData(muxTrackIndex, dataBuffer, bufferInfo)
                    }
                } else {
                    mediaMuxer.writeSampleData(muxTrackIndex, dataBuffer, bufferInfo)
                }

                if (mediaExtractor.advance()) {
                    if (endMs != null && mediaExtractor.sampleTime > endMs!! * 1000) {
                        isCoderDone = true
                    }
                } else {
                    isCoderDone = true
                }
            } else {
                isCoderDone = true
            }
        }
    }

}
package com.religion76.library.extractor

/**
 * Created by SunChao
 * on 2018/5/23.
 */

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.support.annotation.RequiresApi
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Created by SunChao
 * on 2018/3/3.
 */

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class ExtractFrameDecoder {

    companion object {
        const val TAG = "MediaCoder_Decoder"
        const val DEFAULT_QUEUE_TIMEOUT = 100000L
        const val FAKE_SAMPLE_TIME = 999999999999L
        const val FAKE_SAMPLE_NUM_PER = 4
    }

    private lateinit var extractor: MediaExtractor

    private lateinit var decoder: MediaCodec

    private var isDecodeFinish = false

    private var isAuto = true

    private var isRender = false

    private var needLoop = false

    private fun getInputBuffer(index: Int): ByteBuffer {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            decoder.inputBuffers[index]
        } else {
            decoder.getInputBuffer(index)
        }
    }

    private fun getOutBuffer(index: Int): ByteBuffer {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            outputBuffers[index]
        } else {
            decoder.getOutputBuffer(index)
        }
    }

    private lateinit var outputBuffers: Array<ByteBuffer>

    var onOutputBufferGenerate: ((outputBuffer: ByteBuffer?, bufferInfo: MediaCodec.BufferInfo) -> Unit)? = null

    var onDecodeFinish: (() -> Unit)? = null

    private fun configure(mediaFormat: MediaFormat, surface: Surface? = null) {
        Log.d(TAG, "on decoder configured $mediaFormat")
        decoder = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME))
        decoder.configure(mediaFormat, surface, null, 0)
        decoder.start()
    }

    var onSampleFormatConfirmed: ((MediaFormat) -> Unit)? = null

    fun queueEOS() {
        if (!isDecodeFinish) {
            Log.d(TAG, "------------- decoder queueEOS ------------")
            val inputBufferIndex = decoder.dequeueInputBuffer(DEFAULT_QUEUE_TIMEOUT)
            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            isDecodeFinish = true
        }
    }

    fun decode(path: String, surface: Surface? = null, startMs: Long? = null, auto: Boolean = true) {

        extractor = MediaExtractor()
        extractor.setDataSource(path)

        isAuto = auto
        isRender = surface != null

        for (i in 0 until extractor.trackCount) {
            val sampleMediaFormat = extractor.getTrackFormat(i)
            if (sampleMediaFormat.getString(MediaFormat.KEY_MIME).startsWith("video")) {
                Log.d(TAG, "decode format:$sampleMediaFormat")
                extractor.selectTrack(i)

                startMs?.let {
                    Log.d(TAG, "startS:${it * 1000}")
                    extractor.seekTo(it * 1000, MediaExtractor.SEEK_TO_NEXT_SYNC)
                    Log.d(TAG, "seekTo:${extractor.sampleTime}")
                }

                configure(sampleMediaFormat, surface)

                onSampleFormatConfirmed?.invoke(sampleMediaFormat)

                startDecode()

                break
            }
        }
    }


    private fun startDecode() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            outputBuffers = decoder.outputBuffers
        }

        while (!isDecodeFinish) {

            while (!needLoop) {
                Thread.sleep(50)
            }

            if (!isAuto && seekTime != null) {
                extractor.seekTo(seekTime!!, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                Log.d(TAG, "sampleTime: ${extractor.sampleTime}")
                offerData(true)
                seekTime = null
//                extractor.advance()
            }

            if (needQueueFakeSampleCount > 0) {
                offerFakeData2()
            }

            val bufferInfo = MediaCodec.BufferInfo()

            val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, DEFAULT_QUEUE_TIMEOUT)

            when (outputBufferIndex) {
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    Log.d(TAG, "decoder output INFO_OUTPUT_BUFFERS_CHANGED")
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        outputBuffers = decoder.outputBuffers
                    }
                }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    Log.d(TAG, "decoder output INFO_OUTPUT_FORMAT_CHANGED")
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    Log.d(TAG, "decoder output INFO_TRY_AGAIN_LATER")
                }
                else -> {
                    Log.d(TAG, "decoder output generate sample data")
                    if (bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0 && bufferInfo.size > 0) {
                        Log.d(TAG, "decode sample time:${bufferInfo.presentationTimeUs}")
                        if (bufferInfo.presentationTimeUs != FAKE_SAMPLE_TIME) {
                            Log.d(TAG, "decode sample time:${bufferInfo.presentationTimeUs}")
                            Log.d(TAG, "decode buffer size:${bufferInfo.size}")
                            Log.d(TAG, "decode buffer index:$outputBufferIndex")
                            Log.d(TAG, "decode buffer flag:${bufferInfo.flags}")
                            needLoop = false

//                            val outBuffer = getOutBuffer(outputBufferIndex)
                            decoder.releaseOutputBuffer(outputBufferIndex, isRender)
                            Log.d(TAG, "------------------ real frame generate ------------------")
                            onOutputBufferGenerate?.invoke(null, bufferInfo)
                        } else {
                            decoder.releaseOutputBuffer(outputBufferIndex, false)
                        }
                    }
                }
            }

            if (bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                isDecodeFinish = true
                onDecodeFinish?.invoke()
                break
            }
        }
    }

    @Transient
    private var seekTime: Long? = null

    fun seekTo(us: Long) {
        if (isAuto) {
            Log.e(TAG, "current state is AUTO ,can't preform seekTo")
        } else {
            Log.d(TAG, "seeking: $us")

            seekTime = us
            needLoop = true
        }
    }

    private fun offerData(needFakeFill: Boolean = false): Boolean {
        val inputBufferIndex = decoder.dequeueInputBuffer(DEFAULT_QUEUE_TIMEOUT)
        //double check isDecodeFinish because last code is block
        Log.d(TAG, "bufferIndex: $inputBufferIndex")
        if (inputBufferIndex >= 0 && !isDecodeFinish) {
            val inputBuffer = getInputBuffer(inputBufferIndex)
            Log.d(TAG, "inputBuffer before read data: $inputBuffer")
            val sampleSize = extractor.readSampleData(inputBuffer, 0)
            Log.d(TAG, "inputBuffer after read data: $inputBuffer")
            return if (sampleSize < 0) {
                Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM")
                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                true
            } else {
                //here to filter sample data by limit duration
                Log.d(TAG, "queueInputBuffer")
                Log.d(TAG, "sampleSize: $sampleSize")
                Log.d(TAG, "sampleTime: ${extractor.sampleTime}")
                Log.d(TAG, "sampleFlags: ${extractor.sampleFlags}")

                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)

                if (needFakeFill) {
                    needQueueFakeSampleCount += FAKE_SAMPLE_NUM_PER
                }

                if (isAuto) {
                    extractor.advance()
                }

                false
            }
        } else {
            return false
        }
    }

    //fill the input buffers, ensure single frame decode can be work
    private fun offerFakeData() {
        for (i in 0..5) {
            val inputBufferIndex = decoder.dequeueInputBuffer(DEFAULT_QUEUE_TIMEOUT)
            //double check isDecodeFinish because last code is block
            getInputBuffer(inputBufferIndex).putInt(321)
            Log.d(TAG, "fake buffer index: $inputBufferIndex")
            if (inputBufferIndex >= 0 && !isDecodeFinish) {
                decoder.queueInputBuffer(inputBufferIndex, 0, 1, 1, 0)
            }
        }
    }

    private var needQueueFakeSampleCount = 0

    //todo optimize
    private fun offerFakeData2() {
        for (i in 0..FAKE_SAMPLE_NUM_PER) {
            val inputBufferIndex = decoder.dequeueInputBuffer(DEFAULT_QUEUE_TIMEOUT)
            //double check isDecodeFinish because last code is block
            if (inputBufferIndex >= 0 && !isDecodeFinish) {
                val inputBuffer = getInputBuffer(inputBufferIndex)
                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                if (sampleSize < 0) {
                    Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM")
                    decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                } else {
                    //here to filter sample data by limit duration
                    Log.d(TAG, "bufferIndex: $inputBufferIndex")
                    Log.d(TAG, "sampleSize: $sampleSize")
                    Log.d(TAG, "sampleTime: ${extractor.sampleTime}")
                    Log.d(TAG, "sampleFlags: ${extractor.sampleFlags}")
                    decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, FAKE_SAMPLE_TIME, 0)
                    needQueueFakeSampleCount--
                }
            }
        }
    }

    fun release() {
        extractor.release()
        decoder.release()
    }

}
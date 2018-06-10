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
import android.view.Surface
import com.religion76.library.AppLogger
import java.nio.ByteBuffer


@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class ExtractFrameDecoder {

    companion object {
        const val TAG = "MediaCoder_Decoder"
        const val DEFAULT_QUEUE_TIMEOUT = 10000L
        const val FAKE_SAMPLE_TIME = 999999999999L
        const val FAKE_SAMPLE_NUM_PER = 4
    }

    private lateinit var extractor: MediaExtractor

    private lateinit var decoder: MediaCodec

    private var isAuto = true

    private var isRender = false

    @Volatile
    private var needLoop = false

    private var isDecodeFinish = false

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
        AppLogger.d(TAG, "on decoder configured $mediaFormat")
        decoder = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME))
        decoder.configure(mediaFormat, surface, null, 0)
        decoder.start()
    }

    var onSampleFormatConfirmed: ((MediaFormat) -> Unit)? = null

    fun queueEOS() {
        if (!isDecodeFinish) {
            if (!needLoop) {
                AppLogger.d(TAG, "------------- decoder queueEOS ------------")
                val inputBufferIndex = decoder.dequeueInputBuffer(DEFAULT_QUEUE_TIMEOUT)
                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } else {
                isDecodeFinish = true
            }
            needLoop = true
        }
    }

    fun prepare(path: String, surface: Surface? = null, startMs: Long? = null, auto: Boolean = true): Boolean {
        extractor = MediaExtractor()
        extractor.setDataSource(path)

        isAuto = auto
        isRender = surface != null

        for (i in 0 until extractor.trackCount) {
            val sampleMediaFormat = extractor.getTrackFormat(i)
            if (sampleMediaFormat.getString(MediaFormat.KEY_MIME).startsWith("video")) {
                AppLogger.d(TAG, "decode format:$sampleMediaFormat")
                extractor.selectTrack(i)

                startMs?.let {
                    AppLogger.d(TAG, "startS:${it * 1000}")
                    extractor.seekTo(it * 1000, MediaExtractor.SEEK_TO_NEXT_SYNC)
                    AppLogger.d(TAG, "seekTo:${extractor.sampleTime}")
                }

                try {
                    configure(sampleMediaFormat, surface)
                } catch (e: Exception) {
                    e.printStackTrace()
                    return false
                }

                onSampleFormatConfirmed?.invoke(sampleMediaFormat)
                return true
            }
        }

        return false
    }

    fun decode(path: String, surface: Surface? = null, startMs: Long? = null, auto: Boolean = true) {

        extractor = MediaExtractor()
        extractor.setDataSource(path)

        isAuto = auto
        isRender = surface != null

        for (i in 0 until extractor.trackCount) {
            val sampleMediaFormat = extractor.getTrackFormat(i)
            if (sampleMediaFormat.getString(MediaFormat.KEY_MIME).startsWith("video")) {
                AppLogger.d(TAG, "decode format:$sampleMediaFormat")
                extractor.selectTrack(i)

                startMs?.let {
                    AppLogger.d(TAG, "startS:${it * 1000}")
                    extractor.seekTo(it * 1000, MediaExtractor.SEEK_TO_NEXT_SYNC)
                    AppLogger.d(TAG, "seekTo:${extractor.sampleTime}")
                }

                configure(sampleMediaFormat, surface)

                onSampleFormatConfirmed?.invoke(sampleMediaFormat)

                startDecode()

                break
            }
        }
    }


    fun startDecode() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            outputBuffers = decoder.outputBuffers
        }

        while (!isDecodeFinish) {

            while (!needLoop) {
                Thread.sleep(50)
            }

            if (!isAuto && seekTime != null) {
                extractor.seekTo(seekTime!!, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                AppLogger.d(TAG, "sampleTime: ${extractor.sampleTime}")
                if (offerData(true)) {
                    seekTime = null
//                    offerFakeData2()
                }
//                extractor.advance()
            }


            val bufferInfo = MediaCodec.BufferInfo()

            val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, DEFAULT_QUEUE_TIMEOUT)

            when (outputBufferIndex) {
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    AppLogger.d(TAG, "decoder output INFO_OUTPUT_BUFFERS_CHANGED")
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        outputBuffers = decoder.outputBuffers
                    }
                }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    AppLogger.d(TAG, "decoder output INFO_OUTPUT_FORMAT_CHANGED")
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    AppLogger.d(TAG, "decoder output INFO_TRY_AGAIN_LATER")
                    if (needLoop) {
                        offerFakeData2()
                    }
                }
                else -> {
                    AppLogger.d(TAG, "decoder output generate sample data")
                    if (bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0 && bufferInfo.size > 0) {
                        AppLogger.d(TAG, "decode sample time:${bufferInfo.presentationTimeUs}")
                        if (bufferInfo.presentationTimeUs != FAKE_SAMPLE_TIME) {
                            AppLogger.d(TAG, "decode sample time:${bufferInfo.presentationTimeUs}")
                            AppLogger.d(TAG, "decode buffer size:${bufferInfo.size}")
                            AppLogger.d(TAG, "decode buffer index:$outputBufferIndex")
                            AppLogger.d(TAG, "decode buffer flag:${bufferInfo.flags}")
                            AppLogger.d(TAG, "------------------ real frame generate ------------------")
                            decoder.releaseOutputBuffer(outputBufferIndex, isRender)
                            needLoop = false
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

        release()
    }

    @Volatile
    private var seekTime: Long? = null

    fun seekTo(us: Long) {
        if (isAuto) {
            AppLogger.e(TAG, "current state is AUTO ,can't preform seekTo")
        } else {
            AppLogger.d(TAG, "seeking: $us")

            seekTime = us
            needLoop = true
        }
    }

    private fun offerData(needFakeFill: Boolean = false): Boolean {
        val inputBufferIndex = decoder.dequeueInputBuffer(DEFAULT_QUEUE_TIMEOUT)
        //double check isDecodeFinish because last code is block
        AppLogger.d(TAG, "bufferIndex: $inputBufferIndex")
        if (inputBufferIndex >= 0 && !isDecodeFinish) {
            val inputBuffer = getInputBuffer(inputBufferIndex)
            AppLogger.d(TAG, "inputBuffer before read data: $inputBuffer")
            val sampleSize = extractor.readSampleData(inputBuffer, 0)
            AppLogger.d(TAG, "inputBuffer after read data: $inputBuffer")
            if (sampleSize < 0) {
                AppLogger.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM")
                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } else {
                //here to filter sample data by limit duration
                AppLogger.d(TAG, "queueInputBuffer")
                AppLogger.d(TAG, "sampleSize: $sampleSize")
                AppLogger.d(TAG, "sampleTime: ${extractor.sampleTime}")
                AppLogger.d(TAG, "sampleFlags: ${extractor.sampleFlags}")

                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)

                if (needFakeFill) {
                    needQueueFakeSampleCount += FAKE_SAMPLE_NUM_PER
                }

                if (isAuto) {
                    extractor.advance()
                }

                return true

            }
        }

        return false
    }

    //fill the input buffers, ensure single frame decode can be work
    private fun offerFakeData() {
        for (i in 0..5) {
            val inputBufferIndex = decoder.dequeueInputBuffer(DEFAULT_QUEUE_TIMEOUT)
            //double check isDecodeFinish because last code is block
            getInputBuffer(inputBufferIndex).putInt(321)
            AppLogger.d(TAG, "fake buffer index: $inputBufferIndex")
            if (inputBufferIndex >= 0 && !isDecodeFinish) {
                decoder.queueInputBuffer(inputBufferIndex, 0, 1, 1, 0)
            }
        }
    }

    private var needQueueFakeSampleCount = 0

    //todo optimize
    private fun offerFakeData2() {
        val inputBufferIndex = decoder.dequeueInputBuffer(DEFAULT_QUEUE_TIMEOUT)
        //double check isDecodeFinish because last code is block
        if (inputBufferIndex >= 0 && !isDecodeFinish) {
            val inputBuffer = getInputBuffer(inputBufferIndex)
            val sampleSize = extractor.readSampleData(inputBuffer, 0)

            if (sampleSize < 0) {
                AppLogger.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM")
                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } else {
                AppLogger.d(TAG, "-------queue fake data-------")
                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, FAKE_SAMPLE_TIME, 0)
                needQueueFakeSampleCount--
            }
        }
    }

    fun release() {
        AppLogger.d(TAG, "---- release ----")
        extractor.release()
        decoder.release()
    }

}
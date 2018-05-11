package com.religion76.mediaexecutor.coder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.experimental.async
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Created by SunChao
 * on 2018/3/3.
 */
class VideoDecoder {

    companion object {
        private val TAG = "MediaCoder_Decoder"
    }


    private val DEFAULT_QUEUE_TIMEOUT = 10000L

    private val extractor: MediaExtractor by lazy {
        MediaExtractor()
    }

    @Volatile
    private var isDecodeFinish = false

    private lateinit var decoder: MediaCodec

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

    var onOutputBufferGenerate: ((outputBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) -> Unit)? = null

    var onDecodeFinish: (() -> Unit)? = null

    private fun configure(mediaFormat: MediaFormat) {
        Log.d(TAG, "on decoder configured $mediaFormat")
        decoder = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME))
        decoder.configure(mediaFormat, null, null, 0)
        decoder.start()
    }

    var onSampleFormatConfirmed: ((MediaFormat) -> Unit)? = null

    fun queueEOS() {
        if (!isDecodeFinish) {
            Log.d(TAG, "------------- decoder queueEOS ------------")
            val inputBufferIndex = decoder.dequeueInputBuffer(DEFAULT_QUEUE_TIMEOUT)
            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
    }

    fun decode(path: String, startMs: Long? = null) {

        extractor.setDataSource(path)

        inputDisposable = Observable.range(0, extractor.trackCount)
                .filter { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).startsWith("video") }
                .flatMap {
                    val sampleMediaFormat = extractor.getTrackFormat(it)
                    Log.d(TAG, "decode format:$sampleMediaFormat")
                    extractor.selectTrack(it)

                    startMs?.let {
                        extractor.seekTo(it * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    }

                    configure(sampleMediaFormat)
                    onSampleFormatConfirmed?.invoke(sampleMediaFormat)

                    startDecode()

                    Observable.interval(50, TimeUnit.MILLISECONDS)
                            .map {
                                if (!isDecodeFinish) {
                                    val inputBufferIndex = decoder.dequeueInputBuffer(DEFAULT_QUEUE_TIMEOUT)
                                    //double check isDecodeFinish because last code is block
                                    if (inputBufferIndex >= 0 && !isDecodeFinish) {
                                        val inputBuffer = getInputBuffer(inputBufferIndex)
                                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                        if (sampleSize < 0) {
                                            Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM")
                                            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                            true
                                        } else {
                                            //here to filter sample data by limit duration
                                            Log.d(TAG, "InputBuffer queueInputBuffer")
                                            decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                                            extractor.advance()
                                            false
                                        }
                                    } else {
                                        false
                                    }
                                } else {
                                    true
                                }

                            }
                }
                .subscribeOn(Schedulers.computation())
                .subscribe({
                    if (it) {
                        inputDisposable.dispose()
                    }
                }, { t: Throwable? ->
                    t?.printStackTrace()
                })


    }


    private fun startDecode() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            outputBuffers = decoder.outputBuffers
        }

        outputDisposable = Observable.interval(50, TimeUnit.MILLISECONDS)
                .map {
                    if (isDecodeFinish) {
                        true
                    } else {
                        val bufferInfo = MediaCodec.BufferInfo()
                        val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, DEFAULT_QUEUE_TIMEOUT)

                        //double check isDecodeFinish because last code is block
                        when (outputBufferIndex) {
                            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                                Log.d(TAG, "encoder output INFO_OUTPUT_BUFFERS_CHANGED")
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                                    outputBuffers = decoder.outputBuffers
                                }
                            }
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                Log.d(TAG, "encoder output INFO_OUTPUT_FORMAT_CHANGED")
                            }
                            MediaCodec.INFO_TRY_AGAIN_LATER -> {
                                Log.d(TAG, "encoder output INFO_TRY_AGAIN_LATER")
                            }
                            else -> {
                                Log.d(TAG, "encoder output generate sample data")
                                if (bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                                    val outBuffer = getOutBuffer(outputBufferIndex)
                                    onOutputBufferGenerate?.invoke(outBuffer, bufferInfo)
                                    decoder.releaseOutputBuffer(outputBufferIndex, false)
                                }
                            }
                        }

                        bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    }
                }
                .subscribeOn(Schedulers.computation())
                .subscribe({
                    if (it) {
                        isDecodeFinish = true
                        onDecodeFinish?.invoke()
                        outputDisposable.dispose()
                    }
                }, { t: Throwable? ->
                    t?.printStackTrace()
                })
    }


    private lateinit var inputDisposable: Disposable
    private lateinit var outputDisposable: Disposable

    fun release() {
        extractor.release()
        decoder.release()
    }

}
package com.religion76.library.sync

import android.graphics.SurfaceTexture
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.GLES20
import android.os.Handler
import android.util.Log
import android.view.Surface
import com.religion76.library.coder.MediaConfig
import com.religion76.library.gles.EglCore
import com.religion76.library.gles.FullFrameRect
import com.religion76.library.gles.Texture2dProgram
import com.religion76.library.gles.WindowSurface
import java.util.*

/**
 * Created by SunChao
 * on 2018/5/24.
 */
class VideoCoderSync2(private val path: String, private val dest: String) : Runnable, SurfaceTexture.OnFrameAvailableListener {

    companion object {
        const val TAG = "VideoCoderSync2"
    }

    private lateinit var videoEncoder: VideoEncoderSync2

    private lateinit var videoDecoder: VideoDecoderSync2

    private lateinit var mediaExtractor: MediaExtractor

    private lateinit var surfaceTexture: SurfaceTexture

    private lateinit var encodeSurface: WindowSurface

    private var textureId: Int? = null

    private lateinit var fullFrameRect: FullFrameRect

    private lateinit var mediaInfo: MediaInfo

    private lateinit var eglCore: EglCore

    private val tmpMatrix = FloatArray(16)

    private var mediaMuxer: MediaMuxer? = null

    private var muxTrackIndex: Int = -1

    private var startMs: Long? = null
    private var endMs: Long? = null

    private var bitrate: Int? = null

    private lateinit var handler: Handler

    override fun run() {

        if (prepare()) {
            loop()
        }
    }

    //it's should be support via use GLES on the way to encoder
    private fun withScale(width: Int, height: Int) {

    }

    fun withTrim(startMs: Long? = null, endMs: Long? = null) {
        this.startMs = startMs
        this.endMs = endMs
    }

    fun setBitrate(bitrate: Int) {
        this.bitrate = bitrate
    }

    private fun prepare(): Boolean {

        mediaInfo = MediaInfo.getMediaInfo(path)

        mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(path)

        for (i in 0..mediaExtractor.trackCount) {
            val trackFormat = mediaExtractor.getTrackFormat(i)
            if (trackFormat.getString(MediaFormat.KEY_MIME).startsWith("video")) {
                mediaExtractor.selectTrack(i)

                //Encoder must be init first
                initEncoder(trackFormat)
                initDecoder(trackFormat)

                return true
            }
        }

        return false
    }

    private fun initTexture() {

        eglCore = EglCore(null, EglCore.FLAG_RECORDABLE)

        encodeSurface = WindowSurface(eglCore, videoEncoder.surface, true)

        encodeSurface.makeCurrent()

        fullFrameRect = FullFrameRect(Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT))
        textureId = fullFrameRect.createTextureObject()

        surfaceTexture = SurfaceTexture(textureId!!)
        surfaceTexture.setOnFrameAvailableListener(this)
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        Log.d(TAG, "onFrameAvailable")
        isNewFrameAvailable = true
    }

    @Transient
    private var isNewFrameAvailable: Boolean = false

    private var bufferTimeQueue: Queue<Long> = ArrayDeque()

    private fun draw() {
        Log.d(TAG, "draw")
        textureId?.let {

            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(tmpMatrix)

            encodeSurface.makeCurrent()

            GLES20.glViewport(0, 0, mediaInfo.getWidth(), mediaInfo.getHeight())
            fullFrameRect.drawFrame(it, tmpMatrix)

            if (bufferTimeQueue.isNotEmpty()) {
                encodeSurface.setPresentationTime(bufferTimeQueue.poll() * 1000)
            }
            encodeSurface.swapBuffers()
        }
    }

    private fun initDecoder(mediaFormat: MediaFormat) {
        videoDecoder = VideoDecoderSync2()

        videoDecoder.prepare(mediaFormat, mediaExtractor, Surface(surfaceTexture))

        videoDecoder.onOutputBufferGenerate = { dataBuffer, bufferInfo ->
            Log.d(TAG, "onOutputBufferGenerate")
            Log.d(TAG, "decode_presentationTimeUs: ${bufferInfo.presentationTimeUs}")

            bufferTimeQueue.offer(bufferInfo.presentationTimeUs)
//            videoEncoder.offerData(dataBuffer, bufferInfo)
        }

        videoDecoder.onDecodeFinish = {
            Log.d(TAG, "onDecodeFinish")
            encodeSurface.release()
            videoEncoder.queueEOS()
        }
    }

    private fun initEncoder(mediaFormat: MediaFormat) {
        videoEncoder = VideoEncoderSync2()
        val mediaConfig = MediaConfig()

        mediaConfig.width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH)
        mediaConfig.height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)

        mediaConfig.duration = mediaFormat.getLong(MediaFormat.KEY_DURATION)
        mediaConfig.path = path
        videoEncoder.prepare(mediaConfig, bitrate)

        initTexture()

        videoEncoder.onSampleEncode = { dataBuffer, bufferInfo ->
            Log.d(TAG, "onSampleEncode")
            if (mediaMuxer == null) {
                initMediaMuxer()
            }

            Log.d(TAG, "encode_presentationTimeUs: ${bufferInfo.presentationTimeUs}")

            if (endMs != null && bufferInfo.presentationTimeUs > endMs!! * 1000 && !videoDecoder.isDecodeFinish) {
                Log.d(TAG, "------------- end trim ------------")
                videoDecoder.queueEOS()
                videoEncoder.queueEOS()
            } else {
                mediaMuxer?.writeSampleData(muxTrackIndex, dataBuffer, bufferInfo)
            }
        }
    }

    private fun initMediaMuxer() {

        mediaMuxer = MediaMuxer(dest, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxTrackIndex = mediaMuxer!!.addTrack(videoEncoder.getOutputFormat())

        // Set up the orientation and starting time for extractor.
//        val retriever = MediaMetadataRetriever()
//        retriever.setDataSource(path)
//        val degreesString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
//        if (degreesString != null) {
//            val degrees = Integer.parseInt(degreesString)
//            if (degrees >= 0) {
//                mediaMuxer!!.setOrientationHint(degrees)
//            }
//        }

        mediaMuxer!!.start()
    }

    private var isLooping = false

    private fun loop() {
        startMs?.let {
            mediaExtractor.seekTo(it * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        }

        while (true) {

            if (videoDecoder.isDecodeFinish && videoEncoder.isEncodeFinish) {
                Log.d(TAG, "there")
                release()
                break
            }

            if (!isLooping) {
                videoDecoder.enqueueData()

                videoDecoder.pull()
            }

            if (isNewFrameAvailable) {

                isLooping = true
                isNewFrameAvailable = false

                draw()

                videoEncoder.pull()

                videoDecoder.enqueueData()

                videoDecoder.pull()

                if (videoEncoder.isEOSNeed) {
                    videoEncoder.signEOS()
                }

            }


        }
    }

    fun release() {
        Log.d(TAG, "release")
        videoDecoder.release()
        videoEncoder.release()
        mediaExtractor.release()
        mediaMuxer?.release()

        eglCore.release()
        encodeSurface.release()
        surfaceTexture.release()

    }

}
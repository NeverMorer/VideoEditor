package com.religion76.library

import android.media.MediaMetadataRetriever

/**
 * Created by SunChao
 * on 2018/6/1.
 */
class MediaInfo private constructor(private var path: String,
                                    private var width: Int = 0,
                                    private var height: Int = 0,
                                    private var duration: Long = 0,
                                    private var bitrate: Int = 0,
                                    private var rotation: Int = 0) {

    companion object {
        const val FRAME_RATE = 30
        const val BPP = 0.3f
        const val IFRAMEINTERVAL = 1

        fun getMediaInfo(videoPath: String): MediaInfo {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            return MediaInfo(videoPath, extractInt(width), extractInt(height), extractLong(duration), extractInt(bitrate), extractInt(rotation))
        }

        private fun extractInt(str: String? = null): Int {
            return if (!str.isNullOrEmpty()) {
                str!!.toInt()
            } else {
                0
            }
        }

        private fun extractLong(str: String? = null): Long {
            return if (!str.isNullOrEmpty()) {
                str!!.toLong()
            } else {
                0
            }
        }
    }

    fun getWidth() = width
    fun getHeight() = height
    fun getDuration() = duration
    fun getBitrate() = bitrate
    fun getRotation() = rotation
    fun getPath() = path

    fun setScale(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    override fun toString(): String {

        return "width:${getWidth()} " +
                "height:${getHeight()}" +
                "duration:${getDuration()}" +
                "bitrate:${getBitrate()}" +
                "rotation:${getRotation()}"


    }

    fun is3GP():Boolean = path.endsWith("3gp")
}
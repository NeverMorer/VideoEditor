package com.religion76.library.sync

import android.media.MediaMetadataRetriever

/**
 * Created by SunChao
 * on 2018/6/1.
 */
class MediaInfo private constructor(private var width: Int = 0,
                                    private var height: Int = 0,
                                    private var duration: Int = 0,
                                    private var bitrate: Int = 0) {

    companion object {
        fun getMediaInfo(videoPath: String): MediaInfo {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)

            return MediaInfo(extractInt(width), extractInt(height), extractInt(duration), extractInt(bitrate))
        }

        private fun extractInt(str: String? = null): Int {
            return if (!str.isNullOrEmpty()) {
                str!!.toInt()
            } else {
                0
            }
        }
    }

    fun getWidth() = width
    fun getHeight() = height
    fun getDuration() = duration
    fun getBitrate() = bitrate

}
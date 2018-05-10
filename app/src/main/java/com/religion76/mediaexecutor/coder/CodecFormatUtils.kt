package com.religion76.mediaexecutor.coder

import android.media.MediaCodecInfo
import android.os.Build

/**
 * Created by SunChao
 * on 2018/4/11.
 */
object CodecFormatUtils {

    private val recognizedFormats: IntArray by lazy {
        intArrayOf(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar
        )
    }

    fun getVideoCompatibilityColorFormat(mediaCodecInfo: MediaCodecInfo, mimeType: String): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        }

        val codecCapabilities = mediaCodecInfo.getCapabilitiesForType(mimeType)
        codecCapabilities.colorFormats.forEach { colorFormat ->
            if (recognizedFormats.contains(colorFormat)) {
                return colorFormat
            }
        }

        return -1
    }
}
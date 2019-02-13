package com.religion76.library.gles

import android.util.Log
import android.view.Surface

/**
 * should use it in one thread
 *
 * Created by SunChao on 2018/11/26.
 */
class FrameRender {

    companion object {
        const val TAG = "FrameRender"
    }

    private lateinit var encodeInputSurface: WindowSurface

    private lateinit var decodeOutputSurface: CodecOutputSurface2

    private lateinit var eglCore: EglCore

    var rotateDegree: Int = 0

    fun init(surface: Surface) {
        eglCore = EglCore(null, EglCore.FLAG_RECORDABLE)

        encodeInputSurface = WindowSurface(eglCore, surface, true)

        encodeInputSurface.makeCurrent()

        decodeOutputSurface = CodecOutputSurface2()

    }


    fun draw(presentTime: Long) {
        Log.d(TAG, "draw")

        Log.d("bbb1", "b4_1")

        decodeOutputSurface.awaitNewImage()

        Log.d("bbb1", "b4_2")

        encodeInputSurface.makeCurrent()

        Log.d("bbb1", "b4_3")

        if (rotateDegree > 0) {
            decodeOutputSurface.drawImage(false, rotateDegree)
        } else {
            decodeOutputSurface.drawImage(false)
        }

        Log.d("bbb1", "b4_4")

        encodeInputSurface.setPresentationTime(presentTime * 1000)

        encodeInputSurface.swapBuffers()
    }

    fun getDecodeOutputSurface(): Surface = decodeOutputSurface.surface

    fun release(){
        eglCore.release()
        encodeInputSurface.release()
        decodeOutputSurface.release()
    }
}
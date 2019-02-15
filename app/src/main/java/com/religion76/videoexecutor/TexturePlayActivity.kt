package com.religion76.videoexecutor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.SurfaceHolder
import com.religion76.library.play.VideoSurfacePlay
import kotlinx.android.synthetic.main.activity_texture_play.*

class TexturePlayActivity : Activity() {

    companion object {

        fun startPlay(context: Context, srcPath: String) {
            val intent = Intent(context, TexturePlayActivity::class.java)
            intent.putExtra("src_path", srcPath)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_texture_play)

        val src = intent.getStringExtra("src_path")

        if (src.isNullOrEmpty()){
            finish()
            return
        }

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback{
            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {

            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {

            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
                holder?.surface?.let {
                    val videoTexturePlay = VideoSurfacePlay(src, it)
                    videoTexturePlay.startAsync()
                }
            }
        })
    }


}

package com.religion76.videoexecutor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.*
import android.support.annotation.MainThread

import android.view.View
import android.widget.Toast
import com.religion76.library.AppLogger
import com.religion76.library.MediaInfo
import com.religion76.library.v2.VideoAudioCoder
import com.sw926.imagefileselector.PermissionsHelper
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File

class MainActivity : Activity() {

    companion object {
        const val REQUEST_VIDEO = 0x11
        const val REQUEST_PERMISSION_RW = 0x12
    }

    private var videoPath: String? = null

    private var coder: VideoAudioCoder? = null

    private val ENCODE_DEST_PATH = "${Environment.getExternalStorageDirectory().absolutePath}/compressed_video/"

    private val permissionHelper: PermissionsHelper by lazy {
        PermissionsHelper()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnSelectVideo.setOnClickListener {
            permissionHelper.checkAndRequestPermission(this, REQUEST_PERMISSION_RW, {
                selectVideo()
            }, Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        btnCompress.setOnClickListener {
            if (videoPath.isNullOrEmpty()) {
                return@setOnClickListener
            }

            val mediaInfo = MediaInfo.getMediaInfo(videoPath!!)

            AppLogger.d("ddd", "origin video info: $mediaInfo")

            coder = VideoAudioCoder(videoPath!!, ENCODE_DEST_PATH + "bbb.mp4")
            coder!!.setWithAudio(false)

            val isWidthBig = mediaInfo.getWidth() > mediaInfo.getHeight()
            val scaleWidth = if (isWidthBig) 720 else 480
            val scaleHeight = if (isWidthBig) 480 else 720

            coder!!.withScale(scaleWidth, scaleHeight)
            coder!!.withTrim(3000, 6000)
            coder!!.withRotateFrame()

            coder!!.setCallback(object : VideoAudioCoder.ResultCallback {
                override fun onSucceed() {
                    Toast.makeText(this@MainActivity, "compress succeed QvQ", Toast.LENGTH_SHORT).show()
                    pbExecute.visibility = View.GONE

                    val mediaInfo = MediaInfo.getMediaInfo(ENCODE_DEST_PATH + "bbb.mp4")
                    AppLogger.d("ddd", "output video format: $mediaInfo")
                }

                override fun onFailed(errorMessage: String) {
                    Toast.makeText(this@MainActivity, "compress filed - -||", Toast.LENGTH_SHORT).show()
                    pbExecute.visibility = View.GONE
                    AppLogger.d("ddd", "video exc onFailed:$errorMessage")
                }
            }, Handler(Looper.getMainLooper()))

            coder!!.startAsync()
            pbExecute.visibility = View.VISIBLE
        }

        btnPlay.setOnClickListener {
            if (videoPath.isNullOrEmpty()) {
                return@setOnClickListener
            }

//            if (videoView.isPlaying) {
//                videoView.suspend()
//            }
//
//            videoView.setVideoPath(videoPath)
//            videoView.start()

            TexturePlayActivity.startPlay(this, videoPath!!)
        }

        videoView.setOnCompletionListener {
            videoView.suspend()
        }

        val destFile = File(ENCODE_DEST_PATH)
        if (!destFile.exists()) {
            destFile.mkdir()
        }
    }

    private fun selectVideo() {
        val intent = Intent()

        if (Build.VERSION.SDK_INT < 19) {
            intent.action = Intent.ACTION_GET_CONTENT
            intent.type = "video/*"
        } else {
            intent.action = Intent.ACTION_OPEN_DOCUMENT
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "video/*"
        }

        startActivityForResult(Intent.createChooser(intent, "选择要导入的视频"), REQUEST_VIDEO)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (coder != null) {
            coder!!.release()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VIDEO && resultCode == Activity.RESULT_OK) {
            data?.data?.let {
                val path = MediaPathUtil.getPath(this, it)
                videoPath = path
                tvVideoPath.text = path
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHelper.onRequestPermissionsResult(REQUEST_PERMISSION_RW, permissions, grantResults)
    }
}

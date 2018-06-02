package com.religion76.videoexecutor

import android.Manifest
import android.media.MediaMetadataRetriever
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.support.v4.widget.SimpleCursorAdapter
import android.util.Log
import android.view.View
import com.religion76.library.sync.VideoCoderSync
import com.religion76.library.sync.VideoCoderSync3
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_video.view.*
import java.io.File

class MainActivity : AppCompatActivity() {

    val FILE_PATH = Environment.getExternalStorageDirectory().absolutePath.plus(File.separator).plus(System.currentTimeMillis().toString() + "hhhh.mp4")

    private lateinit var localVideos: MutableList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 101)
        } else {
            loadLocalVideos()
        }
    }

    var coder: VideoCoderSync3? = null

    val handler = Handler()

    private fun loadLocalVideos() {
        localVideos = emptyList<String>().toMutableList()

        val cursor = contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null, null, null)

        val adapter = SimpleCursorAdapter(this, R.layout.item_video, cursor, arrayOf(MediaStore.Video.Media.DATA), intArrayOf(R.id.tvPath))

        image.setOnClickListener {
            it.visibility = View.GONE
        }

        lvVideos.adapter = adapter
        lvVideos.setOnItemClickListener { parent, view, position, id ->
            //            val mediaCoder = MediaCoder()
//            mediaCoder.start(view.tvPath.text.toString(),2000, 6000)

            val path = view.tvPath.text.toString()

            val retrieverSrc = MediaMetadataRetriever()
            retrieverSrc.setDataSource(path)


            val degreesString = retrieverSrc.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            if (degreesString != null) {
                val d = Integer.parseInt(degreesString)
                Log.d(VideoCoderSync.TAG, " rotate degree:$d")
            }

            coder = VideoCoderSync3(path, FILE_PATH)
            coder?.withTrim(1000, 5000)
//            coder.withScale(480, 480)M
            Thread(coder).start()

        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        loadLocalVideos()

    }

    override fun onDestroy() {
        super.onDestroy()
        coder?.release()
    }
}

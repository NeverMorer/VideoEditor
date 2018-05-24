package com.religion76.videoexecutor

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.widget.SimpleCursorAdapter
import com.religion76.library.sync.VideoCoderSync
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_video.view.*
import java.io.File

class MainActivity : AppCompatActivity() {

    val FILE_PATH = Environment.getExternalStorageDirectory().absolutePath.plus(File.separator).plus(System.currentTimeMillis().toString() + "hhhh.mp4")

    private lateinit var localVideos: MutableList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        loadLocalVideos()
    }

    private fun loadLocalVideos() {
        localVideos = emptyList<String>().toMutableList()

        val cursor = contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null, null, null)

        val adapter = SimpleCursorAdapter(this, R.layout.item_video, cursor, arrayOf(MediaStore.Video.Media.DATA), intArrayOf(R.id.tvPath))

//        VideoDecoder().decode("/storage/sdcard1/DCIM/Video/V80102-223718.mp4", 1000)

        lvVideos.adapter = adapter
        lvVideos.setOnItemClickListener { parent, view, position, id ->
            //            val mediaCoder = MediaCoder()
//            mediaCoder.start(view.tvPath.text.toString(),2000, 6000)
            val coder = VideoCoderSync(view.tvPath.text.toString(), FILE_PATH)
            coder.withTrim(2000, 5000)
            coder.withScale(480, 480)
            Thread(coder).start()
        }

    }

    override fun onDestroy() {
        super.onDestroy()

    }
}

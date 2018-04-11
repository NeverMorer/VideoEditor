package com.religion76.mediaexecutor

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.widget.SimpleCursorAdapter
import com.religion76.mediaexecutor.coder.MediaCoder
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_video.view.*

class MainActivity : AppCompatActivity() {

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

        lvVideos.adapter = adapter
        lvVideos.setOnItemClickListener { parent, view, position, id ->
            val mediaCoder = MediaCoder()
            mediaCoder.start(view.tvPath.text.toString())
        }

    }

    override fun onDestroy() {
        super.onDestroy()

    }
}

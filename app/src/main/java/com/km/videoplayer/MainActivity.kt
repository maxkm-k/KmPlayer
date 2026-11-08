package com.km.videoplayer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var statusText: TextView

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            playVideo(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root layout
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ExoPlayer View
        playerView = PlayerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            useController = true
        }
        rootLayout.addView(playerView)

        // Status text
        statusText = TextView(this).apply {
            text = "Select a video to play"
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
        }
        rootLayout.addView(statusText)

        // Choose Video Button
        val selectButton = Button(this).apply {
            text = "Choose Video File"
            setOnClickListener {
                videoPickerLauncher.launch(arrayOf("video/*", "*/*"))
            }
        }
        rootLayout.addView(selectButton)

        setContentView(rootLayout)

        // Open directly from external file manager intent
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { playVideo(it) }
        }
    }

    private fun playVideo(uri: Uri) {
        statusText.text = "Playing: ${uri.lastPathSegment ?: "Video"}"
        player?.release()
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            playerView.player = exoPlayer
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}

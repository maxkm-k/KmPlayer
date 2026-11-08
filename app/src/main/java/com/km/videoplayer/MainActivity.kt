package com.km.videoplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var recyclerView: RecyclerView
    private val playlist = mutableListOf<PlaylistItem>()
    private lateinit var adapter: PlaylistAdapter

    private val prefsName = "KmPlayerPrefs"
    private val keyPlaylist = "saved_playlist"

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val fileName = getFileName(it)
            val item = PlaylistItem(fileName, it.toString())
            if (!playlist.any { existing -> existing.uriString == it.toString() }) {
                playlist.add(item)
                savePlaylist()
                adapter.notifyDataSetChanged()
            }
            playVideo(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        recyclerView = findViewById(R.id.playlistRecyclerView)
        val selectButton = findViewById<Button>(R.id.selectButton)
        val clearButton = findViewById<Button>(R.id.clearPlaylistButton)

        loadPlaylist()

        adapter = PlaylistAdapter(playlist) { item ->
            playVideo(Uri.parse(item.uriString))
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        selectButton.setOnClickListener {
            // Allows selecting all video types (MP4, MKV, AVI, MOV, WEBM, 3GP, TS, etc.)
            videoPickerLauncher.launch(arrayOf("video/*", "application/octet-stream"))
        }

        clearButton.setOnClickListener {
            playlist.clear()
            savePlaylist()
            adapter.notifyDataSetChanged()
            player?.stop()
        }

        // Handle file open intents from other apps
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                val name = getFileName(uri)
                val item = PlaylistItem(name, uri.toString())
                if (!playlist.any { it.uriString == uri.toString() }) {
                    playlist.add(0, item)
                    savePlaylist()
                    adapter.notifyDataSetChanged()
                }
                playVideo(uri)
            }
        }
    }

    private fun initializePlayer(uri: Uri) {
        player?.release()
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            playerView.player = exoPlayer
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true

            exoPlayer.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    super.onPlayerError(error)
                    // Fallback handling or logs can go here if format codec fails
                }
            })
        }
    }

    private fun playVideo(uri: Uri) {
        initializePlayer(uri)
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex("_display_name")
                    if (idx != -1) result = cursor.getString(idx)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "Unknown Video"
    }

    private fun savePlaylist() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val json = Gson().toJson(playlist)
        prefs.edit().putString(keyPlaylist, json).apply()
    }

    private fun loadPlaylist() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val json = prefs.getString(keyPlaylist, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<PlaylistItem>>() {}.type
            val savedList: MutableList<PlaylistItem> = Gson().fromJson(json, type)
            playlist.clear()
            playlist.addAll(savedList)
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

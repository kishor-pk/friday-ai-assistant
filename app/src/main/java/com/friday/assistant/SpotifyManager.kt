package com.friday.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent

class SpotifyManager(private val context: Context) {

    private val TAG = "SpotifyManager"

    fun play(query: String, onResult: (String) -> Unit) {
        Log.d(TAG, "Play request: $query")
        if (isInstalled("com.spotify.music")) {
            openAndPlay(query, onResult)
        } else {
            playOnYouTube(query, onResult)
        }
    }

    private fun openAndPlay(query: String, onResult: (String) -> Unit) {
        try {
            // Step 1 — open Spotify
            val launch = context.packageManager
                .getLaunchIntentForPackage("com.spotify.music")!!
            launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(launch)

            onResult("Opening Spotify, Boss.")

            // Step 2 — wait for Spotify to load then send play
            Handler(Looper.getMainLooper()).postDelayed({
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                Log.d(TAG, "Play key sent after delay")
            }, 3000)

        } catch (e: Exception) {
            Log.e(TAG, "Failed: ${e.message}")
            onResult("Could not open Spotify, Boss.")
        }
    }

    fun pause(onResult: (String) -> Unit) {
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        onResult("Paused, Boss.")
    }

    fun resume(onResult: (String) -> Unit) {
        if (isInstalled("com.spotify.music")) {
            try {
                val launch = context.packageManager
                    .getLaunchIntentForPackage("com.spotify.music")!!
                launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(launch)
                Handler(Looper.getMainLooper()).postDelayed({
                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                }, 2000)
                onResult("Resuming Spotify, Boss.")
            } catch (e: Exception) {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                onResult("Resuming, Boss.")
            }
        } else {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            onResult("Resuming, Boss.")
        }
    }

    fun next(onResult: (String) -> Unit) {
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        onResult("Next track, Boss.")
    }

    fun previous(onResult: (String) -> Unit) {
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        onResult("Previous track, Boss.")
    }

    private fun sendMediaKey(keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            Log.d(TAG, "Media key sent: $keyCode")
        } catch (e: Exception) {
            Log.e(TAG, "Media key failed: ${e.message}")
        }
    }

    private fun playOnYouTube(query: String, onResult: (String) -> Unit) {
        try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", "$query official audio")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            onResult("Playing $query on YouTube, Boss.")
        } catch (e: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(
                    "https://www.youtube.com/results?search_query=" +
                            Uri.encode("$query official audio")
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
            onResult("Searching $query on YouTube, Boss.")
        }
    }

    private fun isInstalled(pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) { false }
    }
}
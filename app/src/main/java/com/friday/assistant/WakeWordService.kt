package com.friday.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

class WakeWordService : Service() {

    private var speechService: SpeechService? = null
    private val CHANNEL_ID = "friday_wake"
    private val TAG = "WakeWordService"
    private var isPaused = false
    private var lastTriggerTime = 0L
    private val TRIGGER_COOLDOWN_MS = 5000L  // 5 seconds cooldown

    companion object {
        var instance: WakeWordService? = null
        var isFridaySpeaking = false  // prevent self-trigger

        fun pause()  { instance?.pauseListening() }
        fun resume() { instance?.resumeListening() }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(1, buildNotification("Friday is listening..."))
        initVosk()
    }

    private fun initVosk() {
        if (isPaused) return
        Thread {
            try {
                var waited = 0
                while (!MainActivity.isModelReady && waited < 10000) {
                    Thread.sleep(500)
                    waited += 500
                }

                if (!MainActivity.isModelReady || MainActivity.sharedModel == null) {
                    val modelPath = File(
                        getExternalFilesDir(null),
                        Constants.VOSK_MODEL_FOLDER
                    )
                    if (!modelPath.exists()) {
                        updateNotification("Vosk model missing")
                        return@Thread
                    }
                    MainActivity.sharedModel  = org.vosk.Model(modelPath.absolutePath)
                    MainActivity.isModelReady = true
                }

                val recognizer = Recognizer(MainActivity.sharedModel!!, 16000.0f)
                speechService  = SpeechService(recognizer, 16000.0f)
                speechService!!.startListening(recognitionListener)
                updateNotification("Friday is listening...")
                Log.d(TAG, "Vosk started successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Vosk init failed: ${e.message}")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    initVosk()
                }, 3000)
            }
        }.start()
    }

    fun pauseListening() {
        isPaused = true
        speechService?.stop()
        speechService = null
        updateNotification("Friday paused...")
        Log.d(TAG, "Wake word paused")
    }

    fun resumeListening() {
        isPaused = false
        initVosk()
        Log.d(TAG, "Wake word resumed")
    }

    private val recognitionListener = object : RecognitionListener {

        override fun onPartialResult(hypothesis: String?) {
            if (isPaused || isFridaySpeaking) return
            try {
                val text = JSONObject(hypothesis ?: "")
                    .optString("partial", "").lowercase()
                if (text.isNotBlank()) Log.d(TAG, "Partial: $text")
                if (Constants.WAKE_WORDS.any { text.contains(it) }) {
                    triggerAssistant()
                }
            } catch (e: Exception) {}
        }

        override fun onResult(hypothesis: String?) {
            if (isPaused || isFridaySpeaking) return
            try {
                val text = JSONObject(hypothesis ?: "")
                    .optString("text", "").lowercase()
                if (text.isNotBlank()) Log.d(TAG, "Result: $text")
                if (Constants.WAKE_WORDS.any { text.contains(it) }) {
                    triggerAssistant()
                }
            } catch (e: Exception) {}
        }

        override fun onFinalResult(hypothesis: String?) {}

        override fun onError(e: Exception?) {
            if (!isPaused) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    speechService?.stop()
                    initVosk()
                }, 2000)
            }
        }

        override fun onTimeout() {
            if (!isPaused) {
                speechService?.stop()
                initVosk()
            }
        }
    }

    private fun triggerAssistant() {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < TRIGGER_COOLDOWN_MS) {
            Log.d(TAG, "Cooldown — ignoring")
            return
        }
        lastTriggerTime = now
        Log.d(TAG, "Wake word triggered!")
        pauseListening()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("wake_word_triggered", true)
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Friday Assistant",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("F.R.I.D.A.Y")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(1, buildNotification(text))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        instance = null
        speechService?.stop()
        speechService = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
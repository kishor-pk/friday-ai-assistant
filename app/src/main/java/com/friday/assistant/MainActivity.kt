package com.friday.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tts: TextToSpeech
    private lateinit var tvState: TextView
    private lateinit var tvResponse: TextView
    private lateinit var brain: AIBrain
    private lateinit var executor: TaskExecutor
    private var isTtsReady = false
    private var isListeningForCommand = false
    private var speechService: SpeechService? = null

    companion object {
        var sharedModel: Model? = null
        var isModelReady = false
    }

    private val PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvState    = findViewById(R.id.tvState)
        tvResponse = findViewById(R.id.tvResponse)
        brain      = AIBrain()
        executor   = TaskExecutor(this)

        setupTTS()
        requestMissingPermissions()

        if (!isModelReady) {
            loadVoskModel()
        } else {
            tvState.text = "Tap mic or say Hey Friday"
        }

        startWakeWordService()

        findViewById<ImageButton>(R.id.btnMic).setOnClickListener {
            if (isListeningForCommand) {
                stopListening()
            } else {
                startListeningForCommand()
            }
        }

        if (intent.getBooleanExtra("wake_word_triggered", false)) {
            handleWake()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("wake_word_triggered", false)) {
            handleWake()
        }
    }

    private fun loadVoskModel() {
        tvState.text = "Loading voice model..."
        Thread {
            try {
                val modelPath = File(
                    getExternalFilesDir(null),
                    Constants.VOSK_MODEL_FOLDER
                )
                if (!modelPath.exists()) {
                    runOnUiThread {
                        tvState.text = "Vosk model missing — check setup guide"
                    }
                    return@Thread
                }
                sharedModel  = Model(modelPath.absolutePath)
                isModelReady = true
                runOnUiThread {
                    tvState.text = "Tap mic or say Hey Friday"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvState.text = "Model error: ${e.message}"
                }
            }
        }.start()
    }

    private fun startListeningForCommand() {
        if (!isModelReady || sharedModel == null) {
            tvState.text = "Still loading model, please wait..."
            return
        }

        WakeWordService.pause()
        isListeningForCommand = true
        tvState.text = "Listening... speak now"
        tvResponse.text = ""

        // Save context reference before entering thread
        val appContext = applicationContext

        Thread {
            try {
                val grammar = """["call", "phone", "rashid", "akhil", "bca", "message", "send", "whatsapp",
                "play", "music", "song", "pause", "lonely", "stop", "next", "previous",
                "open", "launch", "start",
                "alarm", "set", "timer", "remind",
                "weather", "time", "date", "temperature",
                "what", "how", "who", "where", "when", "why",
                "hey", "friday", "yes", "no", "cancel",
                "volume", "up", "down", "mute",
                "youtube", "spotify", "whatsapp", "instagram", "chrome",
                "hello", "hi", "thanks", "thank", "you",
                "boss", "kishor", "morning", "night",
                "[unk]"]"""

                // Boost mic volume
                val audioManager = appContext.getSystemService(android.content.Context.AUDIO_SERVICE)
                        as android.media.AudioManager
                audioManager.setStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC,
                    audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC),
                    0
                )

                val recognizer = org.vosk.Recognizer(sharedModel!!, 16000.0f, grammar)
                val service    = org.vosk.android.SpeechService(recognizer, 16000.0f)

                runOnUiThread {
                    speechService = service
                    speechService!!.startListening(commandListener)
                    tvState.text = "Listening... speak now"
                }

            } catch (e: Exception) {
                runOnUiThread {
                    tvState.text = "Mic error: ${e.message}"
                    isListeningForCommand = false
                    WakeWordService.resume()
                }
            }
        }.start()
    }

    private fun stopListening() {
        isListeningForCommand = false
        speechService?.stop()
        speechService = null
        WakeWordService.resume()
        tvState.text = "Tap mic or say Hey Friday"
    }

    private val commandListener = object : RecognitionListener {

        override fun onPartialResult(hypothesis: String?) {
            try {
                val text = JSONObject(hypothesis ?: "").optString("partial", "")
                if (text.isNotBlank()) {
                    runOnUiThread { tvState.text = "Hearing: $text" }
                }
            } catch (e: Exception) {}
        }

        override fun onResult(hypothesis: String?) {
            try {
                val text = JSONObject(hypothesis ?: "").optString("text", "")
                if (text.isNotBlank()) {
                    runOnUiThread {
                        tvState.text    = "You said: $text"
                        tvResponse.text = "Thinking..."
                        stopListening()
                        sendToAI(text)
                    }
                }
            } catch (e: Exception) {}
        }

        override fun onFinalResult(hypothesis: String?) {
            try {
                val text = JSONObject(hypothesis ?: "").optString("text", "")
                if (text.isNotBlank()) {
                    runOnUiThread {
                        tvState.text    = "You said: $text"
                        tvResponse.text = "Thinking..."
                        stopListening()
                        sendToAI(text)
                    }
                } else {
                    runOnUiThread {
                        tvState.text = "Didn't catch that. Tap mic and try again."
                        isListeningForCommand = false
                        WakeWordService.resume()
                    }
                }
            } catch (e: Exception) {}
        }

        override fun onError(e: Exception?) {
            runOnUiThread {
                tvState.text = "Mic error. Tap to try again."
                isListeningForCommand = false
                WakeWordService.resume()
            }
        }

        override fun onTimeout() {
            runOnUiThread {
                tvState.text = "Timed out. Tap mic to try again."
                isListeningForCommand = false
                WakeWordService.resume()
            }
        }
    }

    private fun handleWake() {
        if (!isTtsReady) {
            Handler(Looper.getMainLooper()).postDelayed({ handleWake() }, 500)
            return
        }
        speakThen("Yes Boss?") { startListeningForCommand() }
    }

    private fun sendToAI(input: String) {
        brain.ask(
            userInput = input,
            onResult = { response ->
                runOnUiThread {
                    tvResponse.text = response
                    executor.execute(response) { textToSpeak ->
                        runOnUiThread { speak(textToSpeak) }
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    executor.execute(error) { textToSpeak ->
                        runOnUiThread { speak(textToSpeak) }
                    }
                }
            }
        )
    }

    private fun setupTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.setSpeechRate(0.93f)
                tts.setPitch(0.87f)
                isTtsReady = true
            }
        }
    }

    private fun speak(text: String) {
        tvState.text = "Friday: $text"
        WakeWordService.isFridaySpeaking = true  // block wake word
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                WakeWordService.isFridaySpeaking = false  // unblock wake word
                runOnUiThread { tvState.text = "Tap mic or say Hey Friday" }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                WakeWordService.isFridaySpeaking = false
            }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt")
    }

    private fun speakThen(text: String, onDone: () -> Unit) {
        WakeWordService.isFridaySpeaking = true  // block wake word
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                WakeWordService.isFridaySpeaking = false  // unblock
                runOnUiThread { onDone() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                WakeWordService.isFridaySpeaking = false
                runOnUiThread { onDone() }
            }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wake")
    }

    private fun startWakeWordService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, WakeWordService::class.java)
        )
    }

    private fun requestMissingPermissions() {
        val missing = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                    PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                100
            )
        }
    }

    override fun onDestroy() {
        speechService?.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
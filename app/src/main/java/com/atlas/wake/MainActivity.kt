package com.atlas.wake

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.DetectionMode
import com.rementia.openwakeword.lib.model.WakeWordModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView

    private var wakeWordEngine: WakeWordEngine? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val handler = Handler(Looper.getMainLooper())

    private var isListeningForCommand = false
    private var isRestartingWakeWord = false

    companion object {
        private const val MICROPHONE_REQUEST = 100
        private const val WAKE_RESTART_DELAY = 700L
        private const val COMMAND_TIMEOUT = 7000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this)
        status.text = "ATLAS Wake\n\nStarting..."
        status.textSize = 20f
        status.setPadding(40, 100, 40, 40)
        setContentView(status)

        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MICROPHONE_REQUEST
            )
        } else {
            startWakeWordDetection()
        }
    }

    private fun startWakeWordDetection() {

        if (isListeningForCommand) return

        status.text =
            "ATLAS Wake\n\n" +
            "Listening for:\n" +
            "HEY JARVIS"

        val models = listOf(
            WakeWordModel(
                name = "Hey Jarvis",
                modelPath = "hey_jarvis_v0.1.onnx",
                threshold = 0.5f
            )
        )

        try {

            wakeWordEngine?.release()

            wakeWordEngine = WakeWordEngine(
                context = this,
                models = models,
                detectionMode = DetectionMode.SINGLE_BEST,
                detectionCooldownMs = 2000L
            )

            lifecycleScope.launch {

                wakeWordEngine?.detections?.collect { detection ->

                    if (isListeningForCommand) return@collect

                    isListeningForCommand = true

                    status.text =
                        "ATLAS WAKE!\n\n" +
                        "HEY JARVIS DETECTED!\n\n" +
                        "I'm listening..."

                    stopWakeWordEngine()

                    listenForCommand()
                }
            }

            wakeWordEngine?.start()

        } catch (e: Exception) {

            status.text =
                "ATLAS Wake\n\n" +
                "Wake-word error:\n\n" +
                "${e.message}"

            scheduleWakeWordRestart()
        }
    }

    private fun stopWakeWordEngine() {
        try {
            wakeWordEngine?.release()
        } catch (_: Exception) {
        }
    }

    private fun listenForCommand() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {

            status.text =
                "ATLAS WAKE\n\n" +
                "Speech recognition is not available."

            restartWakeWord()
            return
        }

        speechRecognizer?.destroy()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {

                    status.text =
                        "ATLAS WAKE\n\n" +
                        "I'm listening...\n\n" +
                        "Say your command."
                }

                override fun onBeginningOfSpeech() {

                    status.text =
                        "ATLAS WAKE\n\n" +
                        "Hearing you..."
                }

                override fun onRmsChanged(rmsdB: Float) {
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                }

                override fun onEndOfSpeech() {

                    status.text =
                        "ATLAS WAKE\n\n" +
                        "Processing..."
                }

                override fun onError(error: Int) {

                    status.text =
                        "ATLAS WAKE\n\n" +
                        "I didn't catch that.\n\n" +
                        "Error: $error\n\n" +
                        "Returning to wake mode..."

                    restartWakeWord()
                }

                override fun onResults(results: Bundle?) {

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val command =
                        matches
                            ?.firstOrNull()
                            ?.lowercase(Locale.getDefault())
                            ?.trim()
                            ?: ""

                    if (command.isBlank()) {

                        status.text =
                            "ATLAS WAKE\n\n" +
                            "I didn't hear a command."

                        restartWakeWord()
                        return
                    }

                    handleCommand(command)
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                }
            }
        )

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            )

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            Locale.getDefault()
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_MAX_RESULTS,
            5
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_PARTIAL_RESULTS,
            false
        )

        try {

            speechRecognizer?.startListening(intent)

            handler.postDelayed(
                {
                    if (isListeningForCommand) {
                        speechRecognizer?.stopListening()
                    }
                },
                COMMAND_TIMEOUT
            )

        } catch (e: Exception) {

            status.text =
                "ATLAS WAKE\n\n" +
                "Could not start speech recognition.\n\n" +
                "${e.message}"

            restartWakeWord()
        }
    }

    private fun handleCommand(command: String) {

        status.text =
            "ATLAS WAKE\n\n" +
            "I heard:\n\n" +
            "\"$command\"\n\n" +
            "Processing..."

        when {

            command.contains("open whatsapp") ||
            command.contains("launch whatsapp") ||
            command.contains("start whatsapp") ||
            command.contains("go to whatsapp") ||
            command.contains("open my whatsapp") -> {

                openWhatsApp()
            }

            command.contains("what is the time") ||
            command.contains("what's the time") ||
            command.contains("tell me the time") ||
            command.contains("current time") ||
            command == "time" -> {

                tellTime()
            }

            command.startsWith("search for ") -> {

                val topic =
                    command.removePrefix("search for ").trim()

                searchWeb(topic)
            }

            command.startsWith("search ") -> {

                val topic =
                    command.removePrefix("search ").trim()

                searchWeb(topic)
            }

            command.contains("search the web for ") -> {

                val topic =
                    command.substringAfter(
                        "search the web for "
                    ).trim()

                searchWeb(topic)
            }

            command.contains("google ") -> {

                val topic =
                    command.substringAfter("google ").trim()

                searchWeb(topic)
            }

            else -> {

                status.text =
                    "ATLAS WAKE\n\n" +
                    "I heard:\n\n" +
                    "\"$command\"\n\n" +
                    "I don't know that command yet."

                restartWakeWord()
            }
        }
    }

    private fun openWhatsApp() {

        status.text =
            "ATLAS WAKE\n\n" +
            "Opening WhatsApp..."

        val whatsapp =
            packageManager.getLaunchIntentForPackage(
                "com.whatsapp"
            )

        if (whatsapp != null) {

            try {
                startActivity(whatsapp)
            } catch (_: Exception) {

                status.text =
                    "ATLAS WAKE\n\n" +
                    "I couldn't open WhatsApp."
            }

        } else {

            status.text =
                "ATLAS WAKE\n\n" +
                "WhatsApp is not installed."
        }

        restartWakeWord()
    }

    private fun tellTime() {

        val currentTime =
            SimpleDateFormat(
                "h:mm a",
                Locale.getDefault()
            ).format(Date())

        status.text =
            "ATLAS WAKE\n\n" +
            "The time is:\n\n" +
            currentTime

        restartWakeWord()
    }

    private fun searchWeb(topic: String) {

        if (topic.isBlank()) {

            status.text =
                "ATLAS WAKE\n\n" +
                "Tell me what you want me to search for."

            restartWakeWord()
            return
        }

        status.text =
            "ATLAS WAKE\n\n" +
            "Searching the web for:\n\n" +
            topic

        try {

            val searchUrl =
                "https://www.google.com/search?q=" +
                        Uri.encode(topic)

            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(searchUrl)
                )
            )

        } catch (e: Exception) {

            status.text =
                "ATLAS WAKE\n\n" +
                "Couldn't open the web search."
        }

        restartWakeWord()
    }

    private fun restartWakeWord() {

        isListeningForCommand = false

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null

        handler.postDelayed(
            {

                if (isFinishing || isDestroyed) return@postDelayed

                startWakeWordDetection()

            },
            WAKE_RESTART_DELAY
        )
    }

    private fun scheduleWakeWordRestart() {

        if (isRestartingWakeWord) return

        isRestartingWakeWord = true

        handler.postDelayed(
            {

                isRestartingWakeWord = false

                if (!isFinishing && !isDestroyed) {
                    startWakeWordDetection()
                }

            },
            1500L
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == MICROPHONE_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] ==
            PackageManager.PERMISSION_GRANTED
        ) {

            startWakeWordDetection()

        } else {

            status.text =
                "ATLAS Wake\n\n" +
                "Microphone permission denied."
        }
    }

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(null)

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null

        try {
            wakeWordEngine?.release()
        } catch (_: Exception) {
        }

        wakeWordEngine = null

        super.onDestroy()
    }
}

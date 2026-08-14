package com.atlas.wake

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this)
        status.text = "ATLAS Wake\n\nRequesting microphone permission..."
        status.textSize = 20f
        status.setPadding(40, 100, 40, 40)
        setContentView(status)

        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        } else {
            startWakeWordDetection()
        }
    }

    private fun startWakeWordDetection() {

        status.text =
            "ATLAS Wake\n\nListening for:\nHEY JARVIS"

        val models = listOf(
            WakeWordModel(
                name = "Hey Jarvis",
                modelPath = "hey_jarvis_v0.1.onnx",
                threshold = 0.5f
            )
        )

        wakeWordEngine = WakeWordEngine(
            context = this,
            models = models,
            detectionMode = DetectionMode.SINGLE_BEST,
            detectionCooldownMs = 2000L
        )

        lifecycleScope.launch {
            wakeWordEngine?.detections?.collect {

                status.text =
                    "ATLAS WAKE!\n\n" +
                    "HEY JARVIS DETECTED!\n\n" +
                    "Listening for command..."

                listenForCommand()
            }
        }

        try {
            wakeWordEngine?.start()
        } catch (e: Exception) {
            status.text =
                "ATLAS Wake\n\n" +
                "Could not start wake-word detection.\n\n" +
                e.message
        }
    }

    private fun listenForCommand() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text =
                "ATLAS Wake\n\n" +
                "Speech recognition is not available."

            restartWakeWord()
            return
        }

        speechRecognizer?.destroy()

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    status.text =
                        "ATLAS WAKE\n\n" +
                        "I'm listening..."
                }

                override fun onBeginningOfSpeech() {
                    status.text =
                        "ATLAS WAKE\n\n" +
                        "Hearing you..."
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    status.text =
                        "ATLAS WAKE\n\n" +
                        "Processing..."
                }

                override fun onError(error: Int) {
                    status.text =
                        "ATLAS WAKE\n\n" +
                        "I didn't understand that."

                    restartWakeWord()
                }

                override fun onResults(results: Bundle?) {

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val command =
                        matches?.firstOrNull()
                            ?.lowercase(Locale.getDefault())
                            ?.trim()
                            ?: ""

                    handleCommand(command)
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {}

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {}
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
            1
        )

        speechRecognizer?.startListening(intent)
    }

    private fun handleCommand(command: String) {

        when {

            command.contains("open whatsapp") -> {

                status.text =
                    "ATLAS WAKE\n\n" +
                    "Opening WhatsApp..."

                val whatsapp =
                    packageManager.getLaunchIntentForPackage(
                        "com.whatsapp"
                    )

                if (whatsapp != null) {
                    startActivity(whatsapp)
                } else {
                    status.text =
                        "ATLAS WAKE\n\n" +
                        "WhatsApp is not installed."
                }

                restartWakeWord()
            }

            command.contains("what is the time") ||
            command.contains("what's the time") ||
            command.contains("tell me the time") ||
            command == "time" -> {

                val currentTime =
                    SimpleDateFormat(
                        "h:mm a",
                        Locale.getDefault()
                    ).format(Date())

                status.text =
                    "ATLAS WAKE\n\n" +
                    "The time is $currentTime"

                restartWakeWord()
            }

            command.startsWith("search the web for") -> {

                val topic =
                    command.substringAfter(
                        "search the web for"
                    ).trim()

                searchWeb(topic)
            }

            command.startsWith("search for") -> {

                val topic =
                    command.substringAfter(
                        "search for"
                    ).trim()

                searchWeb(topic)
            }

            else -> {

                status.text =
                    "ATLAS WAKE\n\n" +
                    "I heard:\n\n$command\n\n" +
                    "I don't know that command yet."

                restartWakeWord()
            }
        }
    }

    private fun searchWeb(topic: String) {

        if (topic.isBlank()) {

            status.text =
                "ATLAS WAKE\n\n" +
                "Tell me what to search for."

            restartWakeWord()
            return
        }

        status.text =
            "ATLAS WAKE\n\n" +
            "Searching for:\n$topic"

        val searchUrl =
            "https://www.google.com/search?q=" +
                    Uri.encode(topic)

        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(searchUrl)
            )
        )

        restartWakeWord()
    }

    private fun restartWakeWord() {

        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null

        status.postDelayed({

            status.text =
                "ATLAS Wake\n\n" +
                "Listening for:\nHEY JARVIS"

            try {
                wakeWordEngine?.start()
            } catch (e: Exception) {
                status.text =
                    "ATLAS Wake\n\n" +
                    "Could not restart wake-word detection."
            }

        }, 1000)
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
            requestCode == 100 &&
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

        speechRecognizer?.destroy()

        wakeWordEngine?.release()
        wakeWordEngine = null

        super.onDestroy()
    }
}

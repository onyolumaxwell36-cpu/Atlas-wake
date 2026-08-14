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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private var engine: WakeWordEngine? = null
    private var speechRecognizer: SpeechRecognizer? = null

    companion object {
        private const val MICROPHONE_REQUEST = 100
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
            startWakeWord()
        }
    }

    private fun startWakeWord() {

        status.text = "ATLAS Wake\n\nListening for:\nHey Jarvis"

        engine = WakeWordEngine(this)

        engine?.start()

        lifecycleScope.launch {
            engine?.detections?.collect { detection ->

                status.text =
                    "ATLAS WAKE!\n\nWake word detected!\n\nListening for your command..."

                startCommandListening()
            }
        }
    }

    private fun startCommandListening() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text =
                "ATLAS Wake\n\nSpeech recognition is not available."
            return
        }

        speechRecognizer?.destroy()

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    status.text =
                        "ATLAS WAKE\n\nI'm listening..."
                }

                override fun onBeginningOfSpeech() {
                    status.text =
                        "ATLAS WAKE\n\nHearing you..."
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    status.text =
                        "ATLAS WAKE\n\nProcessing..."
                }

                override fun onError(error: Int) {
                    status.text =
                        "ATLAS WAKE\n\nI didn't understand that."
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
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)

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

            command.contains("open whatsapp") ||
            command.contains("open my whatsapp") -> {

                status.text =
                    "ATLAS WAKE\n\nOpening WhatsApp..."

                val intent =
                    packageManager.getLaunchIntentForPackage(
                        "com.whatsapp"
                    )

                if (intent != null) {
                    startActivity(intent)
                } else {
                    status.text =
                        "ATLAS WAKE\n\nWhatsApp is not installed."
                }

                restartWakeWord()
            }

            command.contains("what is the time") ||
            command.contains("what's the time") ||
            command.contains("tell me the time") ||
            command == "time" -> {

                val time =
                    SimpleDateFormat(
                        "h:mm a",
                        Locale.getDefault()
                    ).format(Date())

                status.text =
                    "ATLAS WAKE\n\nThe time is $time"

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
                    "I heard:\n$command\n\n" +
                    "I don't know that command yet."

                restartWakeWord()
            }
        }
    }

    private fun searchWeb(topic: String) {

        if (topic.isBlank()) {
            status.text =
                "ATLAS WAKE\n\nTell me what to search for."
            restartWakeWord()
            return
        }

        status.text =
            "ATLAS WAKE\n\nSearching for:\n$topic"

        val url =
            "https://www.google.com/search?q=" +
                    Uri.encode(topic)

        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
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
                "ATLAS Wake\n\nListening for:\nHey Jarvis"

            engine?.start()

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
            requestCode == MICROPHONE_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startWakeWord()
        } else {
            status.text =
                "ATLAS Wake\n\nMicrophone permission denied."
        }
    }

    override fun onDestroy() {

        speechRecognizer?.destroy()

        engine?.release()
        engine = null

        super.onDestroy()
    }
}

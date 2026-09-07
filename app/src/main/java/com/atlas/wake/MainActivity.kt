package com.atlas.wake

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread
import org.json.JSONObject

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var statusText: TextView
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech

    private val atlasApiUrl =
        "https://atlas-wake.vercel.app/api/chat"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()

        textToSpeech = TextToSpeech(this, this)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        } else {
            setupSpeechRecognition()
        }
    }

    private fun createInterface() {

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
            setPadding(30, 30, 30, 30)
        }

        val title = TextView(this).apply {
            text = "A T L A S"
            textSize = 34f
            setTextColor(android.graphics.Color.CYAN)
            gravity = android.view.Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "ARTIFICIAL INTELLIGENCE ASSISTANT"
            textSize = 13f
            setTextColor(android.graphics.Color.LTGRAY)
            gravity = android.view.Gravity.CENTER
        }

        val core = TextView(this).apply {
            text = "●"
            textSize = 80f
            setTextColor(android.graphics.Color.CYAN)
            gravity = android.view.Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "ATLAS / SYSTEM READY"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
        }

        val wakeWord = TextView(this).apply {
            text = "\nWAKE WORD\nHEY ATLAS"
            textSize = 16f
            setTextColor(android.graphics.Color.CYAN)
            gravity = android.view.Gravity.CENTER
        }

        val footer = TextView(this).apply {
            text = "\nATLAS ONLINE • VOICE AI"
            textSize = 12f
            setTextColor(android.graphics.Color.GRAY)
            gravity = android.view.Gravity.CENTER
        }

        layout.addView(title)
        layout.addView(subtitle)

        layout.addView(
            core,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                250
            )
        )

        layout.addView(statusText)
        layout.addView(wakeWord)
        layout.addView(footer)

        setContentView(layout)
    }

    private fun setupSpeechRecognition() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = "SPEECH RECOGNITION UNAVAILABLE"
            return
        }

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    statusText.text = "LISTENING..."
                }

                override fun onBeginningOfSpeech() {
                    statusText.text = "HEARING YOU..."
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    statusText.text = "PROCESSING..."
                }

                override fun onError(error: Int) {
                    statusText.text = "ATLAS / READY"
                }

                override fun onResults(results: Bundle?) {

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val command =
                        matches?.firstOrNull()

                    if (!command.isNullOrBlank()) {
                        handleCommand(command)
                    } else {
                        statusText.text = "ATLAS / READY"
                    }
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
    }

    private fun listen() {

        if (!::speechRecognizer.isInitialized) {
            setupSpeechRecognition()
        }

        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.US
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
                )
            }

        speechRecognizer.startListening(intent)
    }

    private fun handleCommand(command: String) {

        val lowerCommand =
            command.lowercase(Locale.getDefault())

        when {

            lowerCommand.contains("hey atlas") -> {
                speak("Yes, I'm listening.")
            }

            lowerCommand.contains("hello") ||
            lowerCommand.contains("hi atlas") -> {
                speak("Hello. I am Atlas. How can I help you?")
            }

            lowerCommand.contains("what is your name") -> {
                speak("My name is Atlas.")
            }

            lowerCommand.contains("who are you") -> {
                speak(
                    "I am Atlas, your artificial intelligence assistant."
                )
            }

            else -> {
                askAtlas(command)
            }
        }
    }

    private fun askAtlas(message: String) {

        statusText.text = "ATLAS / THINKING..."

        thread {

            try {

                val url = URL(atlasApiUrl)

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )
                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 30000

                val requestBody =
                    JSONObject().apply {
                        put("message", message)
                    }.toString()

                connection.outputStream.use { output ->
                    output.write(
                        requestBody.toByteArray(Charsets.UTF_8)
                    )
                }

                val responseCode =
                    connection.responseCode

                val responseText =
                    if (response
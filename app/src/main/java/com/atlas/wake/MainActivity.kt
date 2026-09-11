package com.atlas.wake

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.sin
import org.json.JSONObject

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var statusText: TextView
    private lateinit var responseText: TextView
    private lateinit var orbView: AtlasOrbView
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech

    private var ttsReady = false
    private var restartingListening = false

    private val atlasApiUrl =
        "https://atlas-wake.vercel.app/api/chat"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()

        textToSpeech = TextToSpeech(this, this)

        textToSpeech.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {
                    runOnUiThread {
                        statusText.text = "ATLAS / SPEAKING"
                        orbView.setSpeaking(true)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        orbView.setSpeaking(false)
                        statusText.text = "ATLAS / LISTENING"
                        restartListening()
                    }
                }

                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        orbView.setSpeaking(false)
                        statusText.text = "ATLAS / TTS ERROR"
                        restartListening()
                    }
                }
            }
        )

        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
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
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(20, 20, 20, 20)
        }

        val title = TextView(this).apply {
            text = "ATLAS"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitle = TextView(this).apply {
            text = "ARTIFICIAL INTELLIGENCE"
            textSize = 12f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }

        orbView = AtlasOrbView(this)

        statusText = TextView(this).apply {
            text = "ATLAS / STARTING..."
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        responseText = TextView(this).apply {
            text = ""
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
        }

        val wakeWord = TextView(this).apply {
            text = "HEY ATLAS"
            textSize = 15f
            setTextColor(Color.rgb(255, 120, 20))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val footer = TextView(this).apply {
            text = "ATLAS AI • VOICE ASSISTANT"
            textSize = 11f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }

        layout.addView(title)

        layout.addView(
            subtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                40
            )
        )

        layout.addView(
            orbView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                360
            )
        )

        layout.addView(statusText)

        layout.addView(
            responseText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                130
            )
        )

        layout.addView(wakeWord)

        layout.addView(
            footer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                45
            )
        )

        setContentView(layout)
    }

    private fun setupSpeechRecognition() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = "ATLAS / SPEECH UNAVAILABLE"
            return
        }

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    runOnUiThread {
                        statusText.text = "ATLAS / LISTENING"
                    }
                }

                override fun onBeginningOfSpeech() {
                    runOnUiThread {
                        statusText.text = "ATLAS / HEARING YOU"
                    }
                }

                override fun onRmsChanged(rmsdB: Float) {
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                }

                override fun onEndOfSpeech() {
                    runOnUiThread {
                        statusText.text = "ATLAS / PROCESSING"
                    }
                }

                override fun onError(error: Int) {
                    restartListening()
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
                        restartListening()
                    }
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

        restartListening()
    }

    private fun restartListening() {

        if (isFinishing) return

        if (!::speechRecognizer.isInitialized) return

        if (restartingListening) return

        restartingListening = true

        runOnUiThread {

            statusText.text = "ATLAS / LISTENING"

            try {
                speechRecognizer.cancel()
            } catch (_: Exception) {
            }

            window.decorView.postDelayed({

                try {
                    startListening()
                } catch (_: Exception) {
                }

                restartingListening = false

            }, 350)
        }
    }

    private fun startListening() {

        if (!::speechRecognizer.isInitialized) return

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

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

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )
            }

        speechRecognizer.startListening(intent)
    }

    private fun handleCommand(
        originalCommand: String
    ) {

        val original =
            originalCommand.trim()

        var command =
            original

        val lower =
            command.lowercase(Locale.getDefault())

        if (lower.startsWith("hey atlas")) {

            command =
                command.substring(9).trim()

        } else if (lower.startsWith("atlas")) {

            command =
                command.substring(5).trim()
        }

        val clean =
            command.lowercase(Locale.getDefault())

        if (clean.isBlank()) {

            speak(
                "Yes, I'm listening."
            )

            return
        }

        if (
            clean == "hello" ||
            clean == "hi" ||
            clean.contains("hello atlas") ||
            clean.contains("good morning") ||
            clean.contains("good afternoon") ||
            clean.contains("good evening")
        ) {

            speak(
                "Hello Maxwell. How can I help you?"
            )

            return
        }

        if (
            clean.contains("what is your name") ||
            clean.contains("what's your name") ||
            clean == "who are you"
        ) {

            speak(
                "My name is ATLAS. I am your artificial intelligence assistant."
            )

            return
        }

        if (
            clean == "what time is it" ||
            clean.contains("tell me the time") ||
            clean.contains("what's the time") ||
            clean.contains("current time")
        ) {

            val time =
                SimpleDateFormat(
                    "h:mm a",
                    Locale.getDefault()
                ).format(Date())

            speak(
                "The time is $time."
            )

            return
        }

        if (
            clean.contains("today's date") ||
            clean.contains("what is today's date") ||
            clean.contains("what's today's date") ||
            clean.contains("what date is it")
        ) {

            val date =
                SimpleDateFormat(
                    "EEEE, d MMMM yyyy",
                    Locale.getDefault()
                ).format(Date())

            speak(
                "Today is $date."
            )

            return
        }

        if (
            clean.contains("battery") ||
            clean.contains("battery level") ||
            clean.contains("how much battery")
        ) {

            val batteryManager =
                getSystemService(
                    Context.BATTERY_SERVICE
                ) as BatteryManager

            val level =
                batteryManager.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY
                )

            speak(
                "Your battery is at $level percent."
            )

            return
        }

        if (
            clean == "open youtube" ||
            clean == "youtube"
        ) {

            openWebsite(
                "https://www.youtube.com"
            )

            speak(
                "Opening YouTube."
            )

            return
        }

        if (
            clean == "open google"
        ) {

            openWebsite(
                "https://www.google.com"
            )

            speak(
                "Opening Google."
            )

            return
        }

        if (
            clean.startsWith("search google for ")
        ) {

            val query =
                command.substringAfter(
                    "search google for "
                ).trim()

            searchGoogle(query)

            return
        }

        if (
            clean.startsWith("google ")
        ) {

            val query =
                command.substringAfter(
                    "google "
                ).trim()

            searchGoogle(query)

            return
        }

        if (
            clean == "open settings" ||
            clean == "settings"
        ) {

            try {

                startActivity(
                    Intent(
                        Settings.ACTION_SETTINGS
                    )
                )

                speak(
                    "Opening settings."
                )

            } catch (_: Exception) {

                speak(
                    "I couldn't open settings."
                )
            }

            return
        }

        if (
            clean == "stop" ||
            clean == "be quiet" ||
            clean == "stop talking"
        ) {

            textToSpeech.stop()

            orbView.setSpeaking(false)

            statusText.text =
                "ATLAS / LISTENING"

            restartListening()

            return
        }

        askAtlas(command)
    }

    private fun searchGoogle(
        query: String
    ) {

        if (query.isBlank()) {

            openWebsite(
                "https://www.google.com"
            )

            return
        }

        val encoded =
            Uri.encode(query)

        openWebsite(
            "https://www.google.com/search?q=$encoded"
        )

        speak(
            "Searching Google for $query."
        )
    }

    private fun openWebsite(
        url: String
    ) {

        try {

            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )
            )

        } catch (_: Exception) {

            speak(
                "I couldn't open that."
            )
        }
    }

    private fun askAtlas(
        message: String
    ) {

        runOnUiThread {

            statusText.text =
                "ATLAS / THINKING"

            responseText.text =
                ""
        }

        thread {

            var connection:
                    HttpURLConnection? = null

            try {

                val url =
                    URL(atlasApiUrl)

                connection =
                    url.openConnection()
                        as HttpURLConnection

                connection.requestMethod =
                    "POST"

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                connection.doOutput =
                    true

                connection.connectTimeout =
                    15000

                connection.readTimeout =
                    30000

                val request =
                    JSONObject().apply {
                        put(
                            "message",
                            message
                        )
                    }.toString()

                connection.outputStream.use { output ->

                    output.write(
                        request.toByteArray(
                            Charsets.UTF_8
                        )
                    )

                    output.flush()
                }

                val code =
                    connection.responseCode

                val body =
                    if (code in 200..299) {

                        connection.inputStream
                            .bufferedReader()
                            .use {
                                it.readText()
                            }

                    } else {

                        connection.errorStream
                            ?.bufferedReader()
                            ?.use {
                                it.readText()
                            }
                            ?: ""
                    }

                if (code !in 200..299) {

                    val error =
                        try {

                            JSONObject(body)
                                .optString(
                                    "error",
                                    "Server error"
                                )

                        } catch (_: Exception) {

                            "Server error $code"
                        }

                    runOnUiThread {

                        statusText.text =
                            "ATLAS / SERVER ERROR"

                        responseText.text =
                            error
                    }

                    return@thread
                }

                val json =
                    JSONObject(body)

                val reply =
                    json.optString(
                        "reply",
                        ""
                    )

                if (reply.isBlank()) {

                    runOnUiThread {

                        statusText.text =
                            "ATLAS / NO RESPONSE"

                        responseText.text =
                            "The AI returned no answer."
                    }

                    return@thread
                }

                runOnUiThread {

                    responseText.text =
                        reply

                    speak(reply)
                }

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "ATLAS / CONNECTION ERROR"

                    responseText.text =
                        e.message
                            ?: "Could not reach ATLAS server."
                }

            } finally {

                connection?.disconnect()
            }
        }
    }

    private fun speak(
        message: String
    ) {

        responseText.text =
            message

        if (!ttsReady) {
            return
        }

        val result =
            textToSpeech.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "ATLAS_RESPONSE"
            )

        if (result == TextToSpeech.ERROR) {

            statusText.text =
                "ATLAS / TTS ERROR"
        }
    }

    override fun onInit(
        status: Int
    ) {

        if (status != TextToSpeech.SUCCESS) {

            ttsReady = false

            statusText.text =
                "ATLAS / TTS ERROR"

            return
        }

        val languageResult =
            textToSpeech.setLanguage(
                Locale.US
            )

        textToSpeech.setSpeechRate(
            0.95f
        )

        textToSpeech.setPitch(
            1.0f
        )

        ttsReady =
            languageResult !=
                TextToSpeech.LANG_MISSING_DATA &&
            languageResult !=
                TextToSpeech.LANG_NOT_SUPPORTED

        if (ttsReady) {

            statusText.text =
                "ATLAS / ONLINE"

            speak(
                "ATLAS online."
            )

        } e
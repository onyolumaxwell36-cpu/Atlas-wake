package com.atlas.wake

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
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
import android.view.animation.DecelerateInterpolator
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
import org.json.JSONObject
import kotlin.math.sin

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

    // ---------------------------------------------------------
    // USER INTERFACE
    // ---------------------------------------------------------

    private fun createInterface() {

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(24, 30, 24, 24)
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
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
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
            setPadding(20, 25, 20, 25)
        }

        val wakeWord = TextView(this).apply {
            text = "HEY ATLAS"
            textSize = 15f
            setTextColor(Color.rgb(255, 120, 20))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
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
                45
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
                120
            )
        )

        layout.addView(wakeWord)

        layout.addView(
            footer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                50
            )
        )

        setContentView(layout)
    }

    // ---------------------------------------------------------
    // SPEECH RECOGNITION
    // ---------------------------------------------------------

    private fun setupSpeechRecognition() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {

            statusText.text =
                "ATLAS / SPEECH UNAVAILABLE"

            return
        }

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {
                    runOnUiThread {
                        statusText.text =
                            "ATLAS / LISTENING"
                    }
                }

                override fun onBeginningOfSpeech() {
                    runOnUiThread {
                        statusText.text =
                            "ATLAS / HEARING YOU"
                    }
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                }

                override fun onEndOfSpeech() {
                    runOnUiThread {
                        statusText.text =
                            "ATLAS / PROCESSING"
                    }
                }

                override fun onError(
                    error: Int
                ) {
                    runOnUiThread {
                        restartListening()
                    }
                }

                override fun onResults(
                    results: Bundle?
                ) {

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

        if (isFinishing) {
            return
        }

        if (restartingListening) {
            return
        }

        if (!::speechRecognizer.isInitialized) {
            return
        }

        restartingListening = true

        runOnUiThread {

            statusText.text =
                "ATLAS / LISTENING"

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

            }, 300)
        }
    }

    private fun startListening() {

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

    // ---------------------------------------------------------
    // COMMAND HANDLER
    // ---------------------------------------------------------

    private fun handleCommand(
        originalCommand: String
    ) {

        var command =
            originalCommand.trim()

        val lower =
            command.lowercase(Locale.getDefault())

        // Remove the wake word when it is present.
        if (lower.startsWith("hey atlas")) {

            command =
                command.substringAfter(
                    "hey atlas",
                    ""
                ).trim()

        } else if (lower.startsWith("atlas")) {

            command =
                command.substringAfter(
                    "atlas",
                    ""
                ).trim()
        }

        val clean =
            command.lowercase(Locale.getDefault())

        // -----------------------------------------------------
        // WAKE WORD ONLY
        // -----------------------------------------------------

        if (clean.isBlank()) {

            speak(
                "Yes, I'm listening."
            )

            return
        }

        // -----------------------------------------------------
        // GREETINGS
        // -----------------------------------------------------

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

        // -----------------------------------------------------
        // NAME
        // -----------------------------------------------------

        if (
            clean.contains("what is your name") ||
            clean.contains("what's your name") ||
            clean.contains("who are you")
        ) {

            speak(
                "My name is ATLAS. I am your artificial intelligence assistant."
            )

            return
        }

        // -----------------------------------------------------
        // TIME
        // -----------------------------------------------------

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

        // -----------------------------------------------------
        // DATE
        // -----------------------------------------------------

        if (
            clean.contains("what is today's date") ||
            clean.contains("what's today's date") ||
            clean.contains("what date is it") ||
            clean.contains("today's date")
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

        // -----------------------------------------------------
        // BATTERY
        // -----------------------------------------------------

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

        // -----------------------------------------------------
        // GOOGLE SEARCH
        // -----------------------------------------------------

        if (
            clean.startsWith("search google for ")
        ) {

            val query =
                command.substringAfter(
                    "search google for ",
                    ""
                ).trim()

            openGoogleSearch(query)

            return
        }

        if (
            clean.startsWith("google ")
        ) {

            val query =
                command.substringAfter(
                    "google ",
                    ""
                ).trim()

            openGoogleSearch(query)

            return
        }

        // -----------------------------------------------------
        // YOUTUBE
        // -----------------------------------------------------

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

        // -----------------------------------------------------
        // GOOGLE
        // -----------------------------------------------------

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

        // -----------------------------------------------------
        // SETTINGS
        // -----------------------------------------------------

        if (
            clean == "open settings" ||
            clean == "settings"
        ) {

            startActivity(
                Intent(
                    Settings.ACTION_SETTINGS
                )
            )

            speak(
                "Opening settings."
            )

            return
        }

        // -----------------------------------------------------
        // BACK TO HOME
        // -----------------------------------------------------

        if (
            clean == "go home" ||
            clean == "open home"
        ) {

            val homeIntent =
                Intent(
                    Intent.ACTION_MAIN
                ).apply {
                    addCategory(
                        Intent.CATEGORY_HOME
                    )
                }

            startActivity(homeIntent)

            return
        }

        // -----------------------------------------------------
        // STOP / QUIET
        // -----------------------------------------------------

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

        // -----------------------------------------------------
        // EVERYTHING ELSE → DEEPSEEK
        // -----------------------------------------------------

        askAtlas(command)
    }

    // ---------------------------------------------------------
    // GOOGLE
    // ---------------------------------------------------------

    private fun openGoogleSearch(
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

    // ---------------------------------------------------------
    // OPEN WEBSITE
    // ---------------------------------------------------------

    private fun openWebsite(
        url: String
    ) {

        try {

            val intent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )

            startActivity(intent)

        } catch (_: Exception) {

            speak(
                "I couldn't open that."
            )
        }
    }

    // ---------------------------------------------------------
    // DEEPSEEK
    // ---------------------------------------------------------

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

                val requestBody =
                    JSONObject().apply {
                        put(
                            "message",
                            message
                        )
                    }.toString()

                connection.outputStream.use { output ->

                    output.write(
                        requestBody.toByteArray(
                          
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
                            Charsets.UTF_8
                        )
                    )

                    output.flush()
                }

                val responseCode =
                    connection.responseCode

                val body =
                    if (
                        responseCode in 200..299
                    ) {

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

                if (
                    responseCode !in 200..299
                ) {

                    val errorMessage =
                        try {

                            JSONObject(body)
                                .optString(
                                    "error",
                                    "Server error"
                                )

                        } catch (_: Exception) {

                            "Server error $responseCode"
                        }

                    runOnUiThread {

                        statusText.text =
                            "ATLAS / SERVER ERROR"

                        responseText.text =
                            errorMessage
                    }

                    return@thread
                }

                val json =
                    JSONObject(body)

                val reply =
                    json.optString(
                        "reply"
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
                            ?: "Could not reach the server."
                }

            } finally {

                connection?.disconnect()
            }
        }
    }

    // ---------------------------------------------------------
    // TEXT TO SPEECH
    // ---------------------------------------------------------

    private fun speak(
        message: String
    ) {

        if (!ttsReady) {

            responseText.text =
                message

            return
        }

        responseText.text =
            message

        val result =
            textToSpeech.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "ATLAS_RESPONSE"
            )

        if (
            result == TextToSpeech.ERROR
        ) {

            statusText.text =
                "ATLAS / TTS ERROR"
        }
    }

    override fun onInit(
        status: Int
    ) {

        if (
            status == TextToSpeech.SUCCESS
        ) {

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

            } else {

                statusText.text =
                    "ATLAS / TTS LANGUAGE ERROR"
            }

        } else {

            ttsReady = false

            statusText.text =
                "ATLAS / TTS ERROR"
        }
    }

    // ---------------------------------------------------------
    // PERMISSION
    // ---------------------------------------------------------

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

        if (requestCode == 100) {

            if (
                grantResults.isNotEmpty() &&
                grantResults[0] ==
                PackageManager.PERMISSION_GRANTED
            ) {

                setupSpeechRecognition()

            } else {

                statusText.text =
                    "ATLAS / MICROPHONE DENIED"
            }
        }
    }

    // ---------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------

    override fun onDestroy() {

        if (
            ::speechRecognizer.isInitialized
        ) {

            speechRecognizer.cancel()
            speechRecognizer.destroy()
        }

        if (
            ::textToSpeech.isInitialized
        ) {

            textToSpeech.stop()
            textToSpeech.shutdown()
        }

        super.onDestroy()
    }

    // =========================================================
    // ATLAS ORANGE ORB
    // =========================================================

    class AtlasOrbView(
        context: Context
    ) : View(context) {

        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        private var speaking =
            false

        private var animationTime =
            0f

        private val orange =
            Color.rgb(255, 100, 10)

        private val brightOrange =
            Color.rgb(255, 170, 50)

        private val darkOrange =
            Color.rgb(120, 35, 0)

        private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface =
                    Typeface.create(
                        Typeface.DEFAULT,
                        Typeface.BOLD
                    )
            }

        init {
            setLayerType(
                View.LAYER_TYPE_SOFTWARE,
                null
            )
        }

        fun setSpeaking(
            value: Boolean
        ) {

            speaking = value

            if (value) {
                animateOrb()
            }

            invalidate()
        }

        private fun animateOrb() {

            if (!speaking) {
                return
            }

            animationTime += 0.08f

            invalidate()

            postDelayed(
                {
                    animateOrb()
                },
                30
            )
        }

        override fun onDraw(
            canvas: Canvas
        ) {

            super.onDraw(canvas)

            val cx =
                width / 2f

            val cy =
                height / 2f

            val baseRadius =
                minOf(
                    width,
                    height
                ) * 0.28f

            val pulse =
                if (speaking) {
                    sin(animationTime) * 18f
                } else {
                    0f
                }

            val radius =
                baseRadius + pulse

            // Outer glow
            paint.style =
                Paint.Style.FILL

            paint.shader = null

            paint.color =
                Color.argb(
                    35,
                    255,
                    100,
                    10
                )

            paint.setShadowLayer(
                80f,
                0f,
                0f,
                orange
            )

            canvas.drawCircle(
                cx,
                cy,
                radius + 15f,
                paint
            )

            paint.clearShadowLayer()

            // Main orb gradient
            paint.shader =
                RadialGradient(
                    cx - radius * 0.30f,
                    cy - radius * 0.35f,
                    radius * 1.35f,
                    intArrayOf(
                        brightOrange,
                        orange,
                        darkOrange,
                        Color.BLACK
                    ),
                    floatArrayOf(
                        0f,
                        0.35f,
                        0.75f,
                        1f
                    ),
                    Shader.TileMode.CLAMP
                )

            canvas.drawCircle(
                cx,
                cy,
                radius,
                paint
            )

            paint.shader = null

            // Inner glow
            paint.color =
                Color.argb(
                    90,
                    255,
                    140,
                    30
                )

            paint.setShadowLayer(
                35f,
                0f,
                0f,
                brightOrange
            )

            canvas.drawCircle(
                cx,
                cy,
                radius * 0.82f,
                paint
            )

            paint.clearShadowLayer()

            // Big M
            textPaint.textSize =
                radius * 0.72f

            textPaint.color =
                Color.WHITE

            canvas.drawText(
                "M",
                cx,
                cy -
                    (
                        textPaint.ascent() +
                        textPaint.descent()
                    ) / 2f,
                textPaint
            )

            // Atlas AI
            textPaint.textSize =
                18f

            textPaint.color =
                Color.rgb(
                    255,
                    150,
                    60
                )

            canvas.drawText(
                "Atlas AI",
                cx,
                cy + radius + 55f,
                textPaint
            )
        }
    }
}
package com.atlas.wake

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
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
import android.graphics.drawable.GradientDrawable
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var statusText: TextView
    private lateinit var responseText: TextView
    private lateinit var orbView: AtlasOrbView
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textToSpeech: TextToSpeech

    private var ttsReady = false
    private var restartingListening = false

    private val conversation = mutableListOf<ChatMessage>()

    private val atlasApiUrl =
        "https://atlas-wake.vercel.app/api/chat"

    data class ChatMessage(
        val role: String,
        val content: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()

        textToSpeech = TextToSpeech(this, this)

        textToSpeech.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {
                    runOnUiThread {
                        statusText.text = "ATLAS  •  SPEAKING"
                        orbView.setSpeaking(true)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        orbView.setSpeaking(false)
                        statusText.text = "ATLAS  •  LISTENING"
                        restartListening()
                    }
                }

                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        orbView.setSpeaking(false)
                        statusText.text = "ATLAS  •  TTS ERROR"
                        restartListening()
                    }
                }
            }
        )

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
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

    // ============================================================
    // HOLOGRAPHIC ATLAS INTERFACE
    // ============================================================

    private fun createInterface() {

        val root = LinearLayout(this)

        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL

        root.setPadding(
            18,
            28,
            18,
            18
        )

        val background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(2, 7, 18),
                Color.rgb(3, 18, 38),
                Color.rgb(1, 5, 15)
            )
        )

        root.background = background

        // --------------------------------------------------------
        // TOP HEADER
        // --------------------------------------------------------

        val header = LinearLayout(this)

        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL

        val brand = TextView(this)

        brand.text = "ATLAS"
        brand.textSize = 27f
        brand.setTextColor(
            Color.rgb(80, 220, 255)
        )

        brand.setTypeface(
            Typeface.create(
                "sans-serif",
                Typeface.BOLD
            )
        )

        brand.letterSpacing = 0.20f

        val online = TextView(this)

        online.text = "  ● ONLINE"
        online.textSize = 10f
        online.setTextColor(
            Color.rgb(60, 255, 190)
        )

        online.gravity = Gravity.CENTER_VERTICAL

        header.addView(
            brand,
            LinearLayout.LayoutParams(
                0,
                55,
                1f
            )
        )

        header.addView(
            online,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                55
            )
        )

        root.addView(header)

        // --------------------------------------------------------
        // TOP LINE
        // --------------------------------------------------------

        val line = View(this)

        line.setBackgroundColor(
            Color.rgb(20, 100, 145)
        )

        root.addView(
            line,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            )
        )

        // --------------------------------------------------------
        // HOLOGRAM LABEL
        // --------------------------------------------------------

        val hologramLabel = TextView(this)

        hologramLabel.text =
            "C T P   H O L O G R A P H I C   A V A T A R"

        hologramLabel.textSize = 9f

        hologramLabel.setTextColor(
            Color.rgb(40, 150, 200)
        )

        hologramLabel.gravity = Gravity.CENTER

        hologramLabel.letterSpacing = 0.08f

        val labelParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                38
            )

        labelParams.topMargin = 6

        root.addView(
            hologramLabel,
            labelParams
        )

        // --------------------------------------------------------
        // HOLOGRAPHIC AVATAR
        // --------------------------------------------------------

        orbView = AtlasOrbView(this)

        val orbParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                420
            )

        orbParams.gravity = Gravity.CENTER

        root.addView(
            orbView,
            orbParams
        )

        // --------------------------------------------------------
        // STATUS PANEL
        // --------------------------------------------------------

        statusText = TextView(this)

        statusText.text =
            "ATLAS  •  LISTENING"

        statusText.textSize = 13f

        statusText.setTextColor(
            Color.rgb(80, 220, 255)
        )

        statusText.gravity = Gravity.CENTER

        statusText.setTypeface(
            null,
            Typeface.BOLD
        )

        statusText.letterSpacing = 0.10f

        val statusBackground =
            GradientDrawable()

        statusBackground.setColor(
            Color.rgb(4, 30, 52)
        )

        statusBackground.setStroke(
            1,
            Color.rgb(20, 120, 170)
        )

        statusBackground.cornerRadius = 40f

        statusText.background =
            statusBackground

        val statusParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                48
            )

        statusParams.topMargin = 8

        root.addView(
            statusText,
            statusParams
        )

        // --------------------------------------------------------
        // RESPONSE PANEL
        // --------------------------------------------------------

        responseText = TextView(this)

        responseText.text =
            "Say \"Hey Atlas\" to begin."

        responseText.textSize = 15f

        responseText.setTextColor(
            Color.rgb(210, 240, 255)
        )

        responseText.gravity =
            Gravity.CENTER

        responseText.setPadding(
            18,
            12,
            18,
            12
        )

        responseText.maxLines = 4

        val responseBackground =
            GradientDrawable()

        responseBackground.setColor(
            Color.rgb(2, 14, 28)
        )

        responseBackground.setStroke(
            1,
            Color.rgb(12, 70, 105)
        )

        responseBackground.cornerRadius = 24f

        responseText.background =
            responseBackground

        val responseParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                105
            )

        responseParams.topMargin = 10

        root.addView(
            responseText,
            responseParams
        )

        // --------------------------------------------------------
        // WAKE WORD
        // --------------------------------------------------------

        val wakeWord = TextView(this)

        wakeWord.text =
            "MIC  •  HEY ATLAS  •  VOICE CONTROL"

        wakeWord.textSize = 9f

        wakeWord.setTextColor(
            Color.rgb(50, 130, 170)
        )

        wakeWord.gravity = Gravity.CENTER

        wakeWord.letterSpacing = 0.08f

        val wakeParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                35
            )

        wakeParams.topMargin = 5

        root.addView(
            wakeWord,
            wakeParams
        )

        setContentView(root)
    }

    // ============================================================
    // SPEECH RECOGNITION
    // ============================================================

    private fun setupSpeechRecognition() {

        if (
            !SpeechRecognizer.isRecognitionAvailable(
                this
            )
        ) {
            statusText.text =
                "ATLAS  •  SPEECH UNAVAILABLE"
            return
        }

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(
                this
            )

        speechRecognizer.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {
                    runOnUiThread {
                        statusText.text =
                            "ATLAS  •  LISTENING"
                    }
                }

                override fun onBeginningOfSpeech() {
                    runOnUiThread {
                        statusText.text =
                            "ATLAS  •  HEARING YOU"
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
                            "ATLAS  •  THINKING"
                    }
                }

                override fun onError(
                    error: Int
                ) {
                    restartListening()
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer
                                .RESULTS_RECOGNITION
                        )

                    val command =
                        matches?.firstOrNull()

                    if (
                        !command.isNullOrBlank()
                    ) {
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

        if (
            !::speechRecognizer.isInitialized
        ) {
            return
        }

        if (restartingListening) {
            return
        }

        restartingListening = true

        runOnUiThread {

            statusText.text =
                "ATLAS  •  LISTENING"

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

        if (
            !::speechRecognizer.isInitialized
        ) {
            return
        }

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
            Locale.US
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_PARTIAL_RESULTS,
            false
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_MAX_RESULTS,
            3
        )

        speechRecognizer.startListening(intent)
    }

    // ============================================================
    // COMMAND HANDLING
    // ============================================================

    private fun handleCommand(
        originalCommand: String
    ) {

        var command =
            originalCommand.trim()

        val lower =
            command.lowercase(
                Locale.getDefault()
            )

        if (
            lower.startsWith("hey atlas")
        ) {

            command =
                command.substring(9).trim()

        } else if (
            lower.startsWith("atlas")
        ) {

            command =
                command.substring(5).trim()
        }

        val clean =
            command.lowercase(
                Locale.getDefault()
            )

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
            clean.startsWith(
                "search google for "
            )
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
                "ATLAS  •  LISTENING"

            restartListening()

            return
        }

        askAtlas(command)
    }

    // ============================================================
    // GOOGLE
    // ============================================================

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

    // ============================================================
    // AI CONNECTION
    // ============================================================

    private fun askAtlas(
        message: String
    ) {

        runOnUiThread {

            statusText.text =
                "ATLAS  •  THINKING"

            responseText.text =
                "Processing..."
        }

        conversation.add(
            ChatMessage(
                "user",
                message
            )
        )

        while (
            conversation.size > 20
        ) {
            conversation.removeAt(0)
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

                connection.doOutput = true

                connection.connectTimeout =
                    15000

                connection.readTimeout =
                    30000

                val historyJson =
                    JSONArray()

                synchronized(
                    conversation
                ) {

                    for (
                        item in conversation
                    ) {

                        historyJson.put(
                            JSONObject().apply {

                                put(
                                    "role",
                                    item.role
                                )

                                put(
                                    "content",
                                    item.content
                                )
                            }
                        )
                    }
                }

                val request =
                    JSONObject().apply {

                        put(
                            "message",
                            message
                        )

                        put(
                            "history",
                            historyJson
                        )

                    }.toString()

                connection.outputStream.use {
                    output ->

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
                    if (
                        code in 200..299
                    ) {

                        connection
                            .inputStream
                            .bufferedReader()
                            .use {
                                it.readText()
                            }

                    } else {

                        connection
                            .errorStream
                            ?.bufferedReader()
                            ?.use {
                                it.readText()
                            }
                            ?: ""
                    }

                if (
                    code !in 200..299
                ) {

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

                    synchronized(
                        conversation
                    ) {

                        if (
                            conversation.isNotEmpty()
                        ) {

                            conversation.removeAt(
                                conversation.lastIndex
                            )
                        }
                    }

                    runOnUiThread {

                        statusText.text =
                            "ATLAS  •  SERVER ERROR"

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
                            "ATLAS  •  NO RESPONSE"

                        responseText.text =
                            "The AI returned no answer."
                    }

                    return@thread
                }

                synchronized(
                    conversation
                ) {

                    conversation.add(
                        ChatMessage(
                            "assistant",
                            reply
                        )
                    )

                    while (
                        conversation.size > 20
                    ) {

                        conversation.removeAt(0)
                    }
                }

                runOnUiThread {

                    responseText.text =
                        reply

                    speak(reply)
                }

            } catch (e: Exception) {

                synchronized(
                    conversation
                ) {

                    if (
                        conversation.isNotEmpty()
                    ) {

                        conversation.removeAt(
                            conversation.lastIndex
                        )
                    }
                }

                runOnUiThread {

                    statusText.text =
                        "ATLAS  •  CONNECTION ERROR"

                    responseText.text =
                        e.message
                            ?: "Could not reach ATLAS server."
                }

            } finally {

                connection?.disconnect()
            }
        }
    }

    // ============================================================
    // TEXT TO SPEECH
    // ============================================================

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

        if (
            result == TextToSpeech.ERROR
        ) {

            statusText.text =
                "ATLAS  •  TTS ERROR"
        }
    }

    override fun onInit(
        status: Int
    ) {

        if (
            status != TextToSpeech.SUCCESS
        ) {

            ttsReady = false

            statusText.text =
                "ATLAS  •  TTS ERROR"

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
                "ATLAS  •  ONLINE"

            speak(
                "ATLAS online."
            )

        } else {

            statusText.text =
                "ATLAS  •  TTS LANGUAGE ERROR"
        }
    }

    // ============================================================
    // MICROPHONE PERMISSION
    // ============================================================

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
                    "ATLAS  •  MICROPHONE DENIED"
            }
        }
    }

    // ============================================================
    // CLEANUP
    // ============================================================

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
}
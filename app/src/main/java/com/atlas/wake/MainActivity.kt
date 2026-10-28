
package com.atlas.wake

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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

    private var speechRecognizer: SpeechRecognizer? = null
    private var stopSpeechRecognizer: SpeechRecognizer? = null
    private lateinit var textToSpeech: TextToSpeech

    private var ttsReady = false
    private var isAtlasSpeaking = false
    private var restartingListening = false
    private var stopListenerRestartPending = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val conversation = mutableListOf<ChatMessage>()

    private val atlasApiUrl =
        "https://atlas-wake.vercel.app/api/chat"

    data class ChatMessage(
        val role: String,
        val content: String
    )

    private val restartStopListener = Runnable {
        stopListenerRestartPending = false

        if (isAtlasSpeaking && !isFinishing) {
            startStopCommandListener()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()

        textToSpeech = TextToSpeech(this, this)

        textToSpeech.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {
                    runOnUiThread {
                        isAtlasSpeaking = true
                        statusText.text = "ATLAS • SPEAKING"
                        orbView.setSpeaking(true)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        if (!isAtlasSpeaking) return@runOnUiThread

                        isAtlasSpeaking = false
                        stopListenerRestartPending = false
                        mainHandler.removeCallbacks(restartStopListener)

                        destroyStopCommandListener()

                        orbView.setSpeaking(false)
                        statusText.text = "ATLAS • LISTENING"

                        restartListening()
                    }
                }

                @Deprecated("Deprecated by Android")
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        isAtlasSpeaking = false
                        destroyStopCommandListener()
                        orbView.setSpeaking(false)
                        statusText.text = "ATLAS • LISTENING"
                        restartListening()
                    }
                }
            }
        )

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            setupSpeechRecognition()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        }
    }

    private fun createInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(20, 24, 20, 20)

            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.rgb(3, 8, 24),
                    Color.rgb(5, 18, 38),
                    Color.rgb(0, 0, 0)
                )
            )
        }

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }

        setContentView(scrollView)

        val title = TextView(this).apply {
            text = "ATLAS"
            textSize = 32f
            setTextColor(Color.rgb(0, 230, 255))
            typeface = Typeface.create(
                "sans-serif-black",
                Typeface.BOLD
            )
            gravity = Gravity.CENTER
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val subtitle = TextView(this).apply {
            text = "YOUR PERSONAL AI ASSISTANT"
            textSize = 11f
            letterSpacing = 0.2f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        root.addView(
            subtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        statusText = TextView(this).apply {
            text = "ATLAS • INITIALIZING"
            textSize = 14f
            setTextColor(Color.rgb(0, 255, 180))
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 8)
        }

        root.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        orbView = AtlasOrbView(this)

        root.addView(
            orbView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                360
            )
        )

        val responseHeading = TextView(this).apply {
            text = "ATLAS RESPONSE"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(0, 230, 255))
            setPadding(0, 12, 0, 8)
        }

        root.addView(responseHeading)

        responseText = TextView(this).apply {
            text = "Initializing ATLAS..."
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER_VERTICAL

            background = GradientDrawable().apply {
                setColor(Color.rgb(12, 25, 42))
                cornerRadius = 18f
                setStroke(1, Color.rgb(0, 130, 170))
            }
        }

        root.addView(
            responseText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val footer = TextView(this).apply {
            text = "Say “Atlas” followed by your command"
            textSize = 12f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 8)
        }

        root.addView(
            footer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    // ---------------------------------------------------------
    // MAIN SPEECH RECOGNITION
    // ---------------------------------------------------------

    private fun setupSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = "SPEECH RECOGNITION UNAVAILABLE"
            return
        }

        speechRecognizer?.destroy()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    if (!isAtlasSpeaking) {
                        statusText.text = "ATLAS • LISTENING"
                    }
                }

                override fun onBeginningOfSpeech() {
                    if (!isAtlasSpeaking) {
                        statusText.text = "ATLAS • HEARING YOU"
                    }
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (!isAtlasSpeaking) {
                        statusText.text = "ATLAS • PROCESSING"
                    }
                }

                override fun onError(error: Int) {
                    if (!isAtlasSpeaking) {
                        restartListening()
                    }
                }

                override fun onResults(results: Bundle?) {
                    if (isAtlasSpeaking) {
                        return
                    }

                    val matches = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                    val command = matches?.firstOrNull()

                    if (!command.isNullOrBlank()) {
                        handleCommand(command)
                    } else {
                        restartListening()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Normal commands are handled using final results.
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {}
            }
        )

        restartListening()
    }

    private fun createRecognitionIntent(
        partialResults: Boolean
    ): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.US.toLanguageTag()
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResults)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
    }

    private fun restartListening() {
        if (isFinishing || isAtlasSpeaking ||
            restartingListening || speechRecognizer == null
        ) {
            return
        }

        restartingListening = true

        mainHandler.postDelayed({
            try {
                if (!isFinishing && !isAtlasSpeaking) {
                    speechRecognizer?.cancel()
                    speechRecognizer?.startListening(
                        createRecognitionIntent(true)
                    )
                }
            } catch (_: Exception) {
                // Recognition will be retried below.
            } finally {
                restartingListening = false
            }
        }, 400)
    }

    // ---------------------------------------------------------
    // STOP-WHILE-SPEAKING LISTENER
    // ---------------------------------------------------------

    private fun startStopCommandListener() {
        if (!isAtlasSpeaking || isFinishing) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        try {
            stopSpeechRecognizer?.destroy()

            stopSpeechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(this)

            stopSpeechRecognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {}

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        scheduleStopListenerRestart()
                    }

                    override fun onResults(results: Bundle?) {
                        if (!isAtlasSpeaking) return

                        val matches = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        val heard = matches?.firstOrNull()
                            ?.lowercase(Locale.US)
                            ?.trim()
                            .orEmpty()

                        if (isStopCommand(heard)) {
                            stopAtlasSpeaking()
                        } else {
                            scheduleStopListenerRestart()
                        }
                    }

                    override fun onPartialResults(
                        partialResults: Bundle?
                    ) {
                        if (!isAtlasSpeaking) return

                        val matches = partialResults?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        val heard = matches?.firstOrNull()
                            ?.lowercase(Locale.US)
                            ?.trim()
                            .orEmpty()

                        if (isStopCommand(heard)) {
                            stopAtlasSpeaking()
                        }
                    }

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) {}
                }
            )

            stopSpeechRecognizer?.startListening(
                createRecognitionIntent(true)
            )

        } catch (_: Exception) {
            scheduleStopListenerRestart()
        }
    }

    private fun isStopCommand(heard: String): Boolean {
        val normalized = heard
            .replace(Regex("[^a-z ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return normalized in setOf(
            "stop",
            "stop talking",
            "atlas stop",
            "hey atlas stop",
            "be quiet",
            "atlas be quiet",
            "hey atlas be quiet",
            "quiet",
            "stop atlas",
            "stop speaking",
            "atlas stop talking"
        )
    }

    private fun scheduleStopListenerRestart() {
        if (!isAtlasSpeaking || isFinishing ||
            stopListenerRestartPending
        ) {
            return
        }

        stopListenerRestartPending = true

        mainHandler.postDelayed(
            restartStopListener,
            500
        )
    }

    private fun stopAtlasSpeaking() {
        if (!isAtlasSpeaking) return

        // Set the flag first so that the TTS completion callback
        // cannot restart the stop listener after interruption.
        isAtlasSpeaking = false

        mainHandler.removeCallbacks(restartStopListener)
        stopListenerRestartPending = false

        textToSpeech.stop()

        try {
            stopSpeechRecognizer?.cancel()
        } catch (_: Exception) {}

        destroyStopCommandListener()

        orbView.setSpeaking(false)
        statusText.text = "ATLAS • LISTENING"
        responseText.text = "Okay, I stopped speaking."

        restartListening()
    }

    private fun destroyStopCommandListener() {
        try {
            stopSpeechRecognizer?.cancel()
            stopSpeechRecognizer?.destroy()
        } catch (_: Exception) {}

        stopSpeechRecognizer = null
    }

    // ---------------------------------------------------------
    // COMMAND HANDLING
    // ---------------------------------------------------------

    private fun handleCommand(originalCommand: String) {
        val command = originalCommand.trim()
        val clean = command
            .replace(
                Regex("^(hey\\s+atlas|atlas)[,\\s]*", RegexOption.IGNORE_CASE),
                ""
            )
            .trim()
            .lowercase(Locale.US)

        if (clean.isBlank()) {
            speak("Yes, I'm listening.")
            return
        }

        when {
            clean in setOf(
                "stop",
                "stop talking",
                "be quiet",
                "quiet",
                "stop speaking"
            ) -> {
                stopAtlasSpeaking()
                return
            }

            clean in setOf(
                "hello",
                "hi",
                "hello atlas",
                "good morning",
                "good afternoon",
                "good evening"
            ) -> {
                speak("Hello! I'm ATLAS. How can I help you?")
                return
            }

            clean.contains("your name") ||
                clean == "who are you" -> {
                speak("My name is ATLAS, your personal AI assistant.")
                return
            }

            clean.contains("what time") ||
                clean == "time" ||
                clean.contains("current time") -> {
                val time = SimpleDateFormat(
                    "h:mm a",
                    Locale.US
                ).format(Date())

                speak("The time is $time.")
                return
            }

            clean.contains("what date") ||
                clean.contains("today's date") ||
                clean == "date" -> {
                val date = SimpleDateFormat(
                    "EEEE, MMMM d, yyyy",
                    Locale.US
                ).format(Date())

                speak("Today is $date.")
                return
            }

            clean.contains("battery") -> {
                val manager = getSystemService(BATTERY_SERVICE)
                    as BatteryManager

                val level = manager.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY
                )

                speak("Your battery level is $level percent.")
                return
            }

            clean == "open youtube" ||
                clean == "youtube" -> {
                openWebsite("https://www.youtube.com")
                return
            }

            clean == "open google" -> {
                openWebsite("https://www.google.com")
                return
            }

            clean.startsWith("search for ") ||
                clean.startsWith("search ") ||
                clean.startsWith("google ") -> {
                val query = clean
                    .removePrefix("search for ")
                    .removePrefix("search ")
                    .removePrefix("google ")
                    .trim()

                if (query.isNotBlank()) {
                    searchGoogle(query)
                } else {
                    speak("What would you like me to search for?")
                }

                return
            }

            clean == "open settings" ||
                clean == "settings" -> {
                try {
                    startActivity(
                        Intent(Settings.ACTION_SETTINGS)
                    )
                } catch (_: Exception) {
                    speak("I couldn't open Settings.")
                }
                return
            }
        }

        askAtlas(command)
    }

    private fun searchGoogle(query: String) {
        val url = "https://www.google.com/search?q=" +
            Uri.encode(query)

        openWebsite(url)
    }

    private fun openWebsite(url: String) {
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )
            )
        } catch (_: Exception) {
            speak("I couldn't open that website.")
        }
    }

    // ---------------------------------------------------------
    // GROQ BACKEND
    // ---------------------------------------------------------

    private fun askAtlas(message: String) {
        runOnUiThread {
            statusText.text = "ATLAS • THINKING"
            responseText.text = "Processing your request..."
        }

        synchronized(conversation) {
            conversation.add(
                ChatMessage("user", message)
            )

            while (conversation.size > 20) {
                conversation.removeAt(0)
            }
        }

        thread {
            var connection: HttpURLConnection? = null

            try {
                connection = (
                    URL(atlasApiUrl).openConnection()
                    as HttpURLConnection
                ).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true

                    setRequestProperty(
                        "Content-Type",
                        "application/json"
                    )

                    setRequestProperty(
                        "Accept",
                        "application/json"
                    )
                }

                val history = JSONArray()

                synchronized(conversation) {
                    conversation.forEach { item ->
                        history.put(
                            JSONObject().apply {
                                put("role", item.role)
                                put("content", item.content)
                            }
                        )
                    }
                }

                val requestBody = JSONObject().apply {
                    put("message", message)
                    put("history", history)
                }

                connection.outputStream.use { output ->
                    output.write(
                        requestBody.toString().toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }

                val responseCode = connection.responseCode

                val responseStream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val responseBody = responseStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()

                val json = JSONObject(
                    responseBody.ifBlank { "{}" }
                )

                if (responseCode !in 200..299) {
                    val error = json.optString(
                        "error",
                        "Server returned HTTP $responseCode"
                    )

                    runOnUiThread {
                        statusText.text = "ATLAS • SERVER ERROR"
                        responseText.text = error
                    }

                    synchronized(conversation) {
                        if (conversation.lastOrNull()?.role == "user") {
                            conversation.removeAt(conversation.lastIndex)
                        }
                    }

                    return@thread
                }

                val reply = json.optString(
                    "reply",
                    "I received an empty response from my server."
                )

                synchronized(conversation) {
                    conversation.add(
                        ChatMessage("assistant", reply)
                    )

                    while (conversation.size > 20) {
                        conversation.removeAt(0)
                    }
                }

                runOnUiThread {
                    speak(reply)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "ATLAS • CONNECTION ERROR"
                    responseText.text =
                        "I couldn't connect to my server. " +
                        "Please check your internet connection."
                }

            } finally {
                connection?.disconnect()
            }
        }
    }

    // ---------------------------------------------------------
    // TEXT TO SPEECH
    // ---------------------------------------------------------

    private fun speak(message: String) {
        runOnUiThread {
            responseText.text = message

            if (!ttsReady) {
                statusText.text = "ATLAS • VOICE NOT READY"
                return@runOnUiThread
            }

            // Stop any previous speech before starting another reply.
            isAtlasSpeaking = false
            mainHandler.removeCallbacks(restartStopListener)
            stopListenerRestartPending = false
            destroyStopCommandListener()

            val result = textToSpeech.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "ATLAS_RESPONSE"
            )

            if (result == TextToSpeech.ERROR) {
                statusText.text = "ATLAS • TTS ERROR"
                orbView.setSpeaking(false)
            } else {
                statusText.text = "ATLAS • PREPARING VOICE"
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val languageResult = textToSpeech.setLanguage(Locale.US)

            textToSpeech.setSpeechRate(0.95f)
            textToSpeech.setPitch(1.0f)

            ttsReady =
                languageResult != TextToSpeech.LANG_MISSING_DATA &&
                languageResult != TextToSpeech.LANG_NOT_SUPPORTED

            runOnUiThread {
                if (ttsReady) {
                    statusText.text = "ATLAS • ONLINE"
                    speak("ATLAS online.")
                } else {
                    statusText.text = "ATLAS • VOICE UNAVAILABLE"
                    responseText.text =
                        "The selected text-to-speech language is unavailable."
                }
            }
        } else {
            runOnUiThread {
                statusText.text = "ATLAS • TTS INITIALIZATION FAILED"
            }
        }
    }

    // ---------------------------------------------------------
    // PERMISSIONS AND CLEANUP
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

    if (requestCode == 100 &&
        grantResults.isNotEmpty() &&
        grantResults[0] == PackageManager.PERMISSION_GRANTED
    ) {
        setupSpeechRecognition()
    } else if (requestCode == 100) {
        statusText.text = "ATLAS • MICROPHONE PERMISSION REQUIRED"
    }
}

    override fun onDestroy() {
        isAtlasSpeaking = false

        mainHandler.removeCallbacksAndMessages(null)

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}

        try {
            stopSpeechRecognizer?.cancel()
            stopSpeechRecognizer?.destroy()
        } catch (_: Exception) {}

        speechRecognizer = null
        stopSpeechRecognizer = null

        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }

        super.onDestroy()
    }
}

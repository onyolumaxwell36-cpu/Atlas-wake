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
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.widget.LinearLayout
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
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var orb: AtlasOrbView

    private var wakeWordEngine: WakeWordEngine? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private val handler = Handler(Looper.getMainLooper())

    private var ttsReady = false
    private var speaking = false
    private var listeningForCommand = false

    companion object {
        private const val MICROPHONE_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()

        textToSpeech = TextToSpeech(
            this,
            object : TextToSpeech.OnInitListener {
                override fun onInit(result: Int) {

                    if (result == TextToSpeech.SUCCESS) {

                        val languageResult =
                            textToSpeech?.setLanguage(
                                Locale.getDefault()
                            )

                        ttsReady =
                            languageResult != TextToSpeech.LANG_MISSING_DATA &&
                            languageResult != TextToSpeech.LANG_NOT_SUPPORTED

                        textToSpeech?.setSpeechRate(0.95f)
                        textToSpeech?.setPitch(0.95f)

                        textToSpeech?.setOnUtteranceProgressListener(
                            object : android.speech.tts.UtteranceProgressListener() {

                                override fun onStart(
                                    utteranceId: String?
                                ) {
                                    runOnUiThread {
                                        speaking = true
                                        orb.setMode(
                                            AtlasOrbView.MODE_SPEAKING
                                        )
                                    }
                                }

                                override fun onDone(
                                    utteranceId: String?
                                ) {
                                    runOnUiThread {
                                        speaking = false
                                        restartWakeWord()
                                    }
                                }

                                override fun onError(
                                    utteranceId: String?
                                ) {
                                    runOnUiThread {
                                        speaking = false
                                        restartWakeWord()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        )

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO
                ),
                MICROPHONE_REQUEST
            )

        } else {

            startWakeWordDetection()
        }
    }

    private fun createInterface() {

        val root = LinearLayout(this)

        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(
            30,
            50,
            30,
            50
        )

        orb = AtlasOrbView(this)

        root.addView(
            orb,
            LinearLayout.LayoutParams(
                420,
                420
            )
        )

        status = TextView(this)

        status.text =
            "ATLAS\n\nStarting..."

        status.textSize = 20f
        status.gravity = Gravity.CENTER
        status.setPadding(
            20,
            30,
            20,
            20
        )

        root.addView(
            status,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    private fun updateStatus(
        message: String,
        mode: Int = AtlasOrbView.MODE_IDLE
    ) {

        status.text = message
        orb.setMode(mode)
    }

    private fun startWakeWordDetection() {

        if (speaking || listeningForCommand) {
            return
        }

        updateStatus(
            "ATLAS\n\nListening for:\nHEY JARVIS",
            AtlasOrbView.MODE_LISTENING
        )

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

                wakeWordEngine?.detections?.collect {

                    if (speaking || listeningForCommand) {
                        return@collect
                    }

                    listeningForCommand = true

                    stopWakeWord()

                    updateStatus(
                        "ATLAS\n\n" +
                        "HEY JARVIS DETECTED!\n\n" +
                        "I'm listening...",
                        AtlasOrbView.MODE_LISTENING
                    )

                    listenForCommand()
                }
            }

            wakeWordEngine?.start()

        } catch (e: Exception) {

            updateStatus(
                "ATLAS\n\n" +
                "Wake-word error:\n\n" +
                e.message
            )

            handler.postDelayed(
                {
                    if (!speaking) {
                        startWakeWordDetection()
                    }
                },
                1500
            )
        }
    }

    private fun stopWakeWord() {

        try {
            wakeWordEngine?.release()
        } catch (_: Exception) {
        }

        wakeWordEngine = null
    }

    private fun listenForCommand() {

        if (
            !SpeechRecognizer.isRecognitionAvailable(
                this
            )
        ) {

            speak(
                "Speech recognition is not available on this phone."
            )

            return
        }

        speechRecognizer?.destroy()

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {

                    updateStatus(
                        "ATLAS\n\n" +
                        "I'm listening...",
                        AtlasOrbView.MODE_LISTENING
                    )
                }

                override fun onBeginningOfSpeech() {

                    updateStatus(
                        "ATLAS\n\n" +
                        "I'm hearing you...",
                        AtlasOrbView.MODE_LISTENING
                    )
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {

                    orb.setAudioLevel(rmsdB)
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                }

                override fun onEndOfSpeech() {

                    updateStatus(
                        "ATLAS\n\nProcessing...",
                        AtlasOrbView.MODE_SPEAKING
                    )
                }

                override fun onError(
                    error: Int
                ) {

                    listeningForCommand = false

                    updateStatus(
                        "ATLAS\n\n" +
                        "I didn't catch that.\n\n" +
                        "Try again."
                    )

                    restartWakeWord()
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    val resultsList =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val command =
                        resultsList
                            ?.firstOrNull()
                            ?.lowercase(
                                Locale.getDefault()
                            )
                            ?.trim()
                            ?: ""

                    listeningForCommand = false

                    if (command.isBlank()) {

                        speak(
                            "I didn't hear your command."
                        )

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

        try {

            speechRecognizer?.startListening(
                intent
            )

        } catch (e: Exception) {

            listeningForCommand = false

            speak(
                "I could not start listening."
            )
        }
    }

    private fun handleCommand(
        command: String
    ) {

        when {

            command.contains("open whatsapp") ||
            command.contains("launch whatsapp") ||
            command.contains("start whatsapp") ||
            command.contains("open my whatsapp") ||
            command.contains("go to whatsapp") -> {

                openWhatsApp()
            }

            command.contains("what is the time") ||
            command.contains("what's the time") ||
            command.contains("tell me the time") ||
            command.contains("time now") ||
            command.contains("current time") ||
            command == "time" -> {

                tellTime()
            }

            command.startsWith("search for ") -> {

                val topic =
                    command
                        .removePrefix(
                            "search for "
                        )
                        .trim()

                searchWeb(topic)
            }

            command.startsWith("search ") -> {

                val topic =
                    command
                        .removePrefix(
                            "search "
                        )
                        .trim()

                searchWeb(topic)
            }

            command.contains(
                "search the web for "
            ) -> {

                val topic =
                    command.substringAfter(
                        "search the web for "
                    ).trim()

                searchWeb(topic)
            }

            command.contains(
                "search the internet for "
            ) -> {

                val topic =
                    command.substringAfter(
                        "search the internet for "
                    ).trim()

                searchWeb(topic)
            }

            command.startsWith("google ") -> {

                val topic =
                    command
                        .removePrefix(
                            "google "
                        )
                        .trim()

                searchWeb(topic)
            }

            else -> {

                updateStatus(
                    "ATLAS\n\n" +
                    "I heard:\n\n" +
                    "\"$command\"\n\n" +
                    "I don't know that command yet."
                )

                speak(
                    "I heard $command, but I don't know that command yet."
                )
            }
        }
    }

    private fun openWhatsApp() {

        updateStatus(
            "ATLAS\n\nOpening WhatsApp...",
            AtlasOrbView.MODE_SPEAKING
        )

        val packages =
            listOf(
                "com.whatsapp",
                "com.whatsapp.w4b"
            )

        var whatsappIntent: Intent? = null

        for (packageName in packages) {

            try {

                val launchIntent =
                    packageManager.getLaunchIntentForPackage(
                        packageName
                    )

                if (launchIntent != null) {

                    whatsappIntent = launchIntent
                    break
                }

            } catch (_: Exception) {
            }
        }

        if (whatsappIntent != null) {

            speak(
                "Opening WhatsApp."
            ) {

                try {
                    startActivity(
                        whatsappIntent
                    )
                } catch (_: Exception) {
                }
            }

        } else {

            speak(
                "I couldn't find WhatsApp on this phone."
            )
        }
    }

    private fun tellTime() {

        val currentTime =
            SimpleDateFormat(
                "h:mm a",
                Locale.getDefault()
            ).format(Date())

        updateStatus(
            "ATLAS\n\n" +
            "The time is:\n\n" +
            currentTime,
            AtlasOrbView.MODE_SPEAKING
        )

        speak(
            "The time is $currentTime."
        )
    }

    private fun searchWeb(
        topic: String
    ) {

        if (topic.isBlank()) {

            speak(
                "Tell me what you want me to search for."
            )

            return
        }

        updateStatus(
            "ATLAS\n\n" +
            "Searching for:\n\n" +
            topic,
            AtlasOrbView.MODE_SPEAKING
        )

        speak(
            "Searching the web for $topic."
        ) {

            try {

                val url =
                    "https://www.google.com/search?q=" +
                    Uri.encode(topic)

                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(url)
                    )
                )

            } catch (_: Exception) {

                speak(
                    "I couldn't open the browser."
                )
            }
        }
    }

    private fun speak(
        text: String,
        afterSpeech: (() -> Unit)? = null
    ) {

        if (!ttsReady) {

            updateStatus(
                "ATLAS\n\n$text"
            )

            restartWakeWord()
            return
        }

        speaking = true

        try {

            speechRecognizer?.cancel()
            speechRecognizer?.destroy()

        } catch (_: Exception) {
        }

        speechRecognizer = null

        updateStatus(
            "ATLAS\n\n$text",
            AtlasOrbView.MODE_SPEAKING
        )

        val utteranceId =
            "ATLAS_" +
            System.currentTimeMillis()

        pendingAction = afterSpeech

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )
    }

    private var pendingAction:
        (() -> Unit)? = null

    private fun restartWakeWord() {

        listeningForCommand = false

        handler.postDelayed(
            {

                if (
                    !isFinishing &&
                    !isDestroyed &&
                    !speaking
                ) {

                    startWakeWordDetection()
                }

            },
            800
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

            updateStatus(
                "ATLAS\n\n" +
                "Microphone permission denied."
            )
        }
    }

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(null)

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        try {
            wakeWordEngine?.release()
        } catch (_: Exception) {
        }

        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {
        }

        speechRecognizer = null
        wakeWordEngine = null
        textToSpeech = null

        super.onDestroy()
    }

    class AtlasOrbView(
        context: android.content.Context
    ) : View(context) {

        companion object {

            const val MODE_IDLE = 0
            const val MODE_LISTENING = 1
            const val MODE_SPEAKING = 2
        }

        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        private var mode =
            MODE_IDLE

        private var audioLevel =
            0f

        private var animationTime =
            0f

        private val handler =
            Handler(Looper.getMainLooper())

        private val animationRunnable =
            object : Runnable {

                override fun run() {

                    animationTime += 0.08f

                    invalidate()

                    handler.postDelayed(
                        this,
                        30L
                    )
                }
            }

        init {

            paint.isAntiAlias = true

            handler.post(
                animationRunnable
            )
        }

        fun setMode(
            newMode: Int
        ) {

            mode = newMode

            invalidate()
        }

        fun setAudioLevel(
            level: Float
        ) {

            audioLevel =
                level.coerceIn(
                    -10f,
                    10f
                )

            invalidate()
        }

        override fun onDraw(
            canvas: Canvas
        ) {

            super.onDraw(canvas)

            val centerX =
                width / 2f

            val centerY =
                height / 2f

            val baseRadius =
                minOf(
                    width,
                    height
                ) * 0.20f

            val pulse =
                when (mode) {

                    MODE_LISTENING ->
                        1f +
                        0.08f *
                        sin(
                            animationTime * 2f
                        )

                    MODE_SPEAKING ->
                        1f +
                        0.18f *
                        sin(
                            animationTime * 5f
                        )

                    else ->
                        1f +
                        0.03f *
                        sin(
                            animationTime
                        )
                }

            val audioPulse =
                if (
                    mode ==
                    MODE_LISTENING
                ) {

                    (audioLevel + 10f) /
                    100f

                } else {

                    0f
                }

            val radius =
                baseRadius *
                pulse *
                (1f + audioPulse)

            val gradient =
                RadialGradient(
                    centerX,
                    centerY,
                    radius * 2.3f,
                    intArrayOf(
                        0xFFFFFFFF.toInt(),
                        0xFF00BFFF.toInt(),
                        0xFF0066FF.toInt(),
                        0x000066FF
                    ),
                    floatArrayOf(
                        0f,
                        0.25f,
                        0.55f,
                        1f
                    ),
                    Shader.TileMode.CLAMP
                )

            paint.shader = gradient

            canvas.drawCircle(
                centerX,
                centerY,
                radius * 2.1f,
                paint
            )

            paint.shader = null

            paint.color =
                0xFF00BFFF.toInt()

            canvas.drawCircle(
                centerX,
                centerY,
                radius,
                paint
            )

            paint.color =
                0xFFFFFFFF.toInt()

            canvas.drawCircle(
                centerX,
                centerY,
                radius * 0.55f,
                paint
            )

            paint.color =
                0xFF0088FF.toInt()

            canvas.drawCircle(
                centerX,
                centerY,
                radius * 0.35f,
                paint
            )
        }
    }
}

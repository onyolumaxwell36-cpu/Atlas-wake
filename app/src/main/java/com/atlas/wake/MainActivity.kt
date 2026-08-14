package com.atlas.wake

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.lifecycle.lifecycleScope
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.DetectionMode
import com.rementia.openwakeword.lib.model.WakeWordModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var orb: AtlasOrbView

    private var wakeWordEngine: WakeWordEngine? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private var detectionJob: Job? = null

    private val handler = Handler(Looper.getMainLooper())

    private var ttsReady = false
    private var speaking = false
    private var listeningForCommand = false

    private var conversationMode = false
    private var commandRetryCount = 0

    companion object {
        private const val MICROPHONE_REQUEST = 100
        private const val WAKE_THRESHOLD = 0.40f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()
        initializeTextToSpeech()

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MICROPHONE_REQUEST
            )
        } else {
            startWakeWordDetection()
        }
    }

    // ============================================================
    // JARVIS INTERFACE
    // ============================================================

    private fun createInterface() {

        val root = LinearLayout(this)

        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER

        root.setBackgroundColor(Color.BLACK)

        root.setPadding(
            24,
            45,
            24,
            35
        )

        // --------------------------------------------------------
        // ATLAS TITLE
        // --------------------------------------------------------

        val title = TextView(this)

        title.text = "A T L A S"
        title.textSize = 30f
        title.gravity = Gravity.CENTER
        title.setTextColor(Color.CYAN)
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD))
        title.letterSpacing = 0.18f

        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // --------------------------------------------------------
        // SUBTITLE
        // --------------------------------------------------------

        val subtitle = TextView(this)

        subtitle.text = "ARTIFICIAL INTELLIGENCE ASSISTANT"
        subtitle.textSize = 10f
        subtitle.gravity = Gravity.CENTER
        subtitle.setTextColor(
            Color.rgb(70, 180, 220)
        )
        subtitle.letterSpacing = 0.12f

        val subtitleParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        subtitleParams.setMargins(
            0,
            6,
            0,
            15
        )

        root.addView(
            subtitle,
            subtitleParams
        )

        // --------------------------------------------------------
        // GLOWING ORB
        // --------------------------------------------------------

        orb = AtlasOrbView(this)

        root.addView(
            orb,
            LinearLayout.LayoutParams(
                560,
                560
            )
        )

        // --------------------------------------------------------
        // STATUS
        // --------------------------------------------------------

        status = TextView(this)

        status.text =
            "ATLAS\n\nStarting..."

        status.textSize = 19f
        status.gravity = Gravity.CENTER

        status.setTextColor(
            Color.rgb(0, 229, 255)
        )

        status.setTypeface(
            Typeface.create(
                "sans-serif",
                Typeface.NORMAL
            )
        )

        status.setPadding(
            20,
            20,
            20,
            15
        )

        root.addView(
            status,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // --------------------------------------------------------
        // BOTTOM HUD LABEL
        // --------------------------------------------------------

        val footer = TextView(this)

        footer.text = "VOICE CONTROL • ATLAS ONLINE"
        footer.textSize = 9f
        footer.gravity = Gravity.CENTER
        footer.setTextColor(
            Color.rgb(35, 110, 140)
        )
        footer.letterSpacing = 0.08f

        root.addView(
            footer,
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

        runOnUiThread {

            status.text = message

            orb.setMode(mode)
        }
    }

    // ============================================================
    // TEXT TO SPEECH
    // ============================================================

    private fun initializeTextToSpeech() {

        textToSpeech =
            TextToSpeech(
                this
            ) { result ->

                if (
                    result !=
                    TextToSpeech.SUCCESS
                ) {

                    ttsReady = false

                    return@TextToSpeech
                }

                val languageResult =
                    textToSpeech?.setLanguage(
                        Locale.getDefault()
                    )

                ttsReady =
                    languageResult !=
                        TextToSpeech.LANG_MISSING_DATA &&
                    languageResult !=
                        TextToSpeech.LANG_NOT_SUPPORTED

                textToSpeech?.setSpeechRate(
                    0.95f
                )

                textToSpeech?.setPitch(
                    0.95f
                )

                textToSpeech?.setOnUtteranceProgressListener(

                    object :
                        UtteranceProgressListener() {

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

                                if (
                                    conversationMode
                                ) {

                                    handler.postDelayed(
                                        {

                                            if (
                                                !isFinishing &&
                                                !isDestroyed
                                            ) {

                                                listenForCommand()
                                            }

                                        },
                                        350L
                                    )

                                } else {

                                    restartWakeWord()
                                }
                            }
                        }

                        override fun onError(
                            utteranceId: String?
                        ) {

                            runOnUiThread {

                                speaking = false

                                if (
                                    conversationMode
                                ) {

                                    listenForCommand()

                                } else {

                                    restartWakeWord()
                                }
                            }
                        }
                    }
                )
            }
    }

    private fun speak(
        text: String,
        continueConversation: Boolean = false,
        afterSpeech: (() -> Unit)? = null
    ) {

        conversationMode =
            continueConversation

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

        if (!ttsReady) {

            afterSpeech?.invoke()

            if (
                conversationMode
            ) {

                listenForCommand()

            } else {

                restartWakeWord()
            }

            return
        }

        val utteranceId =
            "ATLAS_" +
            System.currentTimeMillis()

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )

        if (
            afterSpeech != null
        ) {

            handler.postDelayed(
                {

                    try {
                        afterSpeech.invoke()
                    } catch (_: Exception) {
                    }

                },
                900L
            )
        }
    }

    // ============================================================
    // WAKE WORD
    // ============================================================

    private fun startWakeWordDetection() {

        if (
            speaking ||
            listeningForCommand ||
            isFinishing ||
            isDestroyed
        ) {
            return
        }

        updateStatus(
            "ATLAS\n\nListening for:\nHEY JARVIS",
            AtlasOrbView.MODE_LISTENING
        )

        val models =
            listOf(
                WakeWordModel(
                    name = "Hey Jarvis",
                    modelPath =
                        "hey_jarvis_v0.1.onnx",
                    threshold =
                        WAKE_THRESHOLD
                )
            )

        try {

            detectionJob?.cancel()
            detectionJob = null

            wakeWordEngine?.release()
            wakeWordEngine = null

            wakeWordEngine =
                WakeWordEngine(
                    context = this,
                    models = models,
                    detectionMode =
                        DetectionMode.SINGLE_BEST,
                    detectionCooldownMs =
                        1000L
                )

            detectionJob =
                lifecycleScope.launch {

                    wakeWordEngine
                        ?.detections
                        ?.collect {

                            if (
                                speaking ||
                                listeningForCommand
                            ) {

                                return@collect
                            }

                            listeningForCommand =
                                true

                            conversationMode =
                                true

                            commandRetryCount =
                                0

                            stopWakeWord()

                            updateStatus(
                                "ATLAS\n\n" +
                                "HEY JARVIS\n\n" +
                                "I'm listening...",
                                AtlasOrbView.MODE_LISTENING
                            )

                            handler.postDelayed(
                                {

                                    if (
                                        !isFinishing &&
                                        !isDestroyed &&
                                        listeningForCommand
                                    ) {

                                        listenForCommand()
                                    }

                                },
                                300L
                            )
                        }
                }

            wakeWordEngine?.start()

        } catch (e: Exception) {

            updateStatus(
                "ATLAS\n\n" +
                "Wake-word error.\n\n" +
                "${e.message}"
            )

            handler.postDelayed(
                {

                    if (
                        !speaking &&
                        !listeningForCommand &&
                        !isFinishing &&
                        !isDestroyed
                    ) {

                        startWakeWordDetection()
                    }

                },
                1500L
            )
        }
    }

    private fun stopWakeWord() {

        detectionJob?.cancel()

        detectionJob = null

        try {

            wakeWordEngine?.release()

        } catch (_: Exception) {
        }

        wakeWordEngine = null
    }

    // ============================================================
    // SPEECH RECOGNITION
    // ============================================================

    private fun listenForCommand() {

        if (
            isFinishing ||
            isDestroyed
        ) {
            return
        }

        listeningForCommand = true

        if (
            !SpeechRecognizer
                .isRecognitionAvailable(this)
        ) {

            listeningForCommand = false

            speak(
                "Speech recognition isn't available on this phone."
            )

            return
        }

        try {

            speechRecognizer?.cancel()
            speechRecognizer?.destroy()

        } catch (_: Exception) {
        }

        speechRecognizer =
            SpeechRecognizer
                .createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(

            object :
                RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {

                    updateStatus(
                        "ATLAS\n\nI'm listening...",
                        AtlasOrbView.MODE_LISTENING
                    )
                }

                override fun onBeginningOfSpeech() {

                    updateStatus(
                        "ATLAS\n\nI'm hearing you...",
                        AtlasOrbView.MODE_LISTENING
                    )
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {

                    orb.setAudioLevel(
                        rmsdB
                    )
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                }

                override fun onEndOfSpeech() {

                    updateStatus(
                        "ATLAS\n\nThinking...",
                        AtlasOrbView.MODE_SPEAKING
                    )
                }

                override fun onError(
                    error: Int
                ) {

                    try {

                        speechRecognizer?.destroy()

                    } catch (_: Exception) {
                    }

                    speechRecognizer = null

                    if (
                        commandRetryCount < 2
                    ) {

                        commandRetryCount++

                        updateStatus(
                            "ATLAS\n\n" +
                            "I didn't catch that.\n\n" +
                            "Listening again...",
                            AtlasOrbView.MODE_LISTENING
                        )

                        handler.postDelayed(
                            {

                                if (
                                    !isFinishing &&
                                    !isDestroyed
                                ) {

                                    listenForCommand()
                                }

                            },
                            500L
                        )

                    } else {

                        listeningForCommand =
                            false

                        conversationMode =
                            false

                        speak(
                            "I didn't catch that. Try me again."
                        )
                    }
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    val matches =
                        results
                            ?.getStringArrayList(
                                SpeechRecognizer
                                    .RESULTS_RECOGNITION
                            )

                    val command =
                        matches
                            ?.firstOrNull()
                            ?.lowercase(
                                Locale.getDefault()
                            )
                            ?.trim()
                            ?: ""

                    try {

                        speechRecognizer?.destroy()

                    } catch (_: Exception) {
                    }

                    speechRecognizer = null

                    listeningForCommand =
                        false

                    if (
                        command.isBlank()
                    ) {

                        speak(
                            "I didn't hear you clearly. Try again.",
                            true
                        )

                        return
                    }

                    handleCommand(
                        command
                    )
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
                RecognizerIntent
                    .ACTION_RECOGNIZE_SPEECH
            )

        intent.putExtra(
            Recognize

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

        val title = TextView(this)

        title.text = "A T L A S"
        title.textSize = 30f
        title.gravity = Gravity.CENTER
        title.setTextColor(Color.CYAN)
        title.setTypeface(
            Typeface.create(
                "sans-serif",
                Typeface.BOLD
            )
        )
        title.letterSpacing = 0.18f

        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

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

        orb = AtlasOrbView(this)

        root.addView(
            orb,
            LinearLayout.LayoutParams(
                560,
                560
            )
        )

        status = TextView(this)

        status.text =
            "ATLAS\n\nStarting..."

        status.textSize = 19f
        status.gravity = Gravity.CENTER

        status.setTextColor(
            Color.rgb(0, 229, 255)
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
            TextToSpeech(this) { result ->

                if (result != TextToSpeech.SUCCESS) {
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

                textToSpeech?.setSpeechRate(0.93f)
                textToSpeech?.setPitch(0.94f)

                textToSpeech?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {

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

                                if (conversationMode) {

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

                                if (conversationMode) {
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

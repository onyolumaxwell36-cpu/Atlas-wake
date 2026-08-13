package com.atlas.wake

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.rementia.openwakeword.lib.DetectionMode
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.WakeWordModel
import kotlinx.coroutines.launch

class MainActivity : Activity() {

    private lateinit var engine: WakeWordEngine
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this)
        status.text = "ATLAS Wake\n\nRequesting microphone permission..."
        status.textSize = 20f
        status.setPadding(40, 100, 40, 40)
        setContentView(status)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        } else {
            startWakeWord()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            startWakeWord()
        } else {
            status.text = "ATLAS Wake\n\nMicrophone permission denied."
        }
    }

    private fun startWakeWord() {

        val models = listOf(
            WakeWordModel(
                "Hey Jarvis",
                "hey_jarvis_v0.1.onnx",
                0.5f
            )
        )

        engine = WakeWordEngine(
            context = this,
            models = models,
            detectionMode = DetectionMode.SINGLE_BEST
        )

        lifecycleScope.launch {
            engine.detections.collect { detection ->
                runOnUiThread {
                    status.text =
                        "ATLAS Wake\n\n" +
                        "WAKE WORD DETECTED!\n\n" +
                        detection.model.name +
                        "\nScore: " +
                        detection.score
                }
            }
        }

        engine.start()

        status.text =
            "ATLAS Wake\n\n" +
            "Listening...\n\n" +
            "Say: Hey Jarvis"
    }

    override fun onDestroy() {
        super.onDestroy()

        if (::engine.isInitialized) {
            engine.release()
        }
    }
}

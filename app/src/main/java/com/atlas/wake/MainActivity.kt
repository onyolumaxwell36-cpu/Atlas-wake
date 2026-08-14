package com.atlas.wake

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.DetectionMode
import com.rementia.openwakeword.lib.model.WakeWordModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private var wakeWordEngine: WakeWordEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this)
        status.text = "ATLAS Wake\n\nRequesting microphone permission..."
        status.textSize = 20f
        status.setPadding(40, 100, 40, 40)
        setContentView(status)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        } else {
            startWakeWordDetection()
        }
    }

    private fun startWakeWordDetection() {

        status.text = "ATLAS Wake\n\nListening for:\nHEY JARVIS"

        val models = listOf(
            WakeWordModel(
                name = "Hey Jarvis",
                modelPath = "hey_jarvis_v0.1.onnx",
                threshold = 0.5f
            )
        )

        wakeWordEngine = WakeWordEngine(
            context = this,
            models = models,
            detectionMode = DetectionMode.SINGLE_BEST,
            detectionCooldownMs = 2000L
        )

        lifecycleScope.launch {
            wakeWordEngine?.detections?.collect { detection ->

                status.text =
                    "ATLAS WAKE!\n\n" +
                    "HEY JARVIS DETECTED!\n\n" +
                    "Score: ${detection.score}"
            }
        }

        try {
            wakeWordEngine?.start()
        } catch (e: Exception) {
            status.text =
                "ATLAS Wake\n\n" +
                "Could not start wake-word detection.\n\n" +
                e.message
        }
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

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startWakeWordDetection()
        } else {
            status.text =
                "ATLAS Wake\n\nMicrophone permission denied."
        }
    }

    override fun onDestroy() {
        wakeWordEngine?.release()
        wakeWordEngine = null
        super.onDestroy()
    }
}

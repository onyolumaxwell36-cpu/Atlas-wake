package com.atlas.wake

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.rementia.openwakeword.WakeWordEngine
import com.rementia.openwakeword.WakeWordModel
import com.rementia.openwakeword.DetectionMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private var engine: WakeWordEngine? = null

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
            startWakeWord()
        }
    }

    private fun startWakeWord() {
        status.text = "ATLAS Wake\n\nListening for:\nHey Jarvis"

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

        engine?.start()

        lifecycleScope.launch {
            engine?.detections?.collect { detection ->
                status.text =
                    "ATLAS WAKE!\n\nWake word detected!\n\n" +
                    "Model: ${detection.model.name}\n" +
                    "Score: ${detection.score}"
            }
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
            startWakeWord()
        } else {
            status.text = "ATLAS Wake\n\nMicrophone permission denied."
        }
    }

    override fun onDestroy() {
        engine?.release()
        engine = null
        super.onDestroy()
    }
}

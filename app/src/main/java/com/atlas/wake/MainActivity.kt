package com.atlas.wake

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this)
        status.text = "ATLAS Wake\n\nSystem is ready."
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
            status.text = "ATLAS Wake\n\nMicrophone permission granted.\n\nSystem is ready."
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
            status.text =
                "ATLAS Wake\n\nMicrophone permission granted.\n\nSystem is ready."
        } else {
            status.text =
                "ATLAS Wake\n\nMicrophone permission was denied."
        }
    }
}

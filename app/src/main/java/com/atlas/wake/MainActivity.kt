package com.atlas.wake

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()
    }

    private fun createInterface() {

        val root = LinearLayout(this)

        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER

        root.setBackgroundColor(Color.BLACK)

        root.setPadding(
            24,
            40,
            24,
            40
        )

        // -----------------------------
        // ATLAS TITLE
        // -----------------------------

        val title = TextView(this)

        title.text = "A T L A S"
        title.textSize = 30f
        title.gravity = Gravity.CENTER

        title.setTextColor(
            Color.CYAN
        )

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

        // -----------------------------
        // SUBTITLE
        // -----------------------------

        val subtitle = TextView(this)

        subtitle.text =
            "ARTIFICIAL INTELLIGENCE ASSISTANT"

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
            8,
            0,
            40
        )

        root.addView(
            subtitle,
            subtitleParams
        )

        // -----------------------------
        // ATLAS CORE
        // -----------------------------

        val core = TextView(this)

        core.text = "●"
        core.textSize = 100f
        core.gravity = Gravity.CENTER

        core.setTextColor(
            Color.CYAN
        )

        root.addView(
            core,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                250
            )
        )

        // -----------------------------
        // STATUS
        // -----------------------------

        status = TextView(this)

        status.text =
            "ATLAS\n\nSYSTEM READY"

        status.textSize = 19f
        status.gravity = Gravity.CENTER

        status.setTextColor(
            Color.CYAN
        )

        status.setPadding(
            20,
            20,
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

        // -----------------------------
        // WAKE WORD
        // -----------------------------

        val wakeWord = TextView(this)

        wakeWord.text =
            "WAKE WORD\n\nHEY ATLAS"

        wakeWord.textSize = 14f
        wakeWord.gravity = Gravity.CENTER

        wakeWord.setTextColor(
            Color.rgb(70, 180, 220)
        )

        wakeWord.setTypeface(
            Typeface.create(
                "sans-serif",
                Typeface.BOLD
            )
        )

        wakeWord.setPadding(
            20,
            30,
            20,
            20
        )

        root.addView(
            wakeWord,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // -----------------------------
        // FOOTER
        // -----------------------------

        val footer = TextView(this)

        footer.text =
            "ATLAS ONLINE • VOICE AI"

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
}
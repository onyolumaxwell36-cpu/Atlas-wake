package com.atlas.wake

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.min
import kotlin.math.sin

class AtlasOrbView(context: Context) : View(context) {

    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var speaking = false
    private var animationTime = 0.0

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(
            Typeface.DEFAULT,
            Typeface.BOLD
        )
    }

    fun setSpeaking(value: Boolean) {
        speaking = value

        if (value) {
            animateOrb()
        }

        invalidate()
    }

    private fun animateOrb() {
        if (!speaking) return

        animationTime += 0.08
        invalidate()

        postDelayed(
            {
                animateOrb()
            },
            30
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f - 20f

        val baseRadius = min(width, height) * 0.28f

        val pulse = if (speaking) {
            sin(animationTime) * 15f
        } else {
            0f
        }

        val radius = baseRadius + pulse.toFloat()

        // OUTER ORANGE GLOW
        orbPaint.shader = null
        orbPaint.style = Paint.Style.FILL
        orbPaint.color = Color.rgb(255, 90, 0)

        orbPaint.setShadowLayer(
            90f,
            0f,
            0f,
            Color.rgb(255, 70, 0)
        )

        canvas.drawCircle(
            centerX,
            centerY,
            radius + 12f,
            orbPaint
        )

        orbPaint.clearShadowLayer()

        // MAIN ORANGE ORB
        orbPaint.shader = RadialGradient(
            centerX - radius * 0.30f,
            centerY - radius * 0.35f,
            radius * 1.4f,
            intArrayOf(
                Color.rgb(255, 220, 120),
                Color.rgb(255, 145, 30),
                Color.rgb(220, 55, 0),
                Color.BLACK
            ),
            floatArrayOf(
                0f,
                0.35f,
                0.72f,
                1f
            ),
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            orbPaint
        )

        orbPaint.shader = null

        // INNER GLOW
        orbPaint.color = Color.argb(
            75,
            255,
            150,
            30
        )

        orbPaint.setShadowLayer(
            35f,
            0f,
            0f,
            Color.rgb(255, 120, 20)
        )

        canvas.drawCircle(
            centerX,
            centerY,
            radius * 0.80f,
            orbPaint
        )

        orbPaint.clearShadowLayer()

        // LETTER M
        textPaint.textSize = radius * 0.75f
        textPaint.color = Color.WHITE

        val verticalCenter =
            (textPaint.ascent() + textPaint.descent()) / 2f

        canvas.drawText(
            "M",
            centerX,
            centerY - verticalCenter,
            textPaint
        )

        // ATLAS AI
        textPaint.textSize = 20f
        textPaint.color = Color.rgb(
            255,
            150,
            60
        )

        canvas.drawText(
            "Atlas AI",
            centerX,
            centerY + radius + 50f,
            textPaint
        )
    }
}
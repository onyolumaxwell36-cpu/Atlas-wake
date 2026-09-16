package com.atlas.wake

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.sin

class AtlasOrbView(context: Context) : View(context) {

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var hologram: Bitmap? = null

    private var speaking = false
    private var animationTime = 0.0

    init {

        setLayerType(
            View.LAYER_TYPE_SOFTWARE,
            null
        )

        hologram =
            BitmapFactory.decodeResource(
                resources,
                R.drawable.ctp_hologram
            )
    }

    fun setSpeaking(value: Boolean) {

        speaking = value

        if (value) {
            animateHologram()
        }

        invalidate()
    }

    private fun animateHologram() {

        if (!speaking) {
            return
        }

        animationTime += 0.08

        invalidate()

        postDelayed(
            {
                animateHologram()
            },
            30
        )
    }

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        val centerX =
            width / 2f

        val centerY =
            height / 2f

        /*
         * BLACK BACKGROUND
         */
        canvas.drawColor(
            Color.BLACK
        )

        val bitmap =
            hologram ?: return

        /*
         * Keep the entire holographic image
         * visible without stretching it.
         */
        val imageWidth =
            width.toFloat()

        val scale =
            imageWidth /
                bitmap.width.toFloat()

        val imageHeight =
            bitmap.height * scale

        val left =
            (width - imageWidth) / 2f

        val top =
            centerY -
                imageHeight / 2f

        val right =
            left + imageWidth

        val bottom =
            top + imageHeight

        /*
         * BLUE HOLOGRAM GLOW
         */
        val pulse =
            if (speaking) {
                0.85f +
                    (
                        sin(animationTime) * 0.15f
                    ).toFloat()
            } else {
                0.75f
            }

        glowPaint.color =
            Color.argb(
                (70 * pulse).toInt(),
                0,
                120,
                255
            )

        glowPaint.setShadowLayer(
            if (speaking) 45f else 25f,
            0f,
            0f,
            Color.rgb(
                0,
                120,
                255
            )
        )

        canvas.drawRect(
            RectF(
                left + 15f,
                top + 15f,
                right - 15f,
                bottom - 15f
            ),
            glowPaint
        )

        glowPaint.clearShadowLayer()

        /*
         * HOLOGRAPHIC IMAGE
         */
        imagePaint.alpha =
            if (speaking) {
                (
                    225 +
                        sin(animationTime * 2.0) * 30
                    ).toInt()
                        .coerceIn(180, 255)
            } else {
                225
            }

        canvas.drawBitmap(
            bitmap,
            null,
            RectF(
                left,
                top,
                right,
                bottom
            ),
            imagePaint
        )

        /*
         * SCAN-LINE EFFECT
         */
        val scanPosition =
            if (speaking) {
                (
                    (sin(animationTime * 0.7) + 1.0)
                        .toFloat()
                        / 2f
                )
            } else {
                0.5f
            }

        val scanY =
            top +
                imageHeight *
                scanPosition

        val scanPaint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        scanPaint.color =
            Color.argb(
                if (speaking) 110 else 45,
                0,
                170,
                255
            )

        scanPaint.strokeWidth =
            if (speaking) 3f else 1f

        canvas.drawLine(
            left,
            scanY,
            right,
            scanY,
            scanPaint
        )

        /*
         * SMALL HOLOGRAM FLICKER
         */
        if (speaking) {

            val flickerPaint =
                Paint(Paint.ANTI_ALIAS_FLAG)

            flickerPaint.color =
                Color.argb(
                    18,
                    0,
                    170,
                    255
                )

            canvas.drawRect(
                left,
                top,
                right,
                bottom,
                flickerPaint
            )
        }
    }
}
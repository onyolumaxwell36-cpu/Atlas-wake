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
    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flickerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var hologram: Bitmap? = null

    private var speaking = false
    private var animationTime = 0.0
    private var animationScheduled = false

    private val imageRect = RectF()
    private val glowRect = RectF()

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        hologram = BitmapFactory.decodeResource(
            resources,
            R.drawable.ctp_hologram
        )

        scanPaint.color = Color.argb(45, 0, 170, 255)
        flickerPaint.color = Color.argb(18, 0, 170, 255)
    }

    fun setSpeaking(value: Boolean) {
        if (speaking == value) return

        speaking = value

        if (speaking) {
            scheduleAnimation()
        }

        invalidate()
    }

    private fun scheduleAnimation() {
        if (!speaking || animationScheduled) return

        animationScheduled = true

        postOnAnimation {
            animationScheduled = false

            if (speaking) {
                animationTime += 0.08
                invalidate()
                scheduleAnimation()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(Color.BLACK)

        val bitmap = hologram ?: return

        if (width <= 0 || height <= 0 ||
            bitmap.width <= 0 || bitmap.height <= 0
        ) {
            return
        }

        /*
         * FIT THE ENTIRE IMAGE INSIDE THE VIEW.
         * Preserve the image proportions and center it.
         */
        val availableWidth = width.toFloat()
        val availableHeight = height.toFloat()

        val scale = minOf(
            availableWidth / bitmap.width.toFloat(),
            availableHeight / bitmap.height.toFloat()
        )

        val imageWidth = bitmap.width * scale
        val imageHeight = bitmap.height * scale

        val left = (availableWidth - imageWidth) / 2f
        val top = (availableHeight - imageHeight) / 2f
        val right = left + imageWidth
        val bottom = top + imageHeight

        imageRect.set(left, top, right, bottom)

        /*
         * BLUE HOLOGRAM GLOW
         */
        val pulse = if (speaking) {
            0.85f + (sin(animationTime) * 0.15f).toFloat()
        } else {
            0.75f
        }

        glowPaint.color = Color.argb(
            (70 * pulse).toInt(),
            0,
            120,
            255
        )

        glowPaint.setShadowLayer(
            if (speaking) 45f else 25f,
            0f,
            0f,
            Color.rgb(0, 120, 255)
        )

        glowRect.set(
            left + 15f,
            top + 15f,
            right - 15f,
            bottom - 15f
        )

        canvas.drawRoundRect(
            glowRect,
            24f,
            24f,
            glowPaint
        )

        glowPaint.clearShadowLayer()

        /*
         * HOLOGRAPHIC IMAGE
         */
        imagePaint.alpha = if (speaking) {
            (
                225 + sin(animationTime * 2.0) * 30
            ).toInt().coerceIn(180, 255)
        } else {
            225
        }

        canvas.drawBitmap(
            bitmap,
            null,
            imageRect,
            imagePaint
        )

        /*
         * SCAN-LINE EFFECT
         */
        val scanPosition = if (speaking) {
            ((sin(animationTime * 0.7) + 1.0) / 2.0).toFloat()
        } else {
            0.5f
        }

        val scanY = top + imageHeight * scanPosition

        scanPaint.color = Color.argb(
            if (speaking) 110 else 45,
            0,
            170,
            255
        )

        scanPaint.strokeWidth = if (speaking) 3f else 1f

        canvas.drawLine(
            left,
            scanY,
            right,
            scanY,
            scanPaint
        )

        /*
         * SUBTLE HOLOGRAM FLICKER
         */
        if (speaking) {
            canvas.drawRect(
                imageRect,
                flickerPaint
            )
        }
    }

    override fun onDetachedFromWindow() {
        speaking = false
        animationScheduled = false
        super.onDetachedFromWindow()
    }
}

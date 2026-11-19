package com.astranomia.game.ui

import android.graphics.*
import com.astranomia.game.model.Combine

/**
 * Handles retro mobile keypad (L, F, R, 1-9, *, 0, #) in portrait mode (matching J2ME farm look),
 * and on-screen controls in landscape mode.
 */
class VirtualControls {

    // Input flags
    var isUpPressed: Boolean = false
    var isDownPressed: Boolean = false
    var isLeftPressed: Boolean = false
    var isRightPressed: Boolean = false
    var isFirePressed: Boolean = false

    var onReloadClicked: (() -> Unit)? = null
    var onSuperClicked: (() -> Unit)? = null
    var onMenuClicked: (() -> Unit)? = null
    var onHornClicked: (() -> Unit)? = null
    var onMuteClicked: (() -> Unit)? = null

    var isPortrait: Boolean = true
    var viewportBottom: Float = 1000f

    // Keypad button representation
    data class KeyButton(
        val key: String,
        val subLabel: String,
        val bounds: RectF = RectF(),
        var isPressed: Boolean = false
    )

    private val keyButtons = mutableListOf<KeyButton>()
    private var hudLeftBadgeBounds = RectF()
    private var hudRightBadgeBounds = RectF()

    // Landscape fallback bounds
    private var dpadBounds = RectF()
    private var upBounds = RectF()
    private var downBounds = RectF()
    private var leftBounds = RectF()
    private var rightBounds = RectF()
    private var fireBounds = RectF()
    private var reloadBounds = RectF()
    private var superBounds = RectF()
    private var menuBounds = RectF()

    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
    }

    init {
        updateLayout(720f, 1600f)
    }

    fun updateLayout(viewWidth: Float, viewHeight: Float) {
        val w = viewWidth.coerceAtLeast(100f)
        val h = viewHeight.coerceAtLeast(100f)
        isPortrait = h > w

        if (isPortrait) {
            // Viewport takes top ~65%, keypad takes bottom ~35%
            val keypadHeight = (h * 0.38f).coerceIn(350f, 650f)
            viewportBottom = h - keypadHeight

            // HUD badges at the bottom of the viewport
            val badgeW = (w * 0.14f).coerceIn(80f, 120f)
            val badgeH = (badgeW * 1.05f)
            hudLeftBadgeBounds.set(0f, viewportBottom - badgeH, badgeW, viewportBottom)
            hudRightBadgeBounds.set(w - badgeW, viewportBottom - badgeH, w, viewportBottom)

            // Setup Keypad (5 rows x 3 columns)
            keyButtons.clear()
            val rows = 5
            val cols = 3
            val padLeft = 8f
            val padRight = w - 8f
            val padTop = viewportBottom + 12f
            val padBottom = h - 12f

            val totalW = (padRight - padLeft).coerceAtLeast(100f)
            val totalH = (padBottom - padTop).coerceAtLeast(100f)
            val gapX = 8f
            val gapY = 8f

            val btnW = ((totalW - gapX * (cols - 1)) / cols).coerceAtLeast(20f)
            val btnH = ((totalH - gapY * (rows - 1)) / rows).coerceAtLeast(20f)

            val layout = listOf(
                listOf(Triple("L", "⚡", "L"), Triple("F", "🔥", "F"), Triple("R", "📖", "R")),
                listOf(Triple("1", "↖", "1"), Triple("2", "▲", "2"), Triple("3", "↗", "3")),
                listOf(Triple("4", "◄", "4"), Triple("5", "⚡", "5"), Triple("6", "►", "6")),
                listOf(Triple("7", "↙", "7"), Triple("8", "▼", "8"), Triple("9", "↘", "9")),
                listOf(Triple("*", "🔄", "*"), Triple("0", "📢", "0"), Triple("#", "🎵", "#"))
            )

            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val x0 = padLeft + c * (btnW + gapX)
                    val y0 = padTop + r * (btnH + gapY)
                    val info = layout[r][c]
                    val btn = KeyButton(info.first, info.second, RectF(x0, y0, x0 + btnW, y0 + btnH))
                    keyButtons.add(btn)
                }
            }
        } else {
            viewportBottom = viewHeight
            val dpadSize = 180f
            val dpadMargin = 40f
            dpadBounds.set(dpadMargin, viewHeight - dpadSize - dpadMargin, dpadMargin + dpadSize, viewHeight - dpadMargin)

            val btnSize = dpadSize / 3f
            upBounds.set(dpadBounds.left + btnSize, dpadBounds.top, dpadBounds.left + btnSize * 2, dpadBounds.top + btnSize)
            downBounds.set(dpadBounds.left + btnSize, dpadBounds.bottom - btnSize, dpadBounds.left + btnSize * 2, dpadBounds.bottom)
            leftBounds.set(dpadBounds.left, dpadBounds.top + btnSize, dpadBounds.left + btnSize, dpadBounds.top + btnSize * 2)
            rightBounds.set(dpadBounds.right - btnSize, dpadBounds.top + btnSize, dpadBounds.right, dpadBounds.top + btnSize * 2)

            val fireRadius = 55f
            val fireX = viewWidth - fireRadius - 50f
            val fireY = viewHeight - fireRadius - 50f
            fireBounds.set(fireX - fireRadius, fireY - fireRadius, fireX + fireRadius, fireY + fireRadius)

            val reloadRadius = 42f
            val reloadX = fireX - fireRadius - reloadRadius - 25f
            val reloadY = fireY
            reloadBounds.set(reloadX - reloadRadius, reloadY - reloadRadius, reloadX + reloadRadius, reloadY + reloadRadius)

            val superRadius = 48f
            val superX = reloadX
            val superY = reloadY - reloadRadius - superRadius - 20f
            superBounds.set(superX - superRadius, superY - superRadius, superX + superRadius, superY + superRadius)

            menuBounds.set(30f, 25f, 160f, 80f)
        }
    }

    fun handleTouchDown(x: Float, y: Float): Boolean {
        if (isPortrait) {
            // Check HUD badges
            if (hudLeftBadgeBounds.contains(x, y)) {
                onSuperClicked?.invoke()
                return true
            }
            if (hudRightBadgeBounds.contains(x, y)) {
                onMenuClicked?.invoke()
                return true
            }

            // Check Keypad buttons
            for (btn in keyButtons) {
                if (btn.bounds.contains(x, y)) {
                    btn.isPressed = true
                    applyKeyAction(btn.key, isPressed = true)
                    return true
                }
            }
        } else {
            if (menuBounds.contains(x, y)) { onMenuClicked?.invoke(); return true }
            if (superBounds.contains(x, y)) { onSuperClicked?.invoke(); return true }
            if (reloadBounds.contains(x, y)) { onReloadClicked?.invoke(); return true }
            if (fireBounds.contains(x, y)) { isFirePressed = true; return true }
            if (upBounds.contains(x, y)) { isUpPressed = true; return true }
            if (downBounds.contains(x, y)) { isDownPressed = true; return true }
            if (leftBounds.contains(x, y)) { isLeftPressed = true; return true }
            if (rightBounds.contains(x, y)) { isRightPressed = true; return true }
        }
        return false
    }

    fun handleTouchUp(x: Float, y: Float) {
        if (isPortrait) {
            for (btn in keyButtons) {
                if (btn.isPressed) {
                    btn.isPressed = false
                    applyKeyAction(btn.key, isPressed = false)
                }
            }
        } else {
            if (fireBounds.contains(x, y)) isFirePressed = false
            if (upBounds.contains(x, y)) isUpPressed = false
            if (downBounds.contains(x, y)) isDownPressed = false
            if (leftBounds.contains(x, y)) isLeftPressed = false
            if (rightBounds.contains(x, y)) isRightPressed = false
        }
    }

    fun resetTouches() {
        isUpPressed = false
        isDownPressed = false
        isLeftPressed = false
        isRightPressed = false
        isFirePressed = false
        for (btn in keyButtons) btn.isPressed = false
    }

    private fun applyKeyAction(key: String, isPressed: Boolean) {
        when (key) {
            "2" -> isUpPressed = isPressed
            "8" -> isDownPressed = isPressed
            "4" -> isLeftPressed = isPressed
            "6" -> isRightPressed = isPressed
            "1" -> { isUpPressed = isPressed; isLeftPressed = isPressed }
            "3" -> { isUpPressed = isPressed; isRightPressed = isPressed }
            "7" -> { isDownPressed = isPressed; isLeftPressed = isPressed }
            "9" -> { isDownPressed = isPressed; isRightPressed = isPressed }
            "F", "5" -> isFirePressed = isPressed
            "L" -> if (isPressed) onSuperClicked?.invoke()
            "R" -> if (isPressed) onMenuClicked?.invoke()
            "*" -> if (isPressed) onReloadClicked?.invoke()
            "0" -> if (isPressed) onHornClicked?.invoke()
            "#" -> if (isPressed) onMuteClicked?.invoke()
        }
    }

    fun drawControls(canvas: Canvas, viewWidth: Float, viewHeight: Float, combine: Combine) {
        if (isPortrait) {
            drawPortraitKeypad(canvas, viewWidth, viewHeight)
        } else {
            drawLandscapeControls(canvas, combine)
        }
    }

    private fun drawPortraitKeypad(canvas: Canvas, viewWidth: Float, viewHeight: Float) {
        // Light gray keypad background matching screenshot
        buttonPaint.color = Color.parseColor("#D4D4D8")
        canvas.drawRect(0f, viewportBottom, viewWidth, viewHeight, buttonPaint)

        // Subtle top divider
        borderPaint.color = Color.parseColor("#A1A1AA")
        borderPaint.strokeWidth = 2f
        canvas.drawLine(0f, viewportBottom, viewWidth, viewportBottom, borderPaint)

        // Draw each keypad key
        borderPaint.strokeWidth = 1.5f
        borderPaint.color = Color.parseColor("#FFFFFF")

        for (btn in keyButtons) {
            val r = btn.bounds
            buttonPaint.color = if (btn.isPressed) {
                Color.parseColor("#93C5FD") // Active soft blue on press
            } else {
                Color.parseColor("#E4E4E7") // Soft light gray key body
            }

            canvas.drawRoundRect(r, 16f, 16f, buttonPaint)
            canvas.drawRoundRect(r, 16f, 16f, borderPaint)

            // Deep blue label text matching the screenshot
            textPaint.color = if (btn.isPressed) Color.parseColor("#1D4ED8") else Color.parseColor("#000080")
            textPaint.textSize = (r.height() * 0.45f).coerceIn(24f, 44f)
            textPaint.textAlign = Paint.Align.CENTER

            val textY = r.centerY() + (textPaint.textSize * 0.35f)
            canvas.drawText(btn.key, r.centerX(), textY, textPaint)
        }
    }

    private fun drawLandscapeControls(canvas: Canvas, combine: Combine) {
        // D-Pad
        drawDpadButton(canvas, upBounds, "▲", isUpPressed)
        drawDpadButton(canvas, downBounds, "▼", isDownPressed)
        drawDpadButton(canvas, leftBounds, "◄", isLeftPressed)
        drawDpadButton(canvas, rightBounds, "►", isRightPressed)

        // Action Buttons
        buttonPaint.color = if (isFirePressed) Color.parseColor("#DC2626") else Color.parseColor("#EF4444")
        canvas.drawOval(fireBounds, buttonPaint)
        borderPaint.color = Color.WHITE
        borderPaint.strokeWidth = 3f
        canvas.drawOval(fireBounds, borderPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 28f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("🔥 ОГОНЬ", fireBounds.centerX(), fireBounds.centerY() + 10f, textPaint)

        // Reload
        buttonPaint.color = Color.argb(210, 30, 41, 59)
        canvas.drawOval(reloadBounds, buttonPaint)
        borderPaint.color = Color.parseColor("#38BDF8")
        canvas.drawOval(reloadBounds, borderPaint)
        textPaint.color = Color.parseColor("#38BDF8")
        textPaint.textSize = 20f
        canvas.drawText("🔄", reloadBounds.centerX(), reloadBounds.centerY() + 7f, textPaint)

        // Super
        val isReady = combine.superCharge >= 100f
        buttonPaint.color = if (isReady) Color.parseColor("#F59E0B") else Color.argb(180, 147, 51, 234)
        canvas.drawOval(superBounds, buttonPaint)
        borderPaint.color = if (isReady) Color.parseColor("#FEF08A") else Color.parseColor("#E9D5FF")
        canvas.drawOval(superBounds, borderPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 22f
        canvas.drawText("⚡ СУПЕР", superBounds.centerX(), superBounds.centerY() - 2f, textPaint)
        textPaint.textSize = 17f
        canvas.drawText("${combine.superCharge.toInt()}%", superBounds.centerX(), superBounds.centerY() + 20f, textPaint)
    }

    private fun drawDpadButton(canvas: Canvas, rect: RectF, label: String, isPressed: Boolean) {
        buttonPaint.color = if (isPressed) Color.parseColor("#EAB308") else Color.argb(190, 30, 41, 59)
        canvas.drawRoundRect(rect, 14f, 14f, buttonPaint)
        borderPaint.color = if (isPressed) Color.WHITE else Color.argb(120, 255, 255, 255)
        borderPaint.strokeWidth = 3f
        canvas.drawRoundRect(rect, 14f, 14f, borderPaint)

        textPaint.color = if (isPressed) Color.BLACK else Color.WHITE
        textPaint.textSize = 34f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, rect.centerX(), rect.centerY() + 12f, textPaint)
    }
}

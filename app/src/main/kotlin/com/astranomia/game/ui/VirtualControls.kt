package com.astranomia.game.ui

import android.graphics.*
import com.astranomia.game.model.Combine

/**
 * Handles on-screen touch D-Pad, Fire, Reload, Super Weapon buttons and Top-Left HUD
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

    // D-Pad touch bounds
    private var dpadBounds = RectF()
    private var upBounds = RectF()
    private var downBounds = RectF()
    private var leftBounds = RectF()
    private var rightBounds = RectF()

    // Right Action Buttons bounds
    private var fireBounds = RectF()
    private var reloadBounds = RectF()
    private var superBounds = RectF()

    // Top-Left HUD bounds
    private var menuBounds = RectF()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    fun updateLayout(viewWidth: Float, viewHeight: Float) {
        val dpadSize = 180f
        val dpadMargin = 40f
        dpadBounds.set(dpadMargin, viewHeight - dpadSize - dpadMargin, dpadMargin + dpadSize, viewHeight - dpadMargin)

        val btnSize = dpadSize / 3f
        upBounds.set(dpadBounds.left + btnSize, dpadBounds.top, dpadBounds.left + btnSize * 2, dpadBounds.top + btnSize)
        downBounds.set(dpadBounds.left + btnSize, dpadBounds.bottom - btnSize, dpadBounds.left + btnSize * 2, dpadBounds.bottom)
        leftBounds.set(dpadBounds.left, dpadBounds.top + btnSize, dpadBounds.left + btnSize, dpadBounds.top + btnSize * 2)
        rightBounds.set(dpadBounds.right - btnSize, dpadBounds.top + btnSize, dpadBounds.right, dpadBounds.top + btnSize * 2)

        // Right buttons
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

        // Top-left Menu button
        menuBounds.set(30f, 25f, 160f, 80f)
    }

    fun handleTouchDown(x: Float, y: Float): Boolean {
        if (menuBounds.contains(x, y)) {
            onMenuClicked?.invoke()
            return true
        }
        if (superBounds.contains(x, y)) {
            onSuperClicked?.invoke()
            return true
        }
        if (reloadBounds.contains(x, y)) {
            onReloadClicked?.invoke()
            return true
        }
        if (fireBounds.contains(x, y)) {
            isFirePressed = true
            return true
        }

        if (upBounds.contains(x, y)) { isUpPressed = true; return true }
        if (downBounds.contains(x, y)) { isDownPressed = true; return true }
        if (leftBounds.contains(x, y)) { isLeftPressed = true; return true }
        if (rightBounds.contains(x, y)) { isRightPressed = true; return true }

        return false
    }

    fun handleTouchUp(x: Float, y: Float) {
        if (fireBounds.contains(x, y)) isFirePressed = false
        if (upBounds.contains(x, y)) isUpPressed = false
        if (downBounds.contains(x, y)) isDownPressed = false
        if (leftBounds.contains(x, y)) isLeftPressed = false
        if (rightBounds.contains(x, y)) isRightPressed = false
    }

    fun resetTouches() {
        isUpPressed = false
        isDownPressed = false
        isLeftPressed = false
        isRightPressed = false
        isFirePressed = false
    }

    fun drawHUD(
        canvas: Canvas,
        score: Int,
        combine: Combine,
        cropHealth: Float,
        viewWidth: Float
    ) {
        // 1. TOP-LEFT: Menu Button
        buttonPaint.color = Color.argb(200, 15, 23, 42)
        canvas.drawRoundRect(menuBounds, 12f, 12f, buttonPaint)
        borderPaint.color = Color.argb(120, 255, 255, 255)
        canvas.drawRoundRect(menuBounds, 12f, 12f, borderPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 28f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("⚙️ МЕНЮ", menuBounds.centerX(), menuBounds.centerY() + 10f, textPaint)

        // 2. TOP-LEFT: Score strictly ABOVE HP Bar
        val hudLeft = 30f
        val scoreY = menuBounds.bottom + 42f

        textPaint.color = Color.parseColor("#FFCC00")
        textPaint.textSize = 34f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("🏆 ОЧКИ: $score", hudLeft, scoreY, textPaint)

        // 3. TOP-LEFT: Combine Icon next to HP Bar
        val hpY = scoreY + 28f
        textPaint.textSize = 38f
        canvas.drawText("🚜", hudLeft, hpY + 16f, textPaint)

        // HP Bar
        val hpBarLeft = hudLeft + 52f
        val hpBarWidth = 220f
        val hpBarHeight = 26f
        val hpRect = RectF(hpBarLeft, hpY - 10f, hpBarLeft + hpBarWidth, hpY - 10f + hpBarHeight)

        buttonPaint.color = Color.argb(200, 15, 23, 42)
        canvas.drawRoundRect(hpRect, 10f, 10f, buttonPaint)
        borderPaint.color = Color.parseColor("#EF4444")
        canvas.drawRoundRect(hpRect, 10f, 10f, borderPaint)

        val hpRatio = (combine.hp / combine.maxHp).coerceIn(0f, 1f)
        val hpFillRect = RectF(hpBarLeft, hpY - 10f, hpBarLeft + hpBarWidth * hpRatio, hpY - 10f + hpBarHeight)
        buttonPaint.color = if (hpRatio > 0.35f) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
        canvas.drawRoundRect(hpFillRect, 10f, 10f, buttonPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 20f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("${combine.hp.toInt()}% HP", hpRect.centerX(), hpRect.centerY() + 7f, textPaint)

        // 4. TOP-RIGHT: Crop Health & Ammo
        textPaint.color = Color.parseColor("#A3E635")
        textPaint.textSize = 32f
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("🌾 УРОЖАЙ: ${cropHealth.toInt()}%", viewWidth - 30f, 50f, textPaint)

        textPaint.color = Color.parseColor("#38BDF8")
        textPaint.textSize = 26f
        val ammoText = if (combine.isReloading) "🔄 ПЕРЕЗАРЯДКА..." else "🥔 ЗАРЯД: ${combine.ammo} / ${combine.maxAmmo}"
        canvas.drawText(ammoText, viewWidth - 30f, 90f, textPaint)

        // 5. DRAW CONTROLS (D-Pad on left, Buttons on right)
        drawDPad(canvas)
        drawActionButtons(canvas, combine)
    }

    private fun drawDPad(canvas: Canvas) {
        drawDpadButton(canvas, upBounds, "▲", isUpPressed)
        drawDpadButton(canvas, downBounds, "▼", isDownPressed)
        drawDpadButton(canvas, leftBounds, "◄", isLeftPressed)
        drawDpadButton(canvas, rightBounds, "►", isRightPressed)
    }

    private fun drawDpadButton(canvas: Canvas, rect: RectF, label: String, isPressed: Boolean) {
        buttonPaint.color = if (isPressed) Color.parseColor("#EAB308") else Color.argb(190, 30, 41, 59)
        canvas.drawRoundRect(rect, 14f, 14f, buttonPaint)
        borderPaint.color = if (isPressed) Color.WHITE else Color.argb(120, 255, 255, 255)
        canvas.drawRoundRect(rect, 14f, 14f, borderPaint)

        textPaint.color = if (isPressed) Color.BLACK else Color.WHITE
        textPaint.textSize = 34f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, rect.centerX(), rect.centerY() + 12f, textPaint)
    }

    private fun drawActionButtons(canvas: Canvas, combine: Combine) {
        // Fire button
        buttonPaint.color = if (isFirePressed) Color.parseColor("#DC2626") else Color.parseColor("#EF4444")
        canvas.drawOval(fireBounds, buttonPaint)
        borderPaint.color = Color.WHITE
        canvas.drawOval(fireBounds, borderPaint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 28f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("🔥 ОГОНЬ", fireBounds.centerX(), fireBounds.centerY() + 10f, textPaint)

        // Reload button
        buttonPaint.color = Color.argb(210, 30, 41, 59)
        canvas.drawOval(reloadBounds, buttonPaint)
        borderPaint.color = Color.parseColor("#38BDF8")
        canvas.drawOval(reloadBounds, borderPaint)
        textPaint.color = Color.parseColor("#38BDF8")
        textPaint.textSize = 20f
        canvas.drawText("🔄", reloadBounds.centerX(), reloadBounds.centerY() + 7f, textPaint)

        // Super Weapon button (Нитро-Астрономия)
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
}

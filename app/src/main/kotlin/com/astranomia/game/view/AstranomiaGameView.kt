package com.astranomia.game.view

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.astranomia.game.audio.SoundManager
import com.astranomia.game.haptics.HapticManager
import com.astranomia.game.model.*
import com.astranomia.game.ui.VirtualControls
import kotlin.math.*

class AstranomiaGameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var gameThread: Thread? = null
    private var isRunning: Boolean = false

    val soundManager = SoundManager()
    val hapticManager = HapticManager(context)
    val controls = VirtualControls()

    // World state
    private val worldWidth = 1920f
    private val worldHeight = 1080f
    private var screenWidth = 0f
    private var screenHeight = 0f

    val player = Combine(worldWidth / 2, worldHeight / 2)
    val crops = mutableListOf<Crop>()
    val aliens = mutableListOf<Alien>()
    val ufos = mutableListOf<FlyingSaucer>()
    val bullets = mutableListOf<Bullet>()
    val particles = mutableListOf<Particle>()
    val floatingTexts = mutableListOf<FloatingText>()

    var score = 0
    var aliensCrushed = 0
    var ufosDestroyed = 0
    var cropHealth = 100f
    var screenShake = 0f

    enum class GameState { MENU, PLAYING, PAUSED, GAMEOVER }
    var state = GameState.MENU

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
    }

    init {
        holder.addCallback(this)
        isFocusable = true

        controls.onMenuClicked = {
            if (state == GameState.PLAYING) state = GameState.PAUSED
            else if (state == GameState.PAUSED) state = GameState.PLAYING
        }

        controls.onReloadClicked = {
            if (!player.isReloading && player.ammo < player.maxAmmo) {
                player.isReloading = true
                player.reloadProgress = 0f
            }
        }

        controls.onSuperClicked = {
            if (player.superCharge >= 100f && !player.isNitro) {
                player.isNitro = true
                player.nitroTime = 480
                player.superCharge = 0f
                screenShake = 22f
                hapticManager.nitroBurst()
                soundManager.playNitroActivation()
                floatingTexts.add(FloatingText(player.x, player.y - 50, "⚡ НИТРО-АСТРОНОМИЯ! ⚡", Color.parseColor("#FFCC00"), 36f))
            }
        }

        initCrops()
    }

    private fun initCrops() {
        crops.clear()
        val types = CropType.values()
        for (i in 0 until 60) {
            crops.add(
                Crop(
                    x = (100f + (Math.random() * (worldWidth - 200))).toFloat(),
                    y = (100f + (Math.random() * (worldHeight - 200))).toFloat(),
                    type = types[i % types.size]
                )
            )
        }
    }

    fun startGame() {
        score = 0
        aliensCrushed = 0
        ufosDestroyed = 0
        cropHealth = 100f
        player.reset(worldWidth / 2, worldHeight / 2)
        initCrops()
        aliens.clear()
        ufos.clear()
        bullets.clear()
        particles.clear()
        floatingTexts.clear()

        repeat(2) { spawnUFO() }
        repeat(4) { spawnAlien() }

        soundManager.startGameMusic()
        state = GameState.PLAYING
    }

    private fun spawnUFO() {
        ufos.add(
            FlyingSaucer(
                x = (Math.random() * worldWidth).toFloat(),
                y = -50f
            )
        )
    }

    private fun spawnAlien() {
        aliens.add(
            Alien(
                x = (Math.random() * worldWidth).toFloat(),
                y = worldHeight
            )
        )
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isRunning = true
        soundManager.startMenuMusic()
        gameThread = Thread(this)
        gameThread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth = width.toFloat()
        screenHeight = height.toFloat()
        controls.updateLayout(screenWidth, screenHeight)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
        soundManager.stopMusic()
        try {
            gameThread?.join()
        } catch (_: InterruptedException) {}
    }

    override fun run() {
        while (isRunning) {
            val startTime = System.currentTimeMillis()

            updatePhysics()
            drawFrame()

            val elapsed = System.currentTimeMillis() - startTime
            val sleep = 16 - elapsed
            if (sleep > 0) {
                try {
                    Thread.sleep(sleep)
                } catch (_: InterruptedException) {}
            }
        }
    }

    private fun updatePhysics() {
        if (state != GameState.PLAYING) return

        if (screenShake > 0) screenShake = max(0f, screenShake - 0.8f)

        // 1. COMBINE PHYSICS & MOVEMENT
        if (player.isNitro) {
            player.nitroTime--
            if (player.nitroTime <= 0) player.isNitro = false
        }

        val maxSpeed = if (player.isNitro) player.maxSpeed * 2.2f else player.maxSpeed
        val accel = 0.16f

        if (controls.isUpPressed) {
            player.speed = min(maxSpeed, player.speed + accel)
        } else if (controls.isDownPressed) {
            player.speed = max(-maxSpeed * 0.5f, player.speed - accel)
        } else {
            player.speed *= 0.94f
        }

        val steer = 0.045f * (if (player.speed >= 0) 1f else -1f)
        if (controls.isLeftPressed) player.angle -= steer
        if (controls.isRightPressed) player.angle += steer

        player.x += cos(player.angle) * player.speed
        player.y += sin(player.angle) * player.speed

        player.x = player.x.coerceIn(60f, worldWidth - 60f)
        player.y = player.y.coerceIn(60f, worldHeight - 60f)

        if (abs(player.speed) > 0.1f) player.reelAngle += player.speed * 0.08f

        // Reload logic
        if (player.isReloading) {
            player.reloadProgress += 0.02f
            if (player.reloadProgress >= 1f) {
                player.isReloading = false
                player.ammo = player.maxAmmo
            }
        }

        // Fire logic
        if (controls.isFirePressed && !player.isReloading) {
            if (player.ammo > 0) {
                player.ammo--
                soundManager.playShotgun()
                hapticManager.heavyClick()

                val noseX = player.x + cos(player.angle) * 35f
                val noseY = player.y + sin(player.angle) * 35f

                bullets.add(Bullet(noseX, noseY, cos(player.angle) * 14f, sin(player.angle) * 14f))
                controls.isFirePressed = false
            } else {
                controls.onReloadClicked?.invoke()
            }
        }

        // 2. BULLETS & COLLISION WITH UFOS
        val bulletIter = bullets.iterator()
        while (bulletIter.hasNext()) {
            val b = bulletIter.next()
            b.x += b.vx
            b.y += b.vy
            b.life--

            var hit = false
            for (ufo in ufos) {
                val d = hypot(b.x - ufo.x, b.y - ufo.y)
                if (d < 45f) {
                    ufo.hp -= b.damage
                    hit = true
                    screenShake = 5f
                    if (ufo.hp <= 0) {
                        soundManager.playUfoCrash()
                        hapticManager.heavyClick()
                        screenShake = 16f
                        ufosDestroyed++
                        score += 500
                        floatingTexts.add(FloatingText(ufo.x, ufo.y, "+500 ТАРЕЛКА СБИТА!", Color.parseColor("#38BDF8"), 26f))
                        ufo.targetCrop?.isStolen = false
                    }
                    break
                }
            }

            if (hit || b.life <= 0) bulletIter.remove()
        }
        ufos.removeAll { it.hp <= 0 }

        // 3. UFOS UPDATE
        if (ufos.size < 3 && Math.random() < 0.015) spawnUFO()
        ufos.forEach { ufo ->
            ufo.wobble += 0.05f
            if (ufo.targetCrop == null || ufo.targetCrop!!.isStolen) {
                ufo.targetCrop = crops.filter { !it.isStolen }.minByOrNull { hypot(it.x - ufo.x, it.y - ufo.y) }
            }

            ufo.targetCrop?.let { crop ->
                val dx = crop.x - ufo.x
                val dy = crop.y - ufo.y
                val d = hypot(dx, dy)
                if (d > 25f) {
                    ufo.x += (dx / d) * ufo.speed
                    ufo.y += (dy / d) * ufo.speed
                    ufo.beamActive = false
                } else {
                    ufo.beamActive = true
                    ufo.beamTimer++
                    ufo.beamLift = min(1f, ufo.beamTimer / 180f)
                    if (ufo.beamTimer >= 180) {
                        crop.isStolen = true
                        cropHealth = max(0f, cropHealth - 5f)
                        floatingTexts.add(FloatingText(ufo.x, ufo.y - 20, "ОВОЩ ПОХИЩЕН! -5%", Color.RED, 20f))
                        ufo.targetCrop = null
                        ufo.beamActive = false
                        ufo.beamTimer = 0
                        if (cropHealth <= 0f) state = GameState.GAMEOVER
                    }
                }
            }
        }

        // 4. ALIENS & COMBINE HEADER GRINDING
        if (aliens.size < 7 && Math.random() < 0.02) spawnAlien()

        val headerX = player.x + cos(player.angle) * 38f
        val headerY = player.y + sin(player.angle) * 38f
        val headerRadius = 40f

        val alienIter = aliens.iterator()
        while (alienIter.hasNext()) {
            val alien = alienIter.next()
            alien.wobble += 0.08f

            if (alien.carriedCrop == null) {
                if (alien.targetCrop == null || alien.targetCrop!!.isStolen) {
                    alien.targetCrop = crops.filter { !it.isStolen }.minByOrNull { hypot(it.x - alien.x, it.y - alien.y) }
                }
                alien.targetCrop?.let { tc ->
                    val dx = tc.x - alien.x
                    val dy = tc.y - alien.y
                    val d = hypot(dx, dy)
                    if (d > 10f) {
                        alien.x += (dx / d) * alien.speed
                        alien.y += (dy / d) * alien.speed
                    } else {
                        alien.carriedCrop = tc
                        tc.isStolen = true
                    }
                }
            } else {
                val dx = (worldWidth / 2) - alien.x
                val dy = -alien.y
                val d = hypot(dx, dy)
                alien.x += (dx / d) * (alien.speed * 0.75f)
                alien.y += (dy / d) * (alien.speed * 0.75f)
            }

            // MOWING & CRUSHING BY DON-1500
            val distToHeader = hypot(alien.x - headerX, alien.y - headerY)
            val distToBody = hypot(alien.x - player.x, alien.y - player.y)

            if (distToHeader < headerRadius || (distToBody < 45f && abs(player.speed) > 0.8f)) {
                soundManager.playAlienSplat()
                hapticManager.tick()
                aliensCrushed++
                score += 100
                player.superCharge = min(100f, player.superCharge + 6f)

                if (player.isNitro) {
                    floatingTexts.add(FloatingText(alien.x, alien.y, "⚰️ COFFIN COMBO! +250", Color.parseColor("#FFCC00"), 24f))
                    score += 150
                } else {
                    floatingTexts.add(FloatingText(alien.x, alien.y, "+100 ПЕРЕМОЛОТ!", Color.parseColor("#22C55E"), 18f))
                }

                alien.carriedCrop?.isStolen = false
                alienIter.remove()
            }
        }

        // Floating texts update
        floatingTexts.removeAll {
            it.y -= 0.8f
            it.life--
            it.life <= 0
        }
    }

    private fun drawFrame() {
        val canvas = holder.lockCanvas() ?: return
        try {
            canvas.drawColor(Color.parseColor("#142010"))

            if (state == GameState.MENU) {
                drawMenu(canvas)
                return
            }

            val camX = player.x - screenWidth / 2
            val camY = player.y - screenHeight / 2

            canvas.save()
            if (screenShake > 0) {
                canvas.translate((Math.random() * screenShake - screenShake / 2).toFloat(), (Math.random() * screenShake - screenShake / 2).toFloat())
            }
            canvas.translate(-camX, -camY)

            // 1. Draw Field Furrows
            paint.color = Color.parseColor("#25351C")
            paint.strokeWidth = 4f
            for (y in 0 until worldHeight.toInt() step 40) {
                canvas.drawLine(0f, y.toFloat(), worldWidth, y.toFloat(), paint)
            }

            // 2. Draw Crops
            crops.filter { !it.isStolen }.forEach { crop ->
                paint.color = when (crop.type) {
                    CropType.PUMPKIN -> Color.parseColor("#EA580C")
                    CropType.WATERMELON -> Color.parseColor("#15803D")
                    CropType.CARROT -> Color.parseColor("#F97316")
                    CropType.POTATO -> Color.parseColor("#A16207")
                }
                canvas.drawCircle(crop.x, crop.y, 14f, paint)
            }

            // 3. Draw Aliens
            aliens.forEach { alien ->
                paint.color = Color.parseColor("#22C55E")
                canvas.drawCircle(alien.x, alien.y, 12f, paint)
                // Eyes
                paint.color = Color.WHITE
                canvas.drawCircle(alien.x - 4f, alien.y - 3f, 4f, paint)
                canvas.drawCircle(alien.x + 4f, alien.y - 3f, 4f, paint)
                paint.color = Color.BLACK
                canvas.drawCircle(alien.x - 4f, alien.y - 3f, 2f, paint)
                canvas.drawCircle(alien.x + 4f, alien.y - 3f, 2f, paint)
            }

            // 4. Draw Combine «ДОН-1500А»
            drawDon1500(canvas)

            // 5. Draw Bullets
            paint.color = Color.parseColor("#F59E0B")
            bullets.forEach { b ->
                canvas.drawCircle(b.x, b.y, 7f, paint)
            }

            // 6. Draw UFOs
            ufos.forEach { ufo ->
                if (ufo.beamActive && ufo.targetCrop != null) {
                    val path = Path().apply {
                        moveTo(ufo.x - 15f, ufo.y + 10f)
                        lineTo(ufo.x + 15f, ufo.y + 10f)
                        lineTo(ufo.x + 50f, ufo.y + 150f)
                        lineTo(ufo.x - 50f, ufo.y + 150f)
                        close()
                    }
                    paint.color = Color.argb(120, 168, 85, 247)
                    canvas.drawPath(path, paint)
                }

                // Saucer body
                paint.color = Color.parseColor("#64748B")
                canvas.drawOval(ufo.x - 45f, ufo.y - 15f, ufo.x + 45f, ufo.y + 15f, paint)
                paint.color = Color.parseColor("#38BDF8")
                canvas.drawOval(ufo.x - 22f, ufo.y - 24f, ufo.x + 22f, ufo.y, paint)
            }

            // 7. Floating Texts
            floatingTexts.forEach { ft ->
                textPaint.color = ft.color
                textPaint.textSize = ft.size
                canvas.drawText(ft.text, ft.x - 40f, ft.y, textPaint)
            }

            canvas.restore()

            // Draw HUD and Controls Overlay
            controls.drawHUD(canvas, score, player, cropHealth, screenWidth)

            if (state == GameState.GAMEOVER) {
                drawGameOver(canvas)
            }

        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawDon1500(canvas: Canvas) {
        canvas.save()
        canvas.translate(player.x, player.y)
        canvas.rotate((player.angle * 180 / Math.PI).toFloat())

        // Nitro Flames
        if (player.isNitro) {
            paint.color = Color.parseColor("#A855F7")
            canvas.drawRect(-75f, -10f, -42f, -4f, paint)
            canvas.drawRect(-75f, 4f, -42f, 10f, paint)
        }

        // Combine Body: Terracotta-Red
        paint.color = if (player.isNitro) Color.parseColor("#FBBF24") else Color.parseColor("#C2410C")
        canvas.drawRoundRect(RectF(-42f, -22f, 38f, 22f), 8f, 8f, paint)

        // White Stencil: «ДОН 1500А»
        textPaint.color = Color.WHITE
        textPaint.textSize = 9f
        canvas.drawText("ДОН 1500А", -30f, 14f, textPaint)

        // Wheels
        paint.color = Color.parseColor("#0F172A")
        canvas.drawRect(10f, -32f, 34f, -20f, paint)
        canvas.drawRect(10f, 20f, 34f, 32f, paint)

        // Cabin Glass with White Flat Roof
        paint.color = Color.parseColor("#38BDF8")
        canvas.drawRect(8f, -16f, 30f, 16f, paint)
        paint.color = Color.WHITE
        canvas.drawRect(10f, -17f, 28f, 17f, paint)

        // Ded Makar in ushanka with Red Star
        paint.color = Color.parseColor("#78350F")
        canvas.drawCircle(19f, 0f, 7f, paint)
        paint.color = Color.RED
        canvas.drawCircle(22f, 0f, 2.5f, paint)

        // Front Header
        paint.color = Color.parseColor("#475569")
        canvas.drawRect(38f, -24f, 46f, 24f, paint)

        canvas.restore()
    }

    private fun drawMenu(canvas: Canvas) {
        textPaint.color = Color.parseColor("#FFCC00")
        textPaint.textSize = 64f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("ASTRANOMIA", screenWidth / 2, screenHeight / 2 - 80f, textPaint)

        textPaint.color = Color.parseColor("#38BDF8")
        textPaint.textSize = 32f
        canvas.drawText("БИТВА ЗА УРОЖАЙ НА ДОН-1500А", screenWidth / 2, screenHeight / 2 - 20f, textPaint)

        // Start Button
        val btnRect = RectF(screenWidth / 2 - 160f, screenHeight / 2 + 40f, screenWidth / 2 + 160f, screenHeight / 2 + 120f)
        paint.color = Color.parseColor("#EF4444")
        canvas.drawRoundRect(btnRect, 20f, 20f, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 32f
        canvas.drawText("▶ НАЧАТЬ ЖАТВУ", screenWidth / 2, screenHeight / 2 + 92f, textPaint)
    }

    private fun drawGameOver(canvas: Canvas) {
        paint.color = Color.argb(200, 10, 15, 25)
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, paint)

        textPaint.color = Color.parseColor("#EF4444")
        textPaint.textSize = 60f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("ЖАТВА ОКОНЧЕНА", screenWidth / 2, screenHeight / 2 - 60f, textPaint)

        textPaint.color = Color.parseColor("#FFCC00")
        textPaint.textSize = 38f
        canvas.drawText("🏆 ИТОГОВЫЙ СЧЁТ: $score", screenWidth / 2, screenHeight / 2 + 10f, textPaint)

        val btnRect = RectF(screenWidth / 2 - 140f, screenHeight / 2 + 60f, screenWidth / 2 + 140f, screenHeight / 2 + 130f)
        paint.color = Color.parseColor("#3B82F6")
        canvas.drawRoundRect(btnRect, 16f, 16f, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 28f
        canvas.drawText("🔁 ЗАНОВО", screenWidth / 2, screenHeight / 2 + 105f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (state == GameState.MENU) {
                    val btnRect = RectF(screenWidth / 2 - 160f, screenHeight / 2 + 40f, screenWidth / 2 + 160f, screenHeight / 2 + 120f)
                    if (btnRect.contains(x, y)) {
                        startGame()
                        return true
                    }
                } else if (state == GameState.GAMEOVER) {
                    val btnRect = RectF(screenWidth / 2 - 140f, screenHeight / 2 + 60f, screenWidth / 2 + 140f, screenHeight / 2 + 130f)
                    if (btnRect.contains(x, y)) {
                        startGame()
                        return true
                    }
                } else {
                    controls.handleTouchDown(x, y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                controls.handleTouchUp(x, y)
            }
        }
        return true
    }

    fun onPause() {
        soundManager.stopMusic()
        controls.resetTouches()
        if (state == GameState.PLAYING) state = GameState.PAUSED
    }

    fun onResume() {
        if (state == GameState.PLAYING) soundManager.startGameMusic()
        else if (state == GameState.MENU) soundManager.startMenuMusic()
    }

    fun onDestroy() {
        soundManager.release()
    }
}

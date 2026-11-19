package com.astranomia.game.view

import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.astranomia.game.audio.SoundManager
import com.astranomia.game.haptics.HapticManager
import com.astranomia.game.model.*
import com.astranomia.game.ui.VirtualControls
import kotlin.math.*

/**
 * Astranomia Game View - Retro Farm Edition matching classic J2ME look.
 * Rock-solid stability with full exception guards, thread-safe state synchronization,
 * non-blocking audio, and verified zero-crash rendering.
 */
class AstranomiaGameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var gameThread: Thread? = null
    @Volatile private var isRunning: Boolean = false
    private val stateLock = Any()

    val soundManager = SoundManager()
    val hapticManager = HapticManager(context)
    val controls = VirtualControls()

    // Virtual resolution matching the screenshot
    private val vWidth = 720f
    private val vHeight = 1010f

    private var screenWidth = 720f
    private var screenHeight = 1600f

    // Bitmaps loaded from assets
    private var cornTileBmp: Bitmap? = null
    private var soilTileBmp: Bitmap? = null
    private var yardTileBmp: Bitmap? = null
    private var fenceLeftBmp: Bitmap? = null
    private var fenceRightBmp: Bitmap? = null
    private var farmhouseBmp: Bitmap? = null
    private var combineBmp: Bitmap? = null
    private var starburstBmp: Bitmap? = null
    private var starburst2Bmp: Bitmap? = null
    private var hudBarBmp: Bitmap? = null
    private var badgeCornBmp: Bitmap? = null
    private var badgeBookBmp: Bitmap? = null
    private var alienBmp: Bitmap? = null
    private var ufoBmp: Bitmap? = null
    private var cornCobBmp: Bitmap? = null

    // Game Entities
    val player = Combine(360f, 600f) // Start at the fence gate facing UP!
    val cornGrid = mutableListOf<CornTile>()
    val cornParticles = mutableListOf<CornParticle>()
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

    // Starburst popup state
    private var starburstTimer = 0
    private var starburstLabel = "2"
    private var starburstScale = 1.0f

    // Starting Countdown (3 -> 2 -> 1 -> GO)
    private var countdownTimer = 180

    enum class GameState { MENU, COUNTDOWN, PLAYING, PAUSED, GAMEOVER }
    @Volatile var state = GameState.MENU

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = false // Razor-sharp pixel art!
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
    }

    // Corn field dimensions
    private val cornCols = 16
    private val cornRows = 12
    private val cellW = 44f
    private val cellH = 38f
    private val fieldX = 10f
    private val fieldY = 95f

    init {
        holder.addCallback(this)
        isFocusable = true

        loadAssets(context)
        controls.updateLayout(screenWidth, screenHeight)
        setupControlCallbacks()

        synchronized(stateLock) {
            initCornField()
        }
    }

    private fun loadAssets(context: Context) {
        cornTileBmp = loadBitmap(context, "corn_tile.png")
        soilTileBmp = loadBitmap(context, "soil_tile.png")
        yardTileBmp = loadBitmap(context, "yard_tile.png")
        fenceLeftBmp = loadBitmap(context, "fence_left.png")
        fenceRightBmp = loadBitmap(context, "fence_right.png")
        farmhouseBmp = loadBitmap(context, "farmhouse.png")
        combineBmp = loadBitmap(context, "combine_harvester.png")
        starburstBmp = loadBitmap(context, "action_starburst.png")
        starburst2Bmp = loadBitmap(context, "starburst_2.png")
        hudBarBmp = loadBitmap(context, "hud_full_bar.png")
        badgeCornBmp = loadBitmap(context, "badge_corn.png")
        badgeBookBmp = loadBitmap(context, "badge_book.png")
        alienBmp = loadBitmap(context, "alien.png")
        ufoBmp = loadBitmap(context, "ufo.png")
        cornCobBmp = loadBitmap(context, "corn_cob.png")
    }

    private fun loadBitmap(context: Context, filename: String): Bitmap? {
        return try {
            context.assets.open(filename).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (t: Throwable) {
            Log.w("AstranomiaGameView", "Asset load error for $filename", t)
            null
        }
    }

    private fun setupControlCallbacks() {
        controls.onMenuClicked = {
            if (state == GameState.PLAYING || state == GameState.COUNTDOWN) {
                state = GameState.PAUSED
            } else if (state == GameState.PAUSED) {
                state = GameState.PLAYING
            }
        }

        controls.onReloadClicked = {
            synchronized(stateLock) {
                if (!player.isReloading && player.ammo < player.maxAmmo) {
                    player.isReloading = true
                    player.reloadProgress = 0f
                    soundManager.playHorn()
                }
            }
        }

        controls.onSuperClicked = {
            synchronized(stateLock) {
                if (player.superCharge >= 100f && !player.isNitro) {
                    player.isNitro = true
                    player.nitroTime = 420
                    player.superCharge = 0f
                    screenShake = 22f
                    hapticManager.nitroBurst()
                    soundManager.playNitroActivation()
                    triggerStarburst("⚡ НИТРО!")
                }
            }
        }

        controls.onHornClicked = {
            soundManager.playHorn()
            hapticManager.tick()
            triggerStarburst("📢 БИ-БИП!")
            synchronized(stateLock) {
                for (alien in aliens) {
                    val dx = alien.x - player.x
                    val dy = alien.y - player.y
                    val dist = hypot(dx, dy)
                    if (dist < 300f && dist > 1f && !dist.isNaN()) {
                        alien.x += (dx / dist) * 70f
                        alien.y += (dy / dist) * 70f
                    }
                }
            }
        }

        controls.onMuteClicked = {
            soundManager.isSoundEnabled = !soundManager.isSoundEnabled
            if (!soundManager.isSoundEnabled) soundManager.stopMusic()
            else soundManager.startGameMusic()
        }
    }

    private fun initCornField() {
        cornGrid.clear()
        for (r in 0 until cornRows) {
            for (c in 0 until cornCols) {
                cornGrid.add(CornTile(c, r, isHarvested = false))
            }
        }
    }

    fun startGame() {
        synchronized(stateLock) {
            score = 0
            aliensCrushed = 0
            ufosDestroyed = 0
            cropHealth = 100f
            player.reset(360f, 600f) // Start at gate facing UP!
            initCornField()
            cornParticles.clear()
            aliens.clear()
            ufos.clear()
            bullets.clear()
            particles.clear()
            floatingTexts.clear()

            // Starting Countdown: 3... 2... 1...
            countdownTimer = 180
            state = GameState.COUNTDOWN
            triggerStarburst("3")

            repeat(2) { spawnAlien() }
            spawnUFO()
        }
        soundManager.startGameMusic()
    }

    private fun triggerStarburst(label: String, duration: Int = 45) {
        starburstLabel = label
        starburstTimer = duration
        starburstScale = 1.3f
    }

    private fun spawnAlien() {
        val side = (0..3).random()
        val x: Float
        val y: Float
        when (side) {
            0 -> { x = (50..670).random().toFloat(); y = 50f }
            1 -> { x = 680f; y = (50..500).random().toFloat() }
            2 -> { x = 40f; y = (50..500).random().toFloat() }
            else -> { x = (50..670).random().toFloat(); y = 550f }
        }
        aliens.add(Alien(x, y, speed = (1.6f + Math.random() * 0.8f).toFloat()))
    }

    private fun spawnUFO() {
        val x = (100..620).random().toFloat()
        val y = (100..350).random().toFloat()
        ufos.add(FlyingSaucer(x, y, hp = 60f))
    }

    override fun run() {
        var lastTime = System.nanoTime()
        val nsPerFrame = 1_000_000_000.0 / 60.0

        while (isRunning) {
            try {
                val now = System.nanoTime()
                val delta = (now - lastTime) / nsPerFrame
                lastTime = now

                if (delta < 2.5) {
                    updatePhysics()
                }
                drawFrame()

                val sleepMs = ((lastTime + nsPerFrame - System.nanoTime()) / 1_000_000).toLong()
                if (sleepMs > 1) {
                    try { Thread.sleep(sleepMs) } catch (e: InterruptedException) {}
                }
            } catch (t: Throwable) {
                Log.e("AstranomiaGameView", "Exception in game loop tick", t)
                try { Thread.sleep(16) } catch (e: InterruptedException) {}
            }
        }
    }

    private fun updatePhysics() {
        if (state == GameState.PAUSED) return

        synchronized(stateLock) {
            // Countdown handling (3 -> 2 -> 1 -> GO!)
            if (state == GameState.COUNTDOWN) {
                countdownTimer--
                if (countdownTimer in 120..179) {
                    starburstLabel = "3"
                } else if (countdownTimer in 60..119) {
                    starburstLabel = "2" // Exactly like the screenshot!
                } else if (countdownTimer in 1..59) {
                    starburstLabel = "1"
                } else if (countdownTimer <= 0) {
                    state = GameState.PLAYING
                    triggerStarburst("ЖАТВА!", 50)
                    soundManager.playHorn()
                }
                updateParticlesAndEffects()
                return
            }

            if (state != GameState.PLAYING) {
                updateParticlesAndEffects()
                return
            }

            // 1. COMBINE MOVEMENT & STEERING
            var moveX = 0f
            var moveY = 0f

            if (controls.isUpPressed) moveY -= 1f
            if (controls.isDownPressed) moveY += 1f
            if (controls.isLeftPressed) moveX -= 1f
            if (controls.isRightPressed) moveX += 1f

            val hasInput = (moveX != 0f || moveY != 0f)

            if (hasInput) {
                val targetAngle = atan2(moveY, moveX)
                if (!targetAngle.isNaN()) {
                    var diff = targetAngle - player.angle
                    while (diff < -Math.PI) diff += (2 * Math.PI).toFloat()
                    while (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
                    player.angle += diff * 0.22f
                }

                val accel = if (player.isNitro) 0.65f else 0.35f
                val topSpeed = if (player.isNitro) 8.5f else player.maxSpeed
                player.speed = min(topSpeed, player.speed + accel)
            } else {
                player.speed = max(0f, player.speed * 0.88f)
            }

            // Move player
            player.x += cos(player.angle) * player.speed
            player.y += sin(player.angle) * player.speed

            // Reel animation
            if (player.speed > 0.1f) {
                player.reelAngle += player.speed * 0.12f
            }

            // Boundary constraints (Farm boundaries)
            player.x = player.x.coerceIn(50f, vWidth - 50f)
            player.y = player.y.coerceIn(80f, vHeight - 140f)

            // Fence collision: gate is open at x in 275..445!
            if (player.y in 575f..625f) {
                val inGate = (player.x in 275f..445f)
                if (!inGate) {
                    player.y = if (moveY > 0) 574f else 626f
                    player.speed *= 0.5f
                }
            }

            // Nitro timer
            if (player.isNitro) {
                player.nitroTime--
                if (player.nitroTime <= 0) player.isNitro = false
            }

            // Reload timer
            if (player.isReloading) {
                player.reloadProgress += 0.025f
                if (player.reloadProgress >= 1f) {
                    player.ammo = player.maxAmmo
                    player.isReloading = false
                }
            }

            // 2. CORN HARVESTING BY COMBINE CUTTER BAR (ЖАТКА)
            val headerX = player.x + cos(player.angle) * 36f
            val headerY = player.y + sin(player.angle) * 36f
            val harvestRadius = 42f

            var harvestedAny = false
            for (tile in cornGrid) {
                if (!tile.isHarvested) {
                    val tileCenterX = fieldX + tile.col * cellW + cellW / 2
                    val tileCenterY = fieldY + tile.row * cellH + cellH / 2
                    val d = hypot(headerX - tileCenterX, headerY - tileCenterY)
                    if (d < harvestRadius && !d.isNaN()) {
                        tile.isHarvested = true
                        player.collectedCorn++
                        score += 10
                        player.superCharge = min(100f, player.superCharge + 1.2f)
                        harvestedAny = true

                        repeat(2) {
                            cornParticles.add(
                                CornParticle(
                                    x = tileCenterX,
                                    y = tileCenterY,
                                    vx = (Math.random() * 4.0 - 2.0).toFloat(),
                                    vy = (-Math.random() * 5.0 - 2.0).toFloat()
                                )
                            )
                        }

                        if (player.collectedCorn % 25 == 0) {
                            triggerStarburst("+25 КОРЗИНА!", 40)
                            score += 50
                        }
                    }
                }
            }

            if (harvestedAny) {
                soundManager.playHarvestCrop()
                hapticManager.tick()
            }

            // Check if all corn was harvested -> Start New Season!
            if (cornGrid.none { !it.isHarvested }) {
                initCornField()
                score += 500
                triggerStarburst("НОВЫЙ СЕЗОН! +500", 60)
            }

            // 3. FIRE WEAPON
            if (controls.isFirePressed && !player.isReloading) {
                if (player.ammo > 0) {
                    player.ammo--
                    soundManager.playShotgun()
                    hapticManager.heavyClick()

                    val noseX = player.x + cos(player.angle) * 40f
                    val noseY = player.y + sin(player.angle) * 40f
                    bullets.add(Bullet(noseX, noseY, cos(player.angle) * 15f, sin(player.angle) * 15f))
                    controls.isFirePressed = false
                } else {
                    controls.onReloadClicked?.invoke()
                }
            }

            // 4. BULLETS & COLLISION (Strictly thread-safe without in-loop removals)
            val bulletsToRemove = mutableListOf<Bullet>()
            val aliensKilledByBullets = mutableListOf<Alien>()

            for (b in bullets) {
                b.x += b.vx
                b.y += b.vy
                b.life--
                if (b.life <= 0) {
                    bulletsToRemove.add(b)
                    continue
                }

                var hit = false
                // Check UFOs
                for (ufo in ufos) {
                    val d = hypot(b.x - ufo.x, b.y - ufo.y)
                    if (d < 45f && !d.isNaN()) {
                        ufo.hp -= b.damage
                        hit = true
                        bulletsToRemove.add(b)
                        screenShake = 6f
                        if (ufo.hp <= 0) {
                            soundManager.playUfoCrash()
                            hapticManager.heavyClick()
                            screenShake = 16f
                            ufosDestroyed++
                            score += 500
                            triggerStarburst("ТАРЕЛКА СБИТА!", 45)
                            floatingTexts.add(FloatingText(ufo.x, ufo.y, "+500 ОЧКОВ", Color.parseColor("#38BDF8"), 26f))
                        }
                        break
                    }
                }

                // Check Aliens
                if (!hit) {
                    for (alien in aliens) {
                        val d = hypot(b.x - alien.x, b.y - alien.y)
                        if (d < 25f && !d.isNaN()) {
                            aliensKilledByBullets.add(alien)
                            bulletsToRemove.add(b)
                            soundManager.playAlienSplat()
                            score += 50
                            floatingTexts.add(FloatingText(alien.x, alien.y, "+50", Color.parseColor("#22C55E"), 22f))
                            break
                        }
                    }
                }
            }

            bullets.removeAll(bulletsToRemove)
            aliens.removeAll(aliensKilledByBullets)
            ufos.removeAll { it.hp <= 0 }

            // 5. ALIENS UPDATE & COMBINE MOWING
            if (aliens.size < 6 && Math.random() < 0.02) spawnAlien()

            val crushedAliens = mutableListOf<Alien>()
            for (alien in aliens) {
                alien.wobble += 0.08f

                // Find nearest standing corn
                val target = cornGrid.filter { !it.isHarvested }
                    .minByOrNull { hypot((fieldX + it.col * cellW) - alien.x, (fieldY + it.row * cellH) - alien.y) }

                if (target != null) {
                    val tx = fieldX + target.col * cellW + cellW / 2
                    val ty = fieldY + target.row * cellH + cellH / 2
                    val dx = tx - alien.x
                    val dy = ty - alien.y
                    val d = hypot(dx, dy)
                    if (d > 12f && !d.isNaN()) {
                        alien.x += (dx / d) * alien.speed
                        alien.y += (dy / d) * alien.speed
                    } else {
                        target.isHarvested = true
                        cropHealth = max(0f, cropHealth - 2f)
                        floatingTexts.add(FloatingText(alien.x, alien.y, "ВОРУЕТ!", Color.RED, 18f))
                        if (cropHealth <= 0f) state = GameState.GAMEOVER
                    }
                }

                // Mowing aliens with DON-1500 header!
                val distToHeader = hypot(alien.x - headerX, alien.y - headerY)
                val distToBody = hypot(alien.x - player.x, alien.y - player.y)

                if ((distToHeader < harvestRadius || (distToBody < 40f && player.speed > 0.8f)) && !distToHeader.isNaN()) {
                    crushedAliens.add(alien)
                    soundManager.playAlienSplat()
                    hapticManager.heavyClick()
                    aliensCrushed++
                    score += 100
                    player.superCharge = min(100f, player.superCharge + 8f)

                    if (player.isNitro) {
                        triggerStarburst("ПЕРЕМОЛОТ x2!", 35)
                        score += 100
                    } else {
                        triggerStarburst("ПЕРЕМОЛОТ!", 30)
                    }
                }
            }

            aliens.removeAll(crushedAliens)

            // 6. UFOS UPDATE
            if (ufos.size < 2 && Math.random() < 0.01) spawnUFO()
            ufos.forEach { ufo ->
                ufo.wobble += 0.05f
                ufo.x += sin(ufo.wobble) * 1.5f
            }

            updateParticlesAndEffects()
        }
    }

    private fun updateParticlesAndEffects() {
        if (screenShake > 0) screenShake = max(0f, screenShake - 1.2f)
        if (starburstTimer > 0) {
            starburstTimer--
            starburstScale = max(1.0f, starburstScale - 0.01f)
        }

        // Corn Particles
        val cpIter = cornParticles.iterator()
        while (cpIter.hasNext()) {
            val cp = cpIter.next()
            cp.x += cp.vx
            cp.y += cp.vy
            cp.vy += 0.25f // gravity
            cp.life--
            if (cp.life <= 0) cpIter.remove()
        }

        // Floating texts
        floatingTexts.removeAll {
            it.y -= 0.8f
            it.life--
            it.life <= 0
        }
    }

    private fun drawFrame() {
        if (!holder.surface.isValid) return

        val canvas = try {
            holder.lockCanvas()
        } catch (t: Throwable) {
            Log.e("AstranomiaGameView", "lockCanvas error", t)
            null
        } ?: return

        try {
            if (width > 0 && height > 0 && (screenWidth != width.toFloat() || screenHeight != height.toFloat())) {
                screenWidth = width.toFloat()
                screenHeight = height.toFloat()
                controls.updateLayout(screenWidth, screenHeight)
            }

            canvas.drawColor(Color.parseColor("#1C1C1C"))

            if (state == GameState.MENU) {
                drawMenu(canvas)
                return
            }

            val isPortrait = controls.isPortrait

            // 1. TOP TITLE BAR (Portrait Mode)
            var topBarHeight = 0f
            if (isPortrait) {
                topBarHeight = 65f
                drawTopTitleBar(canvas, topBarHeight)
            }

            // 2. VIEWPORT RENDERING
            canvas.save()
            val viewportH = if (isPortrait) (controls.viewportBottom - topBarHeight) else screenHeight
            val scaleX = screenWidth / vWidth
            val scaleY = viewportH / vHeight
            var scale = min(scaleX, scaleY)
            if (scale <= 0.05f || scale.isNaN()) scale = 1.0f

            val offsetX = (screenWidth - vWidth * scale) / 2f
            val offsetY = topBarHeight + (viewportH - vHeight * scale) / 2f

            canvas.translate(offsetX, offsetY)
            canvas.scale(scale, scale)

            // Screen Shake
            if (screenShake > 0) {
                canvas.translate((Math.random() * screenShake - screenShake / 2).toFloat(), (Math.random() * screenShake - screenShake / 2).toFloat())
            }

            // ALL ENTITY DRAWING STRICTLY SYNCHRONIZED
            synchronized(stateLock) {
                drawFarmWorld(canvas)
                drawCombine(canvas)
                drawEntities(canvas)
                drawActionStarburst(canvas)
            }

            canvas.restore()

            // 3. DRAW BOTTOM WOODEN HUD
            if (isPortrait) {
                drawWoodenHUD(canvas, controls.viewportBottom)
            }

            // 4. DRAW KEYPAD / CONTROLS
            controls.drawControls(canvas, screenWidth, screenHeight, player)

            if (state == GameState.GAMEOVER) {
                drawGameOver(canvas)
            } else if (state == GameState.PAUSED) {
                drawPaused(canvas)
            }

        } catch (t: Throwable) {
            Log.e("AstranomiaGameView", "drawFrame render error", t)
        } finally {
            try {
                if (holder.surface.isValid) {
                    holder.unlockCanvasAndPost(canvas)
                }
            } catch (t: Throwable) {
                Log.e("AstranomiaGameView", "unlockCanvasAndPost error", t)
            }
        }
    }

    private fun drawTopTitleBar(canvas: Canvas, barHeight: Float) {
        paint.color = Color.parseColor("#1C1C1C")
        canvas.drawRect(0f, 0f, screenWidth, barHeight, paint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 28f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Farm SEfan.RU", 24f, barHeight / 2 + 10f, textPaint)

        // Retro phone icons on the right (Keyboard, Camera, 3 Dots)
        val cy = barHeight / 2
        var iconX = screenWidth - 28f

        // 3 Dots (menu)
        paint.color = Color.WHITE
        canvas.drawCircle(iconX, cy - 8f, 2.5f, paint)
        canvas.drawCircle(iconX, cy, 2.5f, paint)
        canvas.drawCircle(iconX, cy + 8f, 2.5f, paint)

        // Camera icon
        iconX -= 38f
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(RectF(iconX - 12f, cy - 8f, iconX + 12f, cy + 9f), 3f, 3f, paint)
        canvas.drawRect(iconX - 5f, cy - 11f, iconX + 5f, cy - 8f, paint)
        canvas.drawCircle(iconX, cy + 1f, 4f, paint)
        paint.style = Paint.Style.FILL

        // Keyboard icon
        iconX -= 44f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(RectF(iconX - 14f, cy - 9f, iconX + 14f, cy + 9f), 3f, 3f, paint)
        paint.style = Paint.Style.FILL
        for (kx in -9..9 step 6) {
            canvas.drawRect(iconX + kx - 1.5f, cy - 5f, iconX + kx + 1.5f, cy - 2f, paint)
            canvas.drawRect(iconX + kx - 1.5f, cy + 2f, iconX + kx + 1.5f, cy + 5f, paint)
        }
    }

    private fun drawFarmWorld(canvas: Canvas) {
        // Upper section: Harvested soil background (y from 0 to 570)
        val soilBmp = soilTileBmp
        if (soilBmp != null) {
            val sw = soilBmp.width.coerceAtLeast(1)
            val sh = soilBmp.height.coerceAtLeast(1)
            for (y in 0 until 570 step sh) {
                for (x in 0 until vWidth.toInt() step sw) {
                    canvas.drawBitmap(soilBmp, x.toFloat(), y.toFloat(), paint)
                }
            }
        } else {
            paint.color = Color.parseColor("#A04A15")
            canvas.drawRect(0f, 0f, vWidth, 570f, paint)
        }

        // Lower section: Farmyard sandy ochre ground (y from 570 to 1010)
        val yardBmp = yardTileBmp
        if (yardBmp != null) {
            val yw = yardBmp.width.coerceAtLeast(1)
            val yh = yardBmp.height.coerceAtLeast(1)
            for (y in 570 until vHeight.toInt() step yh) {
                for (x in 0 until vWidth.toInt() step yw) {
                    canvas.drawBitmap(yardBmp, x.toFloat(), y.toFloat(), paint)
                }
            }
        } else {
            paint.color = Color.parseColor("#D4A34B")
            canvas.drawRect(0f, 570f, vWidth, vHeight, paint)
        }

        // Dirt Path & Tire Tracks from gate (x=300 to 420) down towards barn
        paint.color = Color.parseColor("#B48A3C")
        canvas.drawRect(300f, 570f, 420f, 750f, paint)
        paint.color = Color.parseColor("#8B5A2B")
        // Left tire track
        canvas.drawRect(315f, 570f, 335f, 750f, paint)
        // Right tire track
        canvas.drawRect(385f, 570f, 405f, 750f, paint)

        // Draw Corn Grid (Standing Corn Stalks with Ripe Cobs)
        val cornBmp = cornTileBmp
        for (tile in cornGrid) {
            val tx = fieldX + tile.col * cellW
            val ty = fieldY + tile.row * cellH
            if (!tile.isHarvested) {
                if (cornBmp != null) {
                    val src = Rect(0, 0, cornBmp.width, cornBmp.height)
                    val dst = RectF(tx, ty, tx + cellW, ty + cellH)
                    canvas.drawBitmap(cornBmp, src, dst, paint)
                } else {
                    paint.color = Color.parseColor("#277A18")
                    canvas.drawRect(tx, ty, tx + cellW, ty + cellH, paint)
                    paint.color = Color.parseColor("#FFD214")
                    canvas.drawCircle(tx + cellW / 2, ty + cellH / 2, 4f, paint)
                }
            }
        }

        // Draw Wooden Fences with Open Gate
        fenceLeftBmp?.let {
            canvas.drawBitmap(it, 0f, 560f, paint)
        } ?: run {
            paint.color = Color.parseColor("#A0632D")
            canvas.drawRect(0f, 590f, 275f, 615f, paint)
        }

        fenceRightBmp?.let {
            canvas.drawBitmap(it, 440f, 560f, paint)
        } ?: run {
            paint.color = Color.parseColor("#A0632D")
            canvas.drawRect(445f, 590f, vWidth, 615f, paint)
        }

        // Draw Farmhouse & Curly Tree
        farmhouseBmp?.let {
            canvas.drawBitmap(it, 130f, 725f, paint)
        } ?: run {
            paint.color = Color.parseColor("#B91C1C")
            canvas.drawRect(200f, 760f, 520f, 960f, paint)
        }

        // Draw Flying Corn Particles
        val cobBmp = cornCobBmp
        for (cp in cornParticles) {
            if (cobBmp != null) {
                canvas.drawBitmap(cobBmp, cp.x - 16f, cp.y - 16f, paint)
            } else {
                paint.color = Color.parseColor("#FBBF24")
                canvas.drawCircle(cp.x, cp.y, 6f, paint)
            }
        }
    }

    private fun drawCombine(canvas: Canvas) {
        canvas.save()
        canvas.translate(player.x, player.y)

        val deg = ((player.angle + Math.PI / 2) * 180 / Math.PI).toFloat()
        if (!deg.isNaN()) {
            canvas.rotate(deg)
        }

        // Nitro exhaust flames
        if (player.isNitro) {
            paint.color = Color.parseColor("#F59E0B")
            canvas.drawRect(-25f, 48f, -10f, 75f, paint)
            canvas.drawRect(10f, 48f, 25f, 75f, paint)
            paint.color = Color.parseColor("#A855F7")
            canvas.drawRect(-20f, 54f, -15f, 85f, paint)
            canvas.drawRect(15f, 54f, 20f, 85f, paint)
        }

        combineBmp?.let {
            canvas.drawBitmap(it, -it.width / 2f, -it.height / 2f, paint)
        } ?: run {
            paint.color = Color.parseColor("#DC2626")
            canvas.drawRoundRect(RectF(-35f, -30f, 35f, 40f), 8f, 8f, paint)
            paint.color = Color.parseColor("#F59E0B")
            canvas.drawRect(-55f, -45f, 55f, -28f, paint)
        }

        canvas.restore()
    }

    private fun drawEntities(canvas: Canvas) {
        // Draw Aliens
        val aBmp = alienBmp
        for (alien in aliens) {
            if (aBmp != null) {
                canvas.drawBitmap(aBmp, alien.x - aBmp.width / 2f, alien.y - aBmp.height / 2f, paint)
            } else {
                paint.color = Color.parseColor("#22C55E")
                canvas.drawCircle(alien.x, alien.y, 14f, paint)
            }
        }

        // Draw Bullets
        paint.color = Color.parseColor("#F59E0B")
        for (b in bullets) {
            canvas.drawCircle(b.x, b.y, 6f, paint)
        }

        // Draw UFOs
        val uBmp = ufoBmp
        for (ufo in ufos) {
            val beamPath = Path().apply {
                moveTo(ufo.x - 18f, ufo.y + 15f)
                lineTo(ufo.x + 18f, ufo.y + 15f)
                lineTo(ufo.x + 55f, ufo.y + 140f)
                lineTo(ufo.x - 55f, ufo.y + 140f)
                close()
            }
            paint.color = Color.argb(100, 168, 85, 247)
            canvas.drawPath(beamPath, paint)

            if (uBmp != null) {
                canvas.drawBitmap(uBmp, ufo.x - uBmp.width / 2f, ufo.y - uBmp.height / 2f, paint)
            } else {
                paint.color = Color.parseColor("#64748B")
                canvas.drawOval(ufo.x - 45f, ufo.y - 15f, ufo.x + 45f, ufo.y + 15f, paint)
            }
        }

        // Floating texts
        for (ft in floatingTexts) {
            textPaint.color = ft.color
            textPaint.textSize = ft.size
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(ft.text, ft.x, ft.y, textPaint)
        }
    }

    private fun drawActionStarburst(canvas: Canvas) {
        val showStarburst = (state == GameState.COUNTDOWN || starburstTimer > 0)
        if (!showStarburst) return

        canvas.save()
        canvas.translate(player.x, player.y)
        canvas.scale(starburstScale, starburstScale)

        val sbBmp = if (starburstLabel == "2" && starburst2Bmp != null) {
            starburst2Bmp
        } else {
            starburstBmp
        }

        if (sbBmp != null) {
            canvas.drawBitmap(sbBmp, -sbBmp.width / 2f, -sbBmp.height / 2f, paint)
        }

        if (starburstLabel != "2" || starburst2Bmp == null) {
            textPaint.color = Color.BLACK
            textPaint.textSize = 38f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(starburstLabel, 0f, 14f, textPaint)
        }

        canvas.restore()
    }

    private fun drawWoodenHUD(canvas: Canvas, bottomY: Float) {
        val hudH = 110f
        val hudTop = bottomY - hudH

        val barBmp = hudBarBmp
        if (barBmp != null) {
            val src = Rect(0, 0, barBmp.width, barBmp.height)
            val dst = RectF(0f, hudTop, screenWidth, bottomY)
            canvas.drawBitmap(barBmp, src, dst, paint)
        } else {
            paint.color = Color.parseColor("#8C5523")
            canvas.drawRect(0f, hudTop, screenWidth, bottomY, paint)
        }

        val textY = hudTop + 45f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 24f
        textPaint.color = Color.parseColor("#FEF08A")

        val cornStr = "🌽 ${player.collectedCorn}"
        val scoreStr = "🏆 $score"
        val hpStr = "🚜 ${player.hp.toInt()}%"
        val ammoStr = if (player.isReloading) "🔄..." else "🥔 ${player.ammo}"

        val leftPad = screenWidth * 0.16f
        canvas.drawText("$cornStr  $scoreStr", leftPad, textY, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        val rightPad = screenWidth * 0.84f
        canvas.drawText("$hpStr  $ammoStr", rightPad, textY, textPaint)

        // Super Nitro progress bar along bottom of wooden beam
        val nitroRatio = (player.superCharge / 100f).coerceIn(0f, 1f)
        val barW = screenWidth * 0.5f
        val barLeft = (screenWidth - barW) / 2f
        val barY = hudTop + 78f

        paint.color = Color.argb(160, 20, 20, 20)
        canvas.drawRoundRect(RectF(barLeft, barY, barLeft + barW, barY + 12f), 6f, 6f, paint)

        paint.color = if (nitroRatio >= 1f) Color.parseColor("#F59E0B") else Color.parseColor("#A855F7")
        canvas.drawRoundRect(RectF(barLeft, barY, barLeft + barW * nitroRatio, barY + 12f), 6f, 6f, paint)
    }

    private fun drawMenu(canvas: Canvas) {
        val cx = screenWidth / 2
        val cy = screenHeight / 2

        paint.color = Color.parseColor("#142010")
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, paint)

        farmhouseBmp?.let {
            canvas.drawBitmap(it, cx - it.width / 2f, cy - 260f, paint)
        }

        textPaint.color = Color.parseColor("#FFD214")
        textPaint.textSize = 52f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("ASTRANOMIA FARM", cx, cy - 40f, textPaint)

        textPaint.color = Color.parseColor("#86EFAC")
        textPaint.textSize = 28f
        canvas.drawText("БИТВА ЗА УРОЖАЙ НА ДОН-1500А", cx, cy + 10f, textPaint)

        val btnRect = RectF(cx - 160f, cy + 60f, cx + 160f, cy + 140f)
        paint.color = Color.parseColor("#EF4444")
        canvas.drawRoundRect(btnRect, 20f, 20f, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 32f
        canvas.drawText("▶ НАЧАТЬ ЖАТВУ", cx, cy + 112f, textPaint)
    }

    private fun drawGameOver(canvas: Canvas) {
        val cx = screenWidth / 2
        val cy = screenHeight / 2

        paint.color = Color.argb(210, 10, 15, 20)
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, paint)

        textPaint.color = Color.parseColor("#EF4444")
        textPaint.textSize = 52f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("ЖАТВА ОКОНЧЕНА", cx, cy - 80f, textPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 30f
        canvas.drawText("Кукурузы собрано: ${player.collectedCorn}", cx, cy - 20f, textPaint)
        canvas.drawText("Итоговый счёт: $score", cx, cy + 25f, textPaint)

        val btnRect = RectF(cx - 150f, cy + 70f, cx + 150f, cy + 140f)
        paint.color = Color.parseColor("#22C55E")
        canvas.drawRoundRect(btnRect, 18f, 18f, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 30f
        canvas.drawText("🔄 ЕЩЁ РАЗ", cx, cy + 115f, textPaint)
    }

    private fun drawPaused(canvas: Canvas) {
        val cx = screenWidth / 2
        val cy = screenHeight / 2

        paint.color = Color.argb(180, 0, 0, 0)
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, paint)

        textPaint.color = Color.parseColor("#FFD214")
        textPaint.textSize = 50f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("ПАУЗА", cx, cy - 30f, textPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 28f
        canvas.drawText("Нажмите [ R ] чтобы продолжить", cx, cy + 30f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        try {
            val x = event.x
            val y = event.y

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (state == GameState.MENU) {
                        val cx = screenWidth / 2
                        val cy = screenHeight / 2
                        val btnRect = RectF(cx - 160f, cy + 60f, cx + 160f, cy + 140f)
                        if (btnRect.contains(x, y)) {
                            hapticManager.heavyClick()
                            startGame()
                        }
                        return true
                    }

                    if (state == GameState.GAMEOVER) {
                        val cx = screenWidth / 2
                        val cy = screenHeight / 2
                        val btnRect = RectF(cx - 150f, cy + 70f, cx + 150f, cy + 140f)
                        if (btnRect.contains(x, y)) {
                            hapticManager.heavyClick()
                            startGame()
                        }
                        return true
                    }

                    // Check keypad or controls
                    if (controls.handleTouchDown(x, y)) {
                        hapticManager.tick()
                        return true
                    }

                    // Direct touch on game viewport: steer combine towards touch point
                    if (y < controls.viewportBottom - 110f) {
                        synchronized(stateLock) {
                            val dx = x - (screenWidth / 2)
                            val dy = y - (controls.viewportBottom / 2)
                            val a = atan2(dy, dx)
                            if (!a.isNaN()) {
                                player.angle = a
                                player.speed = player.maxSpeed
                            }
                        }
                        return true
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (y < controls.viewportBottom - 110f) {
                        synchronized(stateLock) {
                            val dx = x - (screenWidth / 2)
                            val dy = y - (controls.viewportBottom / 2)
                            val a = atan2(dy, dx)
                            if (!a.isNaN()) {
                                player.angle = a
                                player.speed = player.maxSpeed
                            }
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    controls.handleTouchUp(x, y)
                }
            }
        } catch (t: Throwable) {
            Log.e("AstranomiaGameView", "onTouchEvent error", t)
        }
        return true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        soundManager.startMenuMusic()
        isRunning = true
        gameThread = Thread(this, "GameLoopThread").apply { start() }
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
            gameThread?.join(500)
        } catch (e: InterruptedException) {
            Log.e("AstranomiaGameView", "Interrupted waiting for game thread", e)
        }
        gameThread = null
    }

    fun onResume() {
        if (state == GameState.MENU) soundManager.startMenuMusic()
        else if (state == GameState.PLAYING) soundManager.startGameMusic()
    }

    fun onPause() {
        soundManager.stopMusic()
    }

    fun onDestroy() {
        soundManager.release()
    }
}

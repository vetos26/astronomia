package com.astranomia.game.model

enum class CropType { PUMPKIN, POTATO, WATERMELON, CARROT }

data class Crop(
    var x: Float,
    var y: Float,
    val type: CropType,
    var isStolen: Boolean = false
)

data class CornTile(
    val col: Int,
    val row: Int,
    var isHarvested: Boolean = false
)

data class CornParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float = 0f,
    var vRot: Float = 0f,
    var life: Int = 35
)

data class Combine(
    var x: Float,
    var y: Float,
    var angle: Float = (-Math.PI / 2).toFloat(),
    var speed: Float = 0f,
    var maxSpeed: Float = 4.8f,
    var hp: Float = 100f,
    var maxHp: Float = 100f,
    var ammo: Int = 6,
    val maxAmmo: Int = 6,
    var isReloading: Boolean = false,
    var reloadProgress: Float = 0f,
    var superCharge: Float = 0f, // 0 to 100%
    var isNitro: Boolean = false,
    var nitroTime: Int = 0,
    var reelAngle: Float = 0f,
    var collectedCorn: Int = 0
) {
    fun reset(startX: Float, startY: Float) {
        x = startX
        y = startY
        angle = (-Math.PI / 2).toFloat()
        speed = 0f
        hp = 100f
        ammo = 6
        isReloading = false
        superCharge = 0f
        isNitro = false
        nitroTime = 0
        collectedCorn = 0
    }
}

data class Alien(
    var x: Float,
    var y: Float,
    var speed: Float = 2.4f,
    var carriedCrop: Crop? = null,
    var targetCrop: Crop? = null,
    var wobble: Float = 0f
)

data class FlyingSaucer(
    var x: Float,
    var y: Float,
    var hp: Float = 60f,
    val maxHp: Float = 60f,
    var speed: Float = 1.9f,
    var wobble: Float = 0f,
    var targetCrop: Crop? = null,
    var beamActive: Boolean = false,
    var beamTimer: Int = 0,
    var beamLift: Float = 0f
)

data class Bullet(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    var life: Int = 50,
    val damage: Float = 25f
)

data class Particle(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val color: Int,
    val size: Float,
    var life: Int,
    val maxLife: Int
)

data class FloatingText(
    var x: Float,
    var y: Float,
    val text: String,
    val color: Int,
    val size: Float,
    var life: Int = 40
)

/**
 * ASTRANOMIA - 2D Cartoon Game Engine
 * Combat Combine Harvester «ДОН-1500А» vs Alien UFOs
 */

(function () {
  'use strict';

  // Canvas & Context
  const canvas = document.getElementById('game-canvas');
  const ctx = canvas.getContext('2d');

  // HUD Elements
  const hudScore = document.getElementById('hud-score');
  const hpBarFill = document.getElementById('hp-bar-fill');
  const hpText = document.getElementById('hp-text');
  const cropPercent = document.getElementById('crop-percent');
  const cropBarFill = document.getElementById('crop-bar-fill');
  const ammoBadge = document.getElementById('ammo-badge');
  const superChargeLabel = document.getElementById('super-charge-label');
  const btnSuperWeapon = document.getElementById('btn-super-weapon');

  // Modals
  const menuModal = document.getElementById('menu-modal');
  const pauseModal = document.getElementById('pause-modal');
  const gameoverModal = document.getElementById('gameover-modal');
  const startGameBtn = document.getElementById('start-game-btn');
  const resumeGameBtn = document.getElementById('resume-game-btn');
  const restartGameBtn = document.getElementById('restart-game-btn');
  const retryGameBtn = document.getElementById('retry-game-btn');
  const hudMenuBtn = document.getElementById('hud-menu-btn');
  const toggleSoundBtn = document.getElementById('toggle-sound-btn');

  // Stats elements
  const statScore = document.getElementById('stat-score');
  const statAliens = document.getElementById('stat-aliens');
  const statUfos = document.getElementById('stat-ufos');
  const gameoverTitle = document.getElementById('gameover-title');
  const gameoverDesc = document.getElementById('gameover-desc');

  // Virtual Controls buttons
  const btnFire = document.getElementById('btn-fire');
  const btnReload = document.getElementById('btn-reload');
  const dpadUp = document.getElementById('btn-dpad-up');
  const dpadDown = document.getElementById('btn-dpad-down');
  const dpadLeft = document.getElementById('btn-dpad-left');
  const dpadRight = document.getElementById('btn-dpad-right');

  // World dimensions
  const WORLD_WIDTH = 1920;
  const WORLD_HEIGHT = 1080;

  function resizeCanvas() {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
  }
  window.addEventListener('resize', resizeCanvas);
  resizeCanvas();

  // ==========================================
  // GAME STATE
  // ==========================================
  let gameState = 'menu'; // 'menu', 'playing', 'paused', 'gameover'
  let score = 0;
  let aliensCrushed = 0;
  let ufosDestroyed = 0;
  let cropHealth = 100;
  let screenShake = 0;
  let lastTime = 0;

  // Camera coordinates (follows combine)
  const camera = { x: 0, y: 0 };

  // ==========================================
  // INPUT STATE
  // ==========================================
  const input = {
    up: false,
    down: false,
    left: false,
    right: false,
    fire: false,
    reload: false,
    drift: false,
    mouseX: 0,
    mouseY: 0
  };

  // Keyboard listeners
  window.addEventListener('keydown', (e) => {
    if (e.repeat) return;
    switch (e.code) {
      case 'KeyW': case 'ArrowUp': input.up = true; break;
      case 'KeyS': case 'ArrowDown': input.down = true; break;
      case 'KeyA': case 'ArrowLeft': input.left = true; break;
      case 'KeyD': case 'ArrowRight': input.right = true; break;
      case 'Space': input.fire = true; break;
      case 'KeyR': handleReload(); break;
      case 'ShiftLeft': case 'ShiftRight': case 'KeyE': triggerSuperWeapon(); break;
      case 'Escape': togglePause(); break;
    }
  });

  window.addEventListener('keyup', (e) => {
    switch (e.code) {
      case 'KeyW': case 'ArrowUp': input.up = false; break;
      case 'KeyS': case 'ArrowDown': input.down = false; break;
      case 'KeyA': case 'ArrowLeft': input.left = false; break;
      case 'KeyD': case 'ArrowRight': input.right = false; break;
      case 'Space': input.fire = false; break;
    }
  });

  window.addEventListener('mousemove', (e) => {
    input.mouseX = e.clientX;
    input.mouseY = e.clientY;
  });

  window.addEventListener('mousedown', (e) => {
    if (gameState === 'playing' && e.target === canvas) {
      input.fire = true;
    }
  });

  window.addEventListener('mouseup', () => {
    input.fire = false;
  });

  // Touch handlers for D-Pad
  function setupTouchButton(elem, onDown, onUp) {
    elem.addEventListener('touchstart', (e) => {
      e.preventDefault();
      elem.classList.add('active');
      onDown();
    }, { passive: false });

    elem.addEventListener('touchend', (e) => {
      e.preventDefault();
      elem.classList.remove('active');
      onUp();
    }, { passive: false });

    elem.addEventListener('mousedown', (e) => {
      e.preventDefault();
      elem.classList.add('active');
      onDown();
    });

    elem.addEventListener('mouseup', (e) => {
      e.preventDefault();
      elem.classList.remove('active');
      onUp();
    });
  }

  setupTouchButton(dpadUp, () => { input.up = true; }, () => { input.up = false; });
  setupTouchButton(dpadDown, () => { input.down = true; }, () => { input.down = false; });
  setupTouchButton(dpadLeft, () => { input.left = true; }, () => { input.left = false; });
  setupTouchButton(dpadRight, () => { input.right = true; }, () => { input.right = false; });

  setupTouchButton(btnFire, () => { input.fire = true; }, () => { input.fire = false; });
  setupTouchButton(btnReload, () => { handleReload(); }, () => {});
  setupTouchButton(btnSuperWeapon, () => { triggerSuperWeapon(); }, () => {});

  // ==========================================
  // COMBINE HARVESTER «ДОН-1500А» (PLAYER)
  // ==========================================
  const player = {
    x: WORLD_WIDTH / 2,
    y: WORLD_HEIGHT / 2,
    angle: 0,
    speed: 0,
    maxSpeed: 4.5,
    accel: 0.15,
    friction: 0.94,
    steerAngle: 0,
    maxSteer: 0.045,
    width: 90,
    height: 55,
    hp: 100,
    maxHp: 100,
    ammo: 6,
    maxAmmo: 6,
    isReloading: false,
    reloadTime: 0,
    fireCooldown: 0,
    superCharge: 0, // 0 to 100%
    isNitro: false,
    nitroTime: 0,
    reelAngle: 0,

    reset() {
      this.x = WORLD_WIDTH / 2;
      this.y = WORLD_HEIGHT / 2;
      this.angle = 0;
      this.speed = 0;
      this.hp = 100;
      this.ammo = 6;
      this.isReloading = false;
      this.superCharge = 0;
      this.isNitro = false;
    }
  };

  // ==========================================
  // WEAPONS & PROJECTILES
  // ==========================================
  const bullets = [];

  function fireWeapon() {
    if (player.ammo <= 0) {
      handleReload();
      return;
    }
    if (player.fireCooldown > 0 || player.isReloading) return;

    player.ammo--;
    player.fireCooldown = 15; // frames
    window.soundEngine.playShotgunBlast();

    // Spawn 2 potato/buckshot projectiles from side pipe and roof turret
    const noseX = player.x + Math.cos(player.angle) * 35;
    const noseY = player.y + Math.sin(player.angle) * 35;

    // Target nearest UFO or aim straight
    let targetAngle = player.angle;
    let closestUfo = null;
    let closestDist = 700;

    ufos.forEach(ufo => {
      const d = Math.hypot(ufo.x - player.x, ufo.y - player.y);
      if (d < closestDist) {
        closestDist = d;
        closestUfo = ufo;
      }
    });

    if (closestUfo) {
      targetAngle = Math.atan2(closestUfo.y - noseY, closestUfo.x - noseX);
    }

    // Spread of 2 shots
    for (let i = -1; i <= 1; i += 2) {
      const spread = targetAngle + i * 0.08;
      bullets.push({
        x: noseX,
        y: noseY,
        vx: Math.cos(spread) * 12,
        vy: Math.sin(spread) * 12,
        radius: 8,
        life: 60,
        damage: 25
      });
    }

    // Muzzle flash particle
    for (let p = 0; p < 8; p++) {
      particles.push({
        x: noseX,
        y: noseY,
        vx: (Math.random() - 0.5) * 6,
        vy: (Math.random() - 0.5) * 6,
        color: '#f97316',
        size: Math.random() * 6 + 3,
        life: 15,
        maxLife: 15
      });
    }
  }

  function handleReload() {
    if (player.isReloading || player.ammo === player.maxAmmo) return;
    player.isReloading = true;
    player.reloadTime = 60; // 1 second
    window.soundEngine.playReload();
  }

  function triggerSuperWeapon() {
    if (player.superCharge < 100 || player.isNitro) return;
    player.isNitro = true;
    player.nitroTime = 480; // 8 seconds of Coffin Dance Nitro Overdrive
    player.superCharge = 0;
    screenShake = 18;
    window.soundEngine.isNitro = true;
    window.soundEngine.playNitroActivation();

    // Floating text
    addFloatingText(player.x, player.y - 40, '⚡ НИТРО-АСТРОНОМИЯ! ⚡', '#ffcc00', 32);
  }

  // ==========================================
  // ENEMIES: UFOS & GROUND ALIENS
  // ==========================================
  const ufos = [];
  const aliens = [];
  const crops = [];
  const particles = [];
  const floatingTexts = [];

  // Generate Farm Crops on the field
  function generateCrops() {
    crops.length = 0;
    const types = ['pumpkin', 'potato', 'watermelon', 'carrot'];
    for (let i = 0; i < 70; i++) {
      crops.push({
        x: Math.random() * (WORLD_WIDTH - 200) + 100,
        y: Math.random() * (WORLD_HEIGHT - 200) + 100,
        type: types[i % types.length],
        isStolen: false,
        size: 16
      });
    }
  }

  // Spawn UFO
  function spawnUFO() {
    const side = Math.floor(Math.random() * 4);
    let x = 0, y = 0;
    if (side === 0) { x = Math.random() * WORLD_WIDTH; y = -50; }
    else if (side === 1) { x = WORLD_WIDTH + 50; y = Math.random() * WORLD_HEIGHT; }
    else if (side === 2) { x = Math.random() * WORLD_WIDTH; y = WORLD_HEIGHT + 50; }
    else { x = -50; y = Math.random() * WORLD_HEIGHT; }

    ufos.push({
      x,
      y,
      hp: 60,
      maxHp: 60,
      speed: 1.8,
      wobble: Math.random() * Math.PI * 2,
      targetCrop: null,
      beamActive: false,
      beamTimer: 0,
      beamLift: 0
    });
  }

  // Spawn Ground Alien Minion
  function spawnAlien() {
    const side = Math.floor(Math.random() * 4);
    let x = 0, y = 0;
    if (side === 0) { x = Math.random() * WORLD_WIDTH; y = 0; }
    else if (side === 1) { x = WORLD_WIDTH; y = Math.random() * WORLD_HEIGHT; }
    else if (side === 2) { x = Math.random() * WORLD_WIDTH; y = WORLD_HEIGHT; }
    else { x = 0; y = Math.random() * WORLD_HEIGHT; }

    aliens.push({
      x,
      y,
      speed: 2.2 + Math.random() * 0.8,
      carriedCrop: null,
      targetCrop: null,
      wobble: Math.random() * Math.PI * 2
    });
  }

  function addFloatingText(x, y, text, color = '#ffcc00', size = 18) {
    floatingTexts.push({ x, y, text, color, size, life: 45, maxLife: 45 });
  }

  // ==========================================
  // UPDATE LOGIC
  // ==========================================
  function update(delta) {
    if (gameState !== 'playing') return;

    // Screen Shake decay
    if (screenShake > 0) screenShake = Math.max(0, screenShake - 0.8);

    // 1. COMBINE MOVEMENT & PHYSICS
    if (player.isNitro) {
      player.nitroTime--;
      if (player.nitroTime <= 0) {
        player.isNitro = false;
        window.soundEngine.isNitro = false;
      }
    }

    const currentMaxSpeed = player.isNitro ? player.maxSpeed * 2.2 : player.maxSpeed;

    // Acceleration & Reverse
    if (input.up) {
      player.speed = Math.min(currentMaxSpeed, player.speed + player.accel);
    } else if (input.down) {
      player.speed = Math.max(-currentMaxSpeed * 0.5, player.speed - player.accel);
    } else {
      player.speed *= player.friction;
    }

    // Steering
    if (input.left) {
      player.angle -= player.maxSteer * (player.speed >= 0 ? 1 : -1);
    }
    if (input.right) {
      player.angle += player.maxSteer * (player.speed >= 0 ? 1 : -1);
    }

    // Position update
    player.x += Math.cos(player.angle) * player.speed;
    player.y += Math.sin(player.angle) * player.speed;

    // Boundaries clamp
    player.x = Math.max(60, Math.min(WORLD_WIDTH - 60, player.x));
    player.y = Math.max(60, Math.min(WORLD_HEIGHT - 60, player.y));

    // Reel spinning animation
    if (Math.abs(player.speed) > 0.1) {
      player.reelAngle += player.speed * 0.08;
    }

    // Weapons cooldown & reload
    if (player.fireCooldown > 0) player.fireCooldown--;
    if (player.isReloading) {
      player.reloadTime--;
      if (player.reloadTime <= 0) {
        player.isReloading = false;
        player.ammo = player.maxAmmo;
      }
    }

    if (input.fire) {
      fireWeapon();
    }

    // Camera follow combine
    camera.x = player.x - canvas.width / 2;
    camera.y = player.y - canvas.height / 2;

    // 2. BULLETS UPDATE
    for (let i = bullets.length - 1; i >= 0; i--) {
      const b = bullets[i];
      b.x += b.vx;
      b.y += b.vy;
      b.life--;

      // Check collision with UFOs
      let hit = false;
      for (let u = ufos.length - 1; u >= 0; u--) {
        const ufo = ufos[u];
        const dist = Math.hypot(b.x - ufo.x, b.y - ufo.y);
        if (dist < 45) {
          ufo.hp -= b.damage;
          hit = true;
          screenShake = 4;

          // Spark particles
          for (let p = 0; p < 6; p++) {
            particles.push({
              x: b.x, y: b.y,
              vx: (Math.random() - 0.5) * 5,
              vy: (Math.random() - 0.5) * 5,
              color: '#38bdf8',
              size: 4, life: 18, maxLife: 18
            });
          }

          // UFO destroyed
          if (ufo.hp <= 0) {
            window.soundEngine.playSaucerCrash();
            screenShake = 14;
            ufosDestroyed++;
            score += 500;
            addFloatingText(ufo.x, ufo.y, '+500 ТАРЕЛКА СБИТА!', '#38bdf8', 22);

            // Explosion particles
            for (let p = 0; p < 24; p++) {
              particles.push({
                x: ufo.x, y: ufo.y,
                vx: (Math.random() - 0.5) * 10,
                vy: (Math.random() - 0.5) * 10,
                color: (p % 2 === 0) ? '#f97316' : '#cbd5e1',
                size: Math.random() * 8 + 4,
                life: 35, maxLife: 35
              });
            }

            // Release stolen crop
            if (ufo.targetCrop) {
              ufo.targetCrop.isStolen = false;
            }
            ufos.splice(u, 1);
          }
          break;
        }
      }

      if (hit || b.life <= 0) {
        bullets.splice(i, 1);
      }
    }

    // 3. UFOS UPDATE
    if (ufos.length < 4 && Math.random() < 0.015) {
      spawnUFO();
    }

    ufos.forEach(ufo => {
      ufo.wobble += 0.05;

      // Find nearest unstolen crop
      if (!ufo.targetCrop || ufo.targetCrop.isStolen) {
        let bestCrop = null;
        let minDist = 9999;
        crops.forEach(c => {
          if (!c.isStolen) {
            const d = Math.hypot(c.x - ufo.x, c.y - ufo.y);
            if (d < minDist) {
              minDist = d;
              bestCrop = c;
            }
          }
        });
        ufo.targetCrop = bestCrop;
      }

      // Move towards target crop
      if (ufo.targetCrop) {
        const dx = ufo.targetCrop.x - ufo.x;
        const dy = ufo.targetCrop.y - ufo.y;
        const dist = Math.hypot(dx, dy);

        if (dist > 30) {
          ufo.x += (dx / dist) * ufo.speed;
          ufo.y += (dy / dist) * ufo.speed;
          ufo.beamActive = false;
        } else {
          // Hover and activate tractor beam
          ufo.beamActive = true;
          ufo.beamTimer++;
          ufo.beamLift = Math.min(1.0, ufo.beamTimer / 180); // 3 sec lift

          if (ufo.beamTimer >= 180) {
            // Crop abducted!
            ufo.targetCrop.isStolen = true;
            cropHealth = Math.max(0, cropHealth - 5);
            addFloatingText(ufo.x, ufo.y - 20, 'ОВОЩ ПОХИЩЕН! -5% 🌾', '#ef4444', 16);
            ufo.targetCrop = null;
            ufo.beamActive = false;
            ufo.beamTimer = 0;
            ufo.beamLift = 0;

            if (cropHealth <= 0) {
              endGame(false, 'Урожай полностью похищен!');
            }
          }
        }
      }
    });

    // 4. GROUND ALIENS UPDATE & COMBINE HEADER COLLISION
    if (aliens.length < 8 && Math.random() < 0.02) {
      spawnAlien();
    }

    // Combine front header center position for grinding
    const headerX = player.x + Math.cos(player.angle) * 38;
    const headerY = player.y + Math.sin(player.angle) * 38;
    const headerRadius = 40;

    for (let i = aliens.length - 1; i >= 0; i--) {
      const alien = aliens[i];
      alien.wobble += 0.08;

      // Alien movement towards crops or UFO
      if (!alien.carriedCrop) {
        // Find nearest crop to dig up
        if (!alien.targetCrop || alien.targetCrop.isStolen) {
          let nearest = null;
          let nd = 9999;
          crops.forEach(c => {
            if (!c.isStolen) {
              const d = Math.hypot(c.x - alien.x, c.y - alien.y);
              if (d < nd) { nd = d; nearest = c; }
            }
          });
          alien.targetCrop = nearest;
        }

        if (alien.targetCrop) {
          const dx = alien.targetCrop.x - alien.x;
          const dy = alien.targetCrop.y - alien.y;
          const d = Math.hypot(dx, dy);
          if (d > 10) {
            alien.x += (dx / d) * alien.speed;
            alien.y += (dy / d) * alien.speed;
          } else {
            // Pick up crop
            alien.carriedCrop = alien.targetCrop;
            alien.targetCrop.isStolen = true;
          }
        }
      } else {
        // Carry to nearest UFO or edge
        let destX = WORLD_WIDTH / 2;
        let destY = 0;
        if (ufos.length > 0) {
          destX = ufos[0].x;
          destY = ufos[0].y;
        }
        const dx = destX - alien.x;
        const dy = destY - alien.y;
        const d = Math.hypot(dx, dy);
        if (d > 20) {
          alien.x += (dx / d) * (alien.speed * 0.8);
          alien.y += (dy / d) * (alien.speed * 0.8);
        }
      }

      // Check collision with COMBINE HEADER / WHEELS (MOWING & CRUSHING!)
      const distToHeader = Math.hypot(alien.x - headerX, alien.y - headerY);
      const distToBody = Math.hypot(alien.x - player.x, alien.y - player.y);

      if (distToHeader < headerRadius || (distToBody < 45 && Math.abs(player.speed) > 0.8)) {
        // CRUSHED BY DON-1500!
        window.soundEngine.playAlienSplat();
        aliensCrushed++;
        score += 100;
        player.superCharge = Math.min(100, player.superCharge + 5);

        // Green splatter particles
        for (let p = 0; p < 12; p++) {
          particles.push({
            x: alien.x, y: alien.y,
            vx: (Math.random() - 0.5) * 8,
            vy: (Math.random() - 0.5) * 8,
            color: '#22c55e',
            size: Math.random() * 6 + 3,
            life: 25, maxLife: 25
          });
        }

        // If Nitro: Coffin flying up!
        if (player.isNitro) {
          addFloatingText(alien.x, alien.y, '⚰️ COFFIN COMBO! +250', '#ffcc00', 20);
          score += 150;
        } else {
          addFloatingText(alien.x, alien.y, '+100 ПЕРЕМОЛОТ!', '#22c55e', 16);
        }

        // Release crop if carrying
        if (alien.carriedCrop) {
          alien.carriedCrop.isStolen = false;
        }

        aliens.splice(i, 1);
      }
    }

    // 5. UPDATE PARTICLES & TEXTS
    for (let i = particles.length - 1; i >= 0; i--) {
      const p = particles[i];
      p.x += p.vx;
      p.y += p.vy;
      p.life--;
      if (p.life <= 0) particles.splice(i, 1);
    }

    for (let i = floatingTexts.length - 1; i >= 0; i--) {
      const ft = floatingTexts[i];
      ft.y -= 0.8;
      ft.life--;
      if (ft.life <= 0) floatingTexts.splice(i, 1);
    }

    // 6. UPDATE HUD
    hudScore.textContent = `🏆 ОЧКИ: ${score}`;
    hpBarFill.style.width = `${player.hp}%`;
    hpText.textContent = `${Math.ceil(player.hp)}% HP`;
    cropPercent.textContent = Math.ceil(cropHealth);
    cropBarFill.style.width = `${cropHealth}%`;

    if (player.isReloading) {
      ammoBadge.textContent = '🔄 ПЕРЕЗАРЯДКА...';
      ammoBadge.style.color = '#f59e0b';
    } else {
      ammoBadge.textContent = `🥔 ЗАРЯД: ${player.ammo} / ${player.maxAmmo}`;
      ammoBadge.style.color = '#38bdf8';
    }

    superChargeLabel.textContent = `${Math.floor(player.superCharge)}%`;
    btnSuperWeapon.classList.toggle('ready', player.superCharge >= 100);
  }

  // ==========================================
  // 2D CARTOON RENDERING
  // ==========================================
  function render() {
    ctx.save();

    // Screen Shake
    if (screenShake > 0) {
      ctx.translate((Math.random() - 0.5) * screenShake * 2, (Math.random() - 0.5) * screenShake * 2);
    }

    // Clear Screen
    ctx.fillStyle = '#1b2614'; // Dark night farmland green
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // Apply Camera Translation
    ctx.save();
    ctx.translate(-camera.x, -camera.y);

    // 1. Draw Field Furrows & Ground Grid
    ctx.strokeStyle = '#25351c';
    ctx.lineWidth = 3;
    for (let y = 0; y < WORLD_HEIGHT; y += 40) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(WORLD_WIDTH, y);
      ctx.stroke();
    }

    // 2. Draw Farm Crops (2D Cartoon Pumpkins, Potatoes, Watermelons)
    crops.forEach(crop => {
      if (!crop.isStolen) {
        drawCartoonCrop(crop.x, crop.y, crop.type);
      }
    });

    // 3. Draw Splatter & Skid Marks
    particles.forEach(p => {
      ctx.fillStyle = p.color;
      ctx.globalAlpha = p.life / p.maxLife;
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
      ctx.fill();
    });
    ctx.globalAlpha = 1.0;

    // 4. Draw Ground Aliens
    aliens.forEach(alien => {
      drawCartoonAlien(alien.x, alien.y, alien.wobble, alien.carriedCrop);
    });

    // 5. Draw Combine Harvester «ДОН-1500А»
    drawCartoonDon1500(player.x, player.y, player.angle, player.reelAngle, player.isNitro);

    // 6. Draw Bullets / Potato cannonballs
    bullets.forEach(b => {
      ctx.fillStyle = '#f59e0b';
      ctx.beginPath();
      ctx.arc(b.x, b.y, b.radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = '#fff';
      ctx.lineWidth = 2;
      ctx.stroke();
    });

    // 7. Draw Flying Saucers (UFOs) & Tractor Beams
    ufos.forEach(ufo => {
      drawCartoonUFO(ufo.x, ufo.y, ufo.wobble, ufo.hp, ufo.maxHp, ufo.beamActive, ufo.beamLift, ufo.targetCrop);
    });

    // 8. Draw Floating Texts
    floatingTexts.forEach(ft => {
      ctx.font = `bold ${ft.size}px "Russo One", sans-serif`;
      ctx.fillStyle = ft.color;
      ctx.strokeStyle = '#000';
      ctx.lineWidth = 4;
      ctx.strokeText(ft.text, ft.x - 30, ft.y);
      ctx.fillText(ft.text, ft.x - 30, ft.y);
    });

    ctx.restore(); // Restore Camera
    ctx.restore(); // Restore Shake
  }

  // Draw 2D Cartoon Crops
  function drawCartoonCrop(x, y, type) {
    ctx.save();
    ctx.translate(x, y);

    if (type === 'pumpkin') {
      // Plump Cartoon Pumpkin
      ctx.fillStyle = '#ea580c';
      ctx.beginPath();
      ctx.ellipse(0, 0, 16, 13, 0, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = '#9a3412';
      ctx.lineWidth = 2;
      ctx.stroke();
      // Green stem
      ctx.fillStyle = '#22c55e';
      ctx.fillRect(-2, -18, 4, 6);
    } else if (type === 'watermelon') {
      // Striped Watermelon
      ctx.fillStyle = '#15803d';
      ctx.beginPath();
      ctx.ellipse(0, 0, 15, 12, 0.2, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = '#166534';
      ctx.lineWidth = 3;
      ctx.stroke();
    } else {
      // Cute Potato / Carrot
      ctx.fillStyle = '#a16207';
      ctx.beginPath();
      ctx.ellipse(0, 0, 12, 10, 0.1, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#000';
      ctx.beginPath();
      ctx.arc(-3, -2, 2, 0, Math.PI * 2);
      ctx.arc(3, -2, 2, 0, Math.PI * 2);
      ctx.fill();
    }

    ctx.restore();
  }

  // Draw 2D Cartoon Alien Minion
  function drawCartoonAlien(x, y, wobble, carriedCrop) {
    ctx.save();
    ctx.translate(x, y);

    const bob = Math.sin(wobble * 2) * 3;

    // Shadow
    ctx.fillStyle = 'rgba(0,0,0,0.3)';
    ctx.beginPath();
    ctx.ellipse(0, 12, 12, 5, 0, 0, Math.PI * 2);
    ctx.fill();

    // Body
    ctx.fillStyle = '#22c55e';
    ctx.beginPath();
    ctx.ellipse(0, bob, 11, 14, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = '#14532d';
    ctx.lineWidth = 2;
    ctx.stroke();

    // Big Googly Eyes
    ctx.fillStyle = '#fff';
    ctx.beginPath();
    ctx.arc(-5, bob - 4, 5, 0, Math.PI * 2);
    ctx.arc(5, bob - 4, 5, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#000';
    ctx.beginPath();
    ctx.arc(-5 + Math.sin(wobble) * 2, bob - 4, 2.5, 0, Math.PI * 2);
    ctx.arc(5 + Math.sin(wobble) * 2, bob - 4, 2.5, 0, Math.PI * 2);
    ctx.fill();

    // Antenna with golden ball
    ctx.strokeStyle = '#15803d';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(0, bob - 14);
    ctx.lineTo(Math.sin(wobble) * 4, bob - 22);
    ctx.stroke();
    ctx.fillStyle = '#eab308';
    ctx.beginPath();
    ctx.arc(Math.sin(wobble) * 4, bob - 23, 3.5, 0, Math.PI * 2);
    ctx.fill();

    // Carried crop above head
    if (carriedCrop) {
      drawCartoonCrop(0, bob - 28, carriedCrop.type);
    }

    ctx.restore();
  }

  // Draw 2D Cartoon Red Combine Harvester «ДОН-1500А»
  function drawCartoonDon1500(x, y, angle, reelAngle, isNitro) {
    ctx.save();
    ctx.translate(x, y);
    ctx.rotate(angle);

    // Nitro flames from exhausts
    if (isNitro || Math.abs(player.speed) > 3.5) {
      ctx.fillStyle = isNitro ? '#a855f7' : '#ef4444';
      ctx.beginPath();
      ctx.moveTo(-45, -12);
      ctx.lineTo(-75 - Math.random() * 25, -12);
      ctx.lineTo(-45, -6);
      ctx.fill();

      ctx.fillStyle = isNitro ? '#38bdf8' : '#f59e0b';
      ctx.beginPath();
      ctx.moveTo(-45, 6);
      ctx.lineTo(-75 - Math.random() * 25, 6);
      ctx.lineTo(-45, 12);
      ctx.fill();
    }

    // 4 Dancing Farmers Coffin Dance Holograms during Nitro
    if (isNitro) {
      ctx.fillStyle = 'rgba(56, 189, 248, 0.85)';
      ctx.font = 'bold 12px "Russo One", sans-serif';
      ctx.fillText('⚰️ COFFIN DANCE OVERDRIVE ⚰️', -90, -45);

      // 4 dancing silhouettes on the combine platforms
      const bob = Math.sin(Date.now() * 0.015) * 4;
      for (let i = -1; i <= 2; i++) {
        ctx.fillStyle = '#0f172a';
        ctx.fillRect(-20 + i * 16, -34 + (i % 2 === 0 ? bob : -bob), 8, 12);
        ctx.fillStyle = '#fed7aa';
        ctx.beginPath();
        ctx.arc(-16 + i * 16, -37 + (i % 2 === 0 ? bob : -bob), 3.5, 0, Math.PI * 2);
        ctx.fill();
        // Cool shades
        ctx.fillStyle = '#000';
        ctx.fillRect(-18 + i * 16, -38 + (i % 2 === 0 ? bob : -bob), 4, 2);
      }
    }

    // Shadow
    ctx.fillStyle = 'rgba(0,0,0,0.35)';
    ctx.beginPath();
    ctx.roundRect(-42, -24, 88, 48, 10);
    ctx.fill();

    // Giant Front Tractor Wheels (Chunky treads)
    ctx.fillStyle = '#0f172a';
    ctx.fillRect(10, -32, 26, 12);
    ctx.fillRect(10, 20, 26, 12);
    ctx.fillStyle = '#eab308'; // Yellow rim
    ctx.fillRect(16, -30, 14, 8);
    ctx.fillRect(16, 22, 14, 8);

    // Rear Steering Wheels
    ctx.fillStyle = '#0f172a';
    ctx.fillRect(-35, -28, 18, 9);
    ctx.fillRect(-35, 19, 18, 9);

    // Main Body: Authentic Terracotta-Red Soviet Steel
    ctx.fillStyle = isNitro ? '#fbbf24' : '#c2410c'; // Golden glow in Nitro
    ctx.beginPath();
    ctx.roundRect(-42, -22, 80, 44, [6, 12, 12, 6]);
    ctx.fill();
    ctx.strokeStyle = '#7c2d12';
    ctx.lineWidth = 3;
    ctx.stroke();

    // White Stencil Lettering: «ДОН 1500А»
    ctx.fillStyle = '#ffffff';
    ctx.font = 'bold 9px "Russo One", sans-serif';
    ctx.fillText('ДОН 1500А', -32, 14);

    // Serial Stamp: 1991 г.
    ctx.font = 'bold 7px sans-serif';
    ctx.fillStyle = 'rgba(255,255,255,0.7)';
    ctx.fillText('1991 г.', -32, -12);

    // Side Unloading Auger Tube (Выгрузной шнек-мортира)
    ctx.fillStyle = '#78350f';
    ctx.fillRect(-36, -26, 68, 6);
    ctx.strokeStyle = '#451a03';
    ctx.lineWidth = 1.5;
    ctx.strokeRect(-36, -26, 68, 6);

    // Rear Chopper with White & Red Warning Stripes
    ctx.fillStyle = '#b91c1c';
    ctx.fillRect(-45, -16, 7, 32);
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(-44, -12, 5, 4);
    ctx.fillRect(-44, -2, 5, 4);
    ctx.fillRect(-44, 8, 5, 4);

    // Cabin: Panoramic Glass with White Flat Roof
    ctx.fillStyle = 'rgba(56, 189, 248, 0.85)';
    ctx.fillRect(6, -16, 26, 32);
    ctx.strokeStyle = '#0284c7';
    ctx.lineWidth = 2;
    ctx.strokeRect(6, -16, 26, 32);

    // White Flat Cabin Roof
    ctx.fillStyle = '#f8fafc';
    ctx.fillRect(8, -17, 24, 34);

    // Animated Дед Макар inside cabin in Ushanka with Red Star!
    ctx.fillStyle = '#78350f'; // Brown fur ushanka
    ctx.beginPath();
    ctx.arc(20, 0, 7, 0, Math.PI * 2);
    ctx.fill();
    // Ushanka earflaps
    ctx.fillRect(14, -8, 12, 3);
    ctx.fillRect(14, 5, 12, 3);
    // Soviet Red Star on hat!
    ctx.fillStyle = '#ef4444';
    ctx.beginPath();
    ctx.arc(23, 0, 2.5, 0, Math.PI * 2);
    ctx.fill();

    // Front Active Header (Жатка-мясорубка)
    ctx.save();
    ctx.translate(44, 0);

    // Header Frame
    ctx.fillStyle = '#475569';
    ctx.fillRect(-4, -24, 8, 48);
    ctx.strokeStyle = '#eab308';
    ctx.lineWidth = 2;
    ctx.strokeRect(-4, -24, 8, 48);

    // Rotating Reel & Sharp Saw Teeth
    const reelCos = Math.cos(reelAngle);
    ctx.fillStyle = '#cbd5e1';
    for (let t = -20; t <= 20; t += 8) {
      ctx.beginPath();
      ctx.moveTo(4, t);
      ctx.lineTo(12 + reelCos * 3, t - 3);
      ctx.lineTo(12 + reelCos * 3, t + 3);
      ctx.fill();
    }
    ctx.restore();

    ctx.restore();
  }

  // Draw 2D Cartoon UFO with Tractor Beam
  function drawCartoonUFO(x, y, wobble, hp, maxHp, beamActive, beamLift, targetCrop) {
    ctx.save();
    ctx.translate(x, y + Math.sin(wobble) * 6);

    // Tractor Beam downwards
    if (beamActive && targetCrop) {
      const beamGrad = ctx.createLinearGradient(0, 0, 0, 160);
      beamGrad.addColorStop(0, 'rgba(168, 85, 247, 0.8)');
      beamGrad.addColorStop(0.5, 'rgba(192, 132, 252, 0.5)');
      beamGrad.addColorStop(1, 'rgba(147, 51, 234, 0.1)');

      ctx.fillStyle = beamGrad;
      ctx.beginPath();
      ctx.moveTo(-15, 10);
      ctx.lineTo(15, 10);
      ctx.lineTo(55, 160);
      ctx.lineTo(-55, 160);
      ctx.closePath();
      ctx.fill();

      // Floating vegetable rising inside beam
      const liftY = 150 - beamLift * 130;
      drawCartoonCrop(0, liftY, targetCrop.type);
    }

    // Shadow on ground
    ctx.fillStyle = 'rgba(0,0,0,0.2)';
    ctx.beginPath();
    ctx.ellipse(0, 150, 40, 15, 0, 0, Math.PI * 2);
    ctx.fill();

    // Metallic Saucer Body
    ctx.fillStyle = '#64748b';
    ctx.beginPath();
    ctx.ellipse(0, 0, 48, 16, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = '#94a3b8';
    ctx.lineWidth = 3;
    ctx.stroke();

    // Cockpit Glass Dome
    ctx.fillStyle = 'rgba(56, 189, 248, 0.75)';
    ctx.beginPath();
    ctx.ellipse(0, -9, 22, 15, 0, Math.PI, 0);
    ctx.fill();
    ctx.strokeStyle = '#38bdf8';
    ctx.lineWidth = 2;
    ctx.stroke();

    // Cute Alien Pilot inside cockpit!
    ctx.fillStyle = '#22c55e';
    ctx.beginPath();
    ctx.arc(0, -11, 7, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#000';
    ctx.beginPath();
    ctx.arc(-2.5, -12, 1.8, 0, Math.PI * 2);
    ctx.arc(2.5, -12, 1.8, 0, Math.PI * 2);
    ctx.fill();

    // Blinking Perimeter Lights
    for (let i = 0; i < 6; i++) {
      const la = wobble * 2 + (i * Math.PI * 2) / 6;
      const lx = Math.cos(la) * 40;
      const ly = Math.sin(la) * 9;
      ctx.fillStyle = (i % 2 === 0) ? '#38bdf8' : '#a855f7';
      ctx.beginPath();
      ctx.arc(lx, ly, 3, 0, Math.PI * 2);
      ctx.fill();
    }

    // Health Bar above UFO
    ctx.fillStyle = 'rgba(0,0,0,0.6)';
    ctx.fillRect(-25, -28, 50, 6);
    ctx.fillStyle = (hp / maxHp > 0.4) ? '#22c55e' : '#ef4444';
    ctx.fillRect(-25, -28, 50 * (hp / maxHp), 6);

    ctx.restore();
  }

  // ==========================================
  // GAME LOOP & MODAL CONTROLS
  // ==========================================
  function gameLoop(timestamp) {
    const delta = (timestamp - lastTime) / 1000;
    lastTime = timestamp;

    update(delta);
    render();

    requestAnimationFrame(gameLoop);
  }

  function startGame() {
    window.soundEngine.init();
    window.soundEngine.startGameMusic();
    score = 0;
    aliensCrushed = 0;
    ufosDestroyed = 0;
    cropHealth = 100;
    player.reset();
    generateCrops();
    ufos.length = 0;
    aliens.length = 0;
    bullets.length = 0;
    particles.length = 0;
    floatingTexts.length = 0;

    // Initial spawns
    for (let i = 0; i < 2; i++) spawnUFO();
    for (let i = 0; i < 4; i++) spawnAlien();

    menuModal.classList.add('hidden');
    pauseModal.classList.add('hidden');
    gameoverModal.classList.add('hidden');
    gameState = 'playing';
  }

  function togglePause() {
    if (gameState === 'playing') {
      gameState = 'paused';
      pauseModal.classList.remove('hidden');
    } else if (gameState === 'paused') {
      gameState = 'playing';
      pauseModal.classList.add('hidden');
    }
  }

  function endGame(isVictory, message) {
    gameState = 'gameover';
    window.soundEngine.stopMusic();
    gameoverTitle.textContent = isVictory ? '🏆 УРОЖАЙ СПАСЁН!' : '💀 ЖАТВА ОКОНЧЕНА';
    gameoverDesc.textContent = message || 'Инопланетная осада завершилась.';
    statScore.textContent = score;
    statAliens.textContent = aliensCrushed;
    statUfos.textContent = ufosDestroyed;
    gameoverModal.classList.remove('hidden');
  }

  // Button clicks
  startGameBtn.addEventListener('click', startGame);
  resumeGameBtn.addEventListener('click', togglePause);
  restartGameBtn.addEventListener('click', startGame);
  retryGameBtn.addEventListener('click', startGame);
  hudMenuBtn.addEventListener('click', togglePause);

  toggleSoundBtn.addEventListener('click', () => {
    window.soundEngine.init();
    const enabled = window.soundEngine.toggleSound();
    toggleSoundBtn.innerHTML = enabled ? '<span>🔊</span> ЗВУК: ВКЛЮЧЕН' : '<span>🔇</span> ЗВУК: ВЫКЛЮЧЕН';
  });

  // Start in menu state and play menu theme on first interaction
  window.addEventListener('click', () => {
    if (gameState === 'menu' && !window.soundEngine.ctx) {
      window.soundEngine.init();
      window.soundEngine.startMenuMusic();
    }
  }, { once: true });

  // Start Animation Loop
  requestAnimationFrame(gameLoop);

})();

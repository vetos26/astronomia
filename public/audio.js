/**
 * ASTRANOMIA - Web Audio API Synthesizer & Phonk Engine
 * - Main Menu: Authentic "Astronomia" (Vicetone & Tony Igy) Synth
 * - In-Game: "Agro-Phonk" (Drift Phonk with 808 Sub-bass & Cowbells)
 * - SFX: Combine engine, cutter shredder, alien splat, cannon blast, explosion
 */

class SoundEngine {
  constructor() {
    this.ctx = null;
    this.masterGain = null;
    this.musicGain = null;
    this.sfxGain = null;
    this.enabled = true;
    this.currentTrack = null; // 'menu' or 'game' or null
    this.loopTimer = null;
    this.engineOsc = null;
    this.engineGain = null;
    this.isDrifting = false;
    this.isNitro = false;
  }

  init() {
    if (this.ctx) return;
    const AudioContext = window.AudioContext || window.webkitAudioContext;
    this.ctx = new AudioContext();

    this.masterGain = this.ctx.createGain();
    this.masterGain.gain.setValueAtTime(this.enabled ? 0.9 : 0.0, this.ctx.currentTime);
    this.masterGain.connect(this.ctx.destination);

    this.musicGain = this.ctx.createGain();
    this.musicGain.gain.setValueAtTime(0.7, this.ctx.currentTime);
    this.musicGain.connect(this.masterGain);

    this.sfxGain = this.ctx.createGain();
    this.sfxGain.gain.setValueAtTime(0.85, this.ctx.currentTime);
    this.sfxGain.connect(this.masterGain);

    if (this.ctx.state === 'suspended') {
      this.ctx.resume();
    }
  }

  toggleSound() {
    this.enabled = !this.enabled;
    if (this.masterGain && this.ctx) {
      this.masterGain.gain.setValueAtTime(this.enabled ? 0.9 : 0.0, this.ctx.currentTime);
    }
    return this.enabled;
  }

  // NOTE FREQUENCIES
  static NOTES = {
    C3: 130.81, D3: 146.83, Eb3: 155.56, F3: 174.61, G3: 196.00, A3: 220.00, Bb3: 233.08,
    C4: 261.63, D4: 293.66, Eb4: 311.13, F4: 349.23, G4: 392.00, A4: 440.00, Bb4: 466.16,
    C5: 523.25, D5: 587.33, Eb5: 622.25, F5: 698.46, G5: 783.99, A5: 880.00, Bb5: 932.33
  };

  // Tone synth helper
  playTone(freq, time, duration, type = 'sawtooth', volume = 0.25, filterFreq = 2200) {
    if (!this.ctx || !this.enabled) return;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    const filter = this.ctx.createBiquadFilter();

    osc.type = type;
    osc.frequency.setValueAtTime(freq, time);

    filter.type = 'lowpass';
    filter.frequency.setValueAtTime(filterFreq, time);
    filter.Q.setValueAtTime(2.5, time);

    gain.gain.setValueAtTime(0.001, time);
    gain.gain.exponentialRampToValueAtTime(volume, time + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.001, time + duration);

    osc.connect(filter);
    filter.connect(gain);
    gain.connect(this.musicGain);

    osc.start(time);
    osc.stop(time + duration);
  }

  // 808 Phonk Cowbell (Dual square oscillators bandpassed)
  playCowbell(freq, time, volume = 0.35) {
    if (!this.ctx || !this.enabled) return;
    const osc1 = this.ctx.createOscillator();
    const osc2 = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    const filter = this.ctx.createBiquadFilter();

    osc1.type = 'square';
    osc2.type = 'square';
    osc1.frequency.setValueAtTime(freq, time);
    osc2.frequency.setValueAtTime(freq * 1.48, time);

    filter.type = 'bandpass';
    filter.frequency.setValueAtTime(freq * 1.25, time);
    filter.Q.setValueAtTime(7.5, time);

    const actualVol = (this.isDrifting || this.isNitro) ? volume * 1.4 : volume;
    gain.gain.setValueAtTime(actualVol, time);
    gain.gain.exponentialRampToValueAtTime(0.0001, time + 0.35);

    osc1.connect(filter);
    osc2.connect(filter);
    filter.connect(gain);
    gain.connect(this.musicGain);

    osc1.start(time);
    osc2.start(time);
    osc1.stop(time + 0.38);
    osc2.stop(time + 0.38);
  }

  // 808 Sub-Bass Kick Slide
  play808Sub(startFreq, endFreq, time, duration = 0.45, volume = 0.5) {
    if (!this.ctx || !this.enabled) return;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(startFreq, time);
    osc.frequency.exponentialRampToValueAtTime(endFreq, time + duration);

    const actualVol = (this.isDrifting || this.isNitro) ? volume * 1.5 : volume;
    gain.gain.setValueAtTime(actualVol, time);
    gain.gain.exponentialRampToValueAtTime(0.001, time + duration);

    osc.connect(gain);
    gain.connect(this.musicGain);

    osc.start(time);
    osc.stop(time + duration);
  }

  // Snappy Phonk Snare / Clap
  playSnare(time, volume = 0.25) {
    if (!this.ctx || !this.enabled) return;
    const bufferSize = this.ctx.sampleRate * 0.15;
    const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) {
      data[i] = (Math.random() * 2 - 1) * Math.exp(-i / (bufferSize * 0.22));
    }
    const noise = this.ctx.createBufferSource();
    noise.buffer = buffer;

    const filter = this.ctx.createBiquadFilter();
    filter.type = 'bandpass';
    filter.frequency.setValueAtTime(2200, time);
    filter.Q.setValueAtTime(2.0, time);

    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(volume, time);
    gain.gain.exponentialRampToValueAtTime(0.001, time + 0.15);

    noise.connect(filter);
    filter.connect(gain);
    gain.connect(this.musicGain);

    noise.start(time);
  }

  // ----------------------------------------------------
  // MUSIC TRACKS
  // ----------------------------------------------------

  startMenuMusic() {
    this.stopMusic();
    this.currentTrack = 'menu';
    this.playAstronomiaLoop();
  }

  startGameMusic() {
    this.stopMusic();
    this.currentTrack = 'game';
    this.playAgroPhonkLoop();
  }

  stopMusic() {
    this.currentTrack = null;
    if (this.loopTimer) {
      clearTimeout(this.loopTimer);
      this.loopTimer = null;
    }
  }

  // ASTRONOMIA MENU THEME (Vicetone & Tony Igy Synth)
  playAstronomiaLoop() {
    if (this.currentTrack !== 'menu' || !this.ctx) return;
    const bpm = 130;
    const sixteenth = (60 / bpm) / 4; // ~0.115s
    const N = SoundEngine.NOTES;
    const now = this.ctx.currentTime + 0.05;

    // Iconic lead notes
    const melody = [
      N.G4, N.G4, N.G4, N.G4, N.D5, N.D5, N.D5, N.D5,
      N.C5, N.C5, N.C5, N.C5, N.Bb4, N.Bb4, N.A4, N.G4,
      N.Bb4, N.A4, N.Bb4, N.C5, N.A4, N.F4, N.G4, N.G4
    ];

    let t = now;
    melody.forEach((pitch, i) => {
      const dur = (i === melody.length - 1 || i === melody.length - 2) ? sixteenth * 2 : sixteenth;
      this.playTone(pitch, t, dur * 0.9, 'sawtooth', 0.22, 2400);
      // Soft bass accompaniment
      if (i % 4 === 0) {
        this.playTone(N.G3, t, sixteenth * 3.5, 'sine', 0.3, 400);
      }
      t += dur;
    });

    const loopLengthMs = (t - now) * 1000 + 100;
    this.loopTimer = setTimeout(() => {
      if (this.currentTrack === 'menu') this.playAstronomiaLoop();
    }, loopLengthMs);
  }

  // AGRO DRIFT PHONK IN-GAME LOOP (808 Sub-Bass, Cowbells, Snares)
  playAgroPhonkLoop() {
    if (this.currentTrack !== 'game' || !this.ctx) return;
    const bpm = 136;
    const beat = 60 / bpm; // ~0.441s
    const sixteenth = beat / 4;
    const N = SoundEngine.NOTES;
    const now = this.ctx.currentTime + 0.05;

    // 8-beat Phonk Bar pattern
    // Distorted cowbell hook
    const cowbellRiff = [
      { n: N.D5, t: 0 },
      { n: N.D5, t: sixteenth * 2 },
      { n: N.F5, t: sixteenth * 4 },
      { n: N.D5, t: sixteenth * 6 },
      { n: N.G5, t: sixteenth * 8 },
      { n: N.F5, t: sixteenth * 10 },
      { n: N.D5, t: sixteenth * 12 },
      { n: N.C5, t: sixteenth * 14 },

      { n: N.D5, t: beat * 4 },
      { n: N.D5, t: beat * 4 + sixteenth * 2 },
      { n: N.F5, t: beat * 4 + sixteenth * 4 },
      { n: N.A5, t: beat * 4 + sixteenth * 6 },
      { n: N.G5, t: beat * 4 + sixteenth * 8 },
      { n: N.F5, t: beat * 4 + sixteenth * 10 },
      { n: N.D5, t: beat * 4 + sixteenth * 12 },
      { n: N.Bb4, t: beat * 4 + sixteenth * 14 }
    ];

    cowbellRiff.forEach((step) => {
      this.playCowbell(step.n, now + step.t, 0.32);
    });

    // 808 Sub-Bass Kicks & Snares
    for (let b = 0; b < 8; b++) {
      const beatTime = now + b * beat;
      // Kick on 0, 2, 4, 6
      if (b === 0 || b === 2 || b === 4 || b === 6) {
        this.play808Sub(120, 38, beatTime, beat * 0.9, 0.55);
      }
      // Snare on 1, 3, 5, 7
      if (b % 2 === 1) {
        this.playSnare(beatTime, 0.35);
      }
    }

    const loopLengthMs = (beat * 8) * 1000;
    this.loopTimer = setTimeout(() => {
      if (this.currentTrack === 'game') this.playAgroPhonkLoop();
    }, loopLengthMs);
  }

  // ----------------------------------------------------
  // SOUND EFFECTS (SFX)
  // ----------------------------------------------------

  // Alien Splat / Shred in combine cutter
  playAlienSplat() {
    if (!this.ctx || !this.enabled) return;
    const now = this.ctx.currentTime;
    // Low squish sine
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.type = 'triangle';
    osc.frequency.setValueAtTime(260, now);
    osc.frequency.exponentialRampToValueAtTime(55, now + 0.12);

    gain.gain.setValueAtTime(0.4, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.14);

    osc.connect(gain);
    gain.connect(this.sfxGain);
    osc.start(now);
    osc.stop(now + 0.15);

    // Crackle noise
    const bufferSize = this.ctx.sampleRate * 0.08;
    const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) {
      data[i] = (Math.random() * 2 - 1) * Math.exp(-i / (bufferSize * 0.2));
    }
    const noise = this.ctx.createBufferSource();
    noise.buffer = buffer;
    noise.connect(this.sfxGain);
    noise.start(now);
  }

  // Shotgun / Potato Cannon Blast
  playShotgunBlast() {
    if (!this.ctx || !this.enabled) return;
    const now = this.ctx.currentTime;
    this.play808Sub(160, 40, now, 0.28, 0.6);

    const bufferSize = this.ctx.sampleRate * 0.2;
    const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) {
      data[i] = (Math.random() * 2 - 1) * Math.exp(-i / (bufferSize * 0.3));
    }
    const noise = this.ctx.createBufferSource();
    noise.buffer = buffer;

    const filter = this.ctx.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.setValueAtTime(1200, now);

    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(0.5, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.2);

    noise.connect(filter);
    filter.connect(gain);
    gain.connect(this.sfxGain);

    noise.start(now);
  }

  // Flying Saucer Crash Explosion
  playSaucerCrash() {
    if (!this.ctx || !this.enabled) return;
    const now = this.ctx.currentTime;
    this.play808Sub(150, 24, now, 0.8, 0.8);

    const bufferSize = this.ctx.sampleRate * 0.6;
    const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) {
      data[i] = (Math.random() * 2 - 1) * Math.exp(-i / (bufferSize * 0.35));
    }
    const noise = this.ctx.createBufferSource();
    noise.buffer = buffer;

    const filter = this.ctx.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.setValueAtTime(900, now);
    filter.frequency.exponentialRampToValueAtTime(100, now + 0.6);

    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(0.7, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.6);

    noise.connect(filter);
    filter.connect(gain);
    gain.connect(this.sfxGain);

    noise.start(now);
  }

  // Reload click-clack
  playReload() {
    if (!this.ctx || !this.enabled) return;
    const now = this.ctx.currentTime;
    this.playTone(600, now, 0.05, 'square', 0.25, 1800);
    this.playTone(850, now + 0.12, 0.06, 'square', 0.25, 2200);
  }

  // Super Weapon / Nitro Coffin Overdrive Stinger
  playNitroActivation() {
    if (!this.ctx || !this.enabled) return;
    const now = this.ctx.currentTime;
    const N = SoundEngine.NOTES;
    // Rapid Astronomia Stinger
    const stinger = [N.G4, N.D5, N.C5, N.Bb4, N.A4, N.G4];
    stinger.forEach((p, idx) => {
      this.playTone(p, now + idx * 0.08, 0.15, 'sawtooth', 0.35, 3000);
    });
    // Massive bass drop
    this.play808Sub(180, 30, now + 0.48, 1.0, 0.9);
  }
}

window.soundEngine = new SoundEngine();

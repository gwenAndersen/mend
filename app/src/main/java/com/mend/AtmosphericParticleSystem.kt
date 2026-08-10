package com.mend

import java.util.Random
import kotlin.math.*

class AtmosphericParticleSystem(private val width: Int, private val height: Int) {
    private val rng = Random()
    val particleCount = 600
    
    val px = FloatArray(particleCount)
    val py = FloatArray(particleCount)
    private val ax = FloatArray(particleCount)
    private val ay = FloatArray(particleCount)
    val palpha = FloatArray(particleCount)
    private val pflicker = FloatArray(particleCount)
    private val pbrightening = BooleanArray(particleCount)
    
    // New states for Dark Wave
    val isDark = BooleanArray(particleCount)
    val isGlowing = BooleanArray(particleCount)
    private val lastGlowTime = LongArray(particleCount)

    // New states for Cosmic Wave
    val cosmicEnergy = FloatArray(particleCount)
    val cosmicRefractory = IntArray(particleCount)
    
    // Influence grids
    private val cellW = 128f
    private val cellShift = 7
    private val cols = ceil(width / cellW).toInt()
    private val rows = ceil(height / cellW).toInt()
    private val excitationGrid = Array(cols) { FloatArray(rows) }
    private val darknessGrid = Array(cols) { FloatArray(rows) }
    private val gridBias = Array(cols) { FloatArray(rows) }

    // Ripple state
    private var ripplePhase = 0 // 0: Idle, 1: Charging, 2: Expanding
    private var rippleX = 0f
    private var rippleY = 0f
    private var rippleRadius = 0f
    private var rippleSpeed = 0f
    private var chargeTimer = 0
    private val rippleMaxRadiusSq = (width * width + height * height) * 1.1f
    private var prevRippleRadiusSq = 0f

    // Config (could be synced with prefs)
    var isDarkWaveEnabled = true
    var isCosmicWaveEnabled = false
    var isRippleEnabled = true

    init {
        // Initialize grid bias for emergent behavior
        for (i in 0 until 5) {
            val cx = rng.nextInt(cols)
            val cy = rng.nextInt(rows)
            val strength = 0.3f + rng.nextFloat() * 0.5f
            val radius = 2 + rng.nextInt(3)
            val radiusSq = radius * radius
            for (x in max(0, cx - radius)..min(cols - 1, cx + radius)) {
                for (y in max(0, cy - radius)..min(rows - 1, cy + radius)) {
                    val dx = (x - cx).toFloat()
                    val dy = (y - cy).toFloat()
                    val d2 = dx * dx + dy * dy
                    if (d2 < radiusSq) {
                        val dist = sqrt(d2)
                        gridBias[x][y] += strength * (1.0f - dist / radius)
                    }
                }
            }
        }

        // Initialize particles
        for (i in 0 until particleCount) {
            val targetX: Float
            val targetY: Float
            if (rng.nextFloat() < 0.75f) {
                val progress = rng.nextFloat()
                val centerX = progress * width
                val centerY = progress * height
                val spread = width * 0.25f
                targetX = centerX + (rng.nextGaussian().toFloat() * spread)
                targetY = centerY + (rng.nextGaussian().toFloat() * spread)
            } else {
                targetX = rng.nextFloat() * width
                targetY = rng.nextFloat() * height
            }
            
            val finalX = max(0f, min(width.toFloat(), targetX))
            val finalY = max(0f, min(height.toFloat(), targetY))

            ax[i] = finalX
            px[i] = finalX
            ay[i] = finalY
            py[i] = finalY
            palpha[i] = rng.nextFloat()
            pflicker[i] = 0.002f + rng.nextFloat() * 0.015f
            pbrightening[i] = rng.nextBoolean()
            isDark[i] = false
            isGlowing[i] = false
        }
    }

    fun triggerRipple(x: Float, y: Float) {
        if (ripplePhase == 0) {
            rippleX = x
            rippleY = y
            rippleRadius = 0f
            prevRippleRadiusSq = 0f
            rippleSpeed = 12f + rng.nextFloat() * 8f
            chargeTimer = 45 
            ripplePhase = 1
        }
    }

    fun update() {
        // 1. Base Environment Decay
        updateBaseGrids()

        // 2. Modifier: Ripples (Dark Wave can also trigger ripples for atmosphere)
        if (isRippleEnabled || isDarkWaveEnabled) {
            updateRipples()
        }

        // 3. Apply Particle Physics & Styles
        val now = System.currentTimeMillis()
        for (i in 0 until particleCount) {
            applyBasePhysics(i)

            val gc = max(0, min(cols - 1, (px[i].toInt()) shr cellShift))
            val gr = max(0, min(rows - 1, (py[i].toInt()) shr cellShift))

            if (isCosmicWaveEnabled) {
                applyCosmicWaveStyle(i, gc, gr, now)
            } else if (isDarkWaveEnabled) {
                applyDarkWaveStyle(i, gc, gr, now)
            } else {
                // Ensure state is clean for base
                isDark[i] = false
                isGlowing[i] = false
                cosmicEnergy[i] = 0f
                applyBaseStyle(i, gc, gr)
            }
        }
    }

    private fun updateBaseGrids() {
        for (c in 0 until cols) {
            for (r in 0 until rows) {
                excitationGrid[c][r] = min(1.0f, excitationGrid[c][r] * 0.94f + gridBias[c][r] * 0.005f)
                darknessGrid[c][r] = min(1.0f, darknessGrid[c][r] * 0.985f)
            }
        }
    }

    private fun updateRipples() {
        val triggerProb = if (isDarkWaveEnabled) 0.0005f else 0.002f
        if (ripplePhase == 0 && rng.nextFloat() < triggerProb) {
            val targetIdx = rng.nextInt(particleCount)
            triggerRipple(px[targetIdx], py[targetIdx])
        }

        if (ripplePhase == 1) {
            updateRippleCharging()
        } else if (ripplePhase == 2) {
            updateRippleExpanding()
        }
    }

    private fun applyBasePhysics(i: Int) {
        px[i] += (ax[i] - px[i]) * 0.006f
        py[i] += (ay[i] - py[i]) * 0.006f
    }

    private fun updateRippleCharging() {
        val attractRadius = width * 0.4f
        val attractRadiusSq = attractRadius * attractRadius
        for (i in 0 until particleCount) {
            val dx = rippleX - px[i]
            val dy = rippleY - py[i]
            val d2 = dx * dx + dy * dy
            if (d2 < attractRadiusSq) {
                val dist = sqrt(d2)
                if (dist > 1f) {
                    val t = 1.0f - dist / attractRadius
                    val force = 0.025f * (t * t * t)
                    px[i] += dx * force
                    py[i] += dy * force
                    palpha[i] = min(1.0f, palpha[i] + 0.015f * t)
                }
            }
        }
        chargeTimer--
        if (chargeTimer <= 0) ripplePhase = 2
    }

    private fun updateRippleExpanding() {
        rippleRadius += rippleSpeed
        val currentRippleRadiusSq = rippleRadius * rippleRadius
        if (currentRippleRadiusSq > rippleMaxRadiusSq) {
            ripplePhase = 0
        } else {
            for (i in 0 until particleCount) {
                val dx = px[i] - rippleX
                val dy = py[i] - rippleY
                val d2 = dx * dx + dy * dy
                if (d2 <= currentRippleRadiusSq && d2 > prevRippleRadiusSq) {
                    palpha[i] = 1.0f
                    pbrightening[i] = false
                    pflicker[i] = 0.015f + rng.nextFloat() * 0.01f
                    
                    val dist = sqrt(d2)
                    if (dist > 0) {
                        val force = 25f + rng.nextFloat() * 35f
                        px[i] += (dx / dist) * force
                        py[i] += (dy / dist) * force
                    }

                    if (isDarkWaveEnabled) {
                        isDark[i] = true
                        val rgc = max(0, min(cols - 1, px[i].toInt() shr cellShift))
                        val rgr = max(0, min(rows - 1, py[i].toInt() shr cellShift))
                        darknessGrid[rgc][rgr] = 1.0f
                    }
                }
            }
            prevRippleRadiusSq = currentRippleRadiusSq
        }
    }

    private fun applyDarkWaveStyle(i: Int, gc: Int, gr: Int, now: Long) {
        if (isDark[i]) {
            darknessGrid[gc][gr] = min(1.0f, darknessGrid[gc][gr] + 0.04f)

            if (isGlowing[i]) {
                if (pbrightening[i]) {
                    palpha[i] += pflicker[i]
                    if (palpha[i] >= 1.0f) {
                        palpha[i] = 1.0f
                        pbrightening[i] = false
                        // Energize neighbors
                        for (dx in -1..1) {
                            for (dy in -1..1) {
                                val nxc = gc + dx
                                val nyc = gr + dy
                                if (nxc in 0 until cols && nyc in 0 until rows) {
                                    val strength = if (dx == 0 && dy == 0) 0.25f else 0.12f
                                    excitationGrid[nxc][nyc] = min(1.0f, excitationGrid[nxc][nyc] + strength)
                                }
                            }
                        }
                    }
                } else {
                    palpha[i] -= pflicker[i]
                    if (palpha[i] <= 0.1f) {
                        palpha[i] = 0.1f
                        isGlowing[i] = false
                        pbrightening[i] = true
                    }
                }
            } else {
                // No blinking for dark particles as requested, stay at dim fixed alpha
                palpha[i] = 0.2f

                if (now - lastGlowTime[i] > 5000) {
                    if (excitationGrid[gc][gr] > 0.25f && rng.nextFloat() < 0.22f) {
                        isGlowing[i] = true
                        pbrightening[i] = true
                        lastGlowTime[i] = now
                    } else if (rng.nextFloat() < 0.00015f) {
                        isGlowing[i] = true
                        pbrightening[i] = true
                        lastGlowTime[i] = now
                    }
                }
            }
        } else {
            // Can become dark
            if (darknessGrid[gc][gr] > 0.75f && rng.nextFloat() < 0.1f) {
                isDark[i] = true
            }
            applyBaseStyle(i, gc, gr)
        }
    }

    private fun applyCosmicWaveStyle(i: Int, gc: Int, gr: Int, now: Long) {
        // Fix float precision loss: System.currentTimeMillis() is too large for a 32-bit float!
        // We must take a modulo first to keep the number small enough to retain fractional precision.
        val t = (now % 1000000L) * 0.001f // Time in seconds

        
        // A sweeping "solar wind" front that travels down the screen (Sped up)
        val waveSpeed = 500f // pixels per second (moderate sweeping speed)
        val wavePeriod = height + 1200f
        val wavePos = (t * waveSpeed) % wavePeriod - 600f

        // Distance from the particle to the center of the aurora band
        val distToWave = abs(py[i] - wavePos)
        val waveWidth = 150f // 300px wide band! Very narrow so you clearly see it move!

        var targetAlpha = 0.15f // Baseline dimness for cosmic dust

        if (distToWave < waveWidth) {
            // Normalized intensity of the magnetic wave (1.0 at center, 0.0 at edges)
            val intensity = 1.0f - (distToWave / waveWidth)
            val smoothIntensity = intensity * intensity * (3 - 2 * intensity)

            // Aurora Vector Field Math
            // We use the particle's original anchor (ax, ay) to keep the magnetic field stable
            val sx = ax[i] * 0.003f
            val sy = ay[i] * 0.003f
            
            // Complex trigonometric interference pattern for beautiful, weaving ribbons (Sped up morphing)
            val flowAngle = sin(sx + t * 1.5f) + cos(sy - t * 1.2f) + sin((sx - sy) * 1.5f + t * 2.0f)
            
            // Calculate bright strands within the ribbon
            // High frequency sine applied over the flow field creates crisp, bright energy lines (Sped up shimmer)
            val strand = (sin(flowAngle * 5.0f + t * 6.0f) * 0.5f + 0.5f).toFloat()
            
            // Peak brightness highlights the strands near the center of the wave
            targetAlpha = 0.15f + (0.85f * smoothIntensity * strand)
        }

        // Smoothly approach the target alpha (faster response time)
        palpha[i] += (targetAlpha - palpha[i]) * 0.15f
    }

    private fun applyBaseStyle(i: Int, gc: Int, gr: Int) {
        if (!pbrightening[i] && palpha[i] < 0.25f && excitationGrid[gc][gr] > 0.2f) {
            if (rng.nextFloat() < excitationGrid[gc][gr] * 0.15f) {
                pbrightening[i] = true
                pflicker[i] = 0.012f + rng.nextFloat() * 0.01f
            }
        }

        if (pbrightening[i]) {
            palpha[i] += pflicker[i]
            if (palpha[i] >= 1.0f) {
                palpha[i] = 1.0f
                pbrightening[i] = false
                excitationGrid[gc][gr] = min(1.0f, excitationGrid[gc][gr] + 0.08f)
            }
        } else {
            palpha[i] -= pflicker[i]
            if (palpha[i] <= 0.1f) {
                palpha[i] = 0.1f
                pbrightening[i] = true
                pflicker[i] = 0.002f + rng.nextFloat() * 0.015f
            }
        }
    }
}

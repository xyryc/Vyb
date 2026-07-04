package com.example.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.MainActivity
import com.example.data.TrackEntity
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

// Data class to represent floating visualizer particles
private data class VisualizerParticle(
    val baseRadius: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val phaseOffset: Float,
    val initialAngle: Float
)

@Composable
fun FluidVisualizer(
    track: TrackEntity,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    // Continuous time variable that advances based on playing state
    var animTime by remember { mutableStateOf(0f) }
    
    // Slow continuous rotation for the album cover art
    var rotationAngle by remember { mutableStateOf(0f) }

    LaunchedEffect(isPlaying) {
        var lastTime = withFrameMillis { it }
        while (isActive) {
            val currentTime = withFrameMillis { it }
            val delta = (currentTime - lastTime) / 1000f
            
            // Advance time quickly when playing, slowly "breathing" when paused
            val speedFactor = if (isPlaying) 1.0f else 0.12f
            animTime += delta * speedFactor
            
            // Spin album art when playing
            if (isPlaying) {
                rotationAngle = (rotationAngle + delta * 20f) % 360f
            } else {
                // Slower deceleration back to standard
                rotationAngle = (rotationAngle + delta * 2f) % 360f
            }
            
            lastTime = currentTime
        }
    }

    // Initialize particles once
    val particles = remember {
        List(40) { index ->
            val angle = (index * (360f / 40)) * (PI.toFloat() / 180f)
            VisualizerParticle(
                baseRadius = 150f + (index % 4) * 45f,
                speed = (0.2f + (index % 3) * 0.15f) * (if (index % 2 == 0) 1f else -1f),
                size = 3f + (index % 5) * 2f,
                color = when (index % 3) {
                    0 -> Color(0xFF1DB954).copy(alpha = 0.7f) // Spotify Green
                    1 -> Color(0xFF00D2FF).copy(alpha = 0.6f) // Neon Blue
                    else -> Color(0xFFFF007F).copy(alpha = 0.5f) // Hot Pink
                },
                phaseOffset = (index * 13.5f) * (PI.toFloat() / 180f),
                initialAngle = angle
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("fluid_visualizer_container"),
        contentAlignment = Alignment.Center
    ) {
        // Drawing Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("fluid_visualizer_canvas")
        ) {
            val width = size.width
            val height = size.height
            val cx = width / 2f
            val cy = height / 2f
            
            // Calculate musical beat simulation at ~120 BPM
            val bps = 120f / 60f // 2 beats per second
            val beatVal = sin(animTime * 2f * PI.toFloat() * bps)
            val beatPower = maxOf(0f, beatVal) * maxOf(0f, beatVal) // sharp peaks
            
            // Reactive amplitudes for low, mid, high frequencies
            val baseAmp = if (isPlaying) 0.5f + 0.5f * beatPower else 0.2f
            val midAmp = if (isPlaying) 0.4f + 0.3f * sin(animTime * 4.5f) else 0.15f
            val trebleAmp = if (isPlaying) 0.3f + 0.2f * cos(animTime * 9f) else 0.1f

            // 1. Nebula Center Glow
            val glowRadius = 220.dp.toPx() * (1f + 0.1f * baseAmp)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0D1B2A).copy(alpha = 0.9f),
                        Color(0xFF1B263B).copy(alpha = 0.4f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = glowRadius
                )
            )

            // 2. Draw Overlapping Fluid Waves (Sine/Cosine modulation of circular radius)
            // Draw Wave 1 (Deep Bass - Purple)
            drawFluidWave(
                cx = cx, cy = cy,
                baseRadius = 140.dp.toPx(),
                waveAmp = 22.dp.toPx() * baseAmp,
                frequency = 6,
                timePhase = animTime * 2.2f,
                color = Color(0xFF8A2BE2).copy(alpha = 0.28f),
                outlineColor = Color(0xFFD8BFD8).copy(alpha = 0.4f)
            )

            // Draw Wave 2 (Melody - Cyan/Turquoise)
            drawFluidWave(
                cx = cx, cy = cy,
                baseRadius = 125.dp.toPx(),
                waveAmp = 18.dp.toPx() * midAmp,
                frequency = 8,
                timePhase = -animTime * 3.1f,
                color = Color(0xFF00F2FE).copy(alpha = 0.22f),
                outlineColor = Color(0xFF4FACFE).copy(alpha = 0.45f)
            )

            // Draw Wave 3 (Highs/Treble - Magenta)
            drawFluidWave(
                cx = cx, cy = cy,
                baseRadius = 110.dp.toPx(),
                waveAmp = 14.dp.toPx() * trebleAmp,
                frequency = 11,
                timePhase = animTime * 4.4f,
                color = Color(0xFFF35588).copy(alpha = 0.18f),
                outlineColor = Color(0xFFFF007F).copy(alpha = 0.5f)
            )

            // 3. Draw Floating & Orbiting Particles
            particles.forEach { particle ->
                val dynamicAngle = particle.initialAngle + animTime * particle.speed
                val wobble = sin(animTime * 3f + particle.phaseOffset) * 12f
                val dynamicRadius = particle.baseRadius.dp.toPx() * (1f + 0.12f * baseAmp) + wobble
                
                val px = cx + dynamicRadius * cos(dynamicAngle)
                val py = cy + dynamicRadius * sin(dynamicAngle)
                
                // Draw particle with glow
                drawCircle(
                    color = particle.color,
                    radius = particle.size.dp.toPx(),
                    center = Offset(px, py)
                )
                
                // Subtle outer halo for active play state
                if (isPlaying && particle.size > 4f) {
                    drawCircle(
                        color = particle.color.copy(alpha = 0.2f),
                        radius = particle.size.dp.toPx() * 2f,
                        center = Offset(px, py),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }

        // 4. Central Spinning Cover Art framed inside the pulsing waves
        Box(
            modifier = Modifier
                .size(175.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .rotate(rotationAngle)
                .testTag("fluid_visualizer_album_art"),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = track.coverUrl,
                contentDescription = "Spinning album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Elegant inner vinyl ring overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )
            
            // Sleek centerpiece vinyl dot
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
            )
        }
    }
}

// Extension to draw a gorgeous closed fluid wave loop on the Canvas
private fun DrawScope.drawFluidWave(
    cx: Float,
    cy: Float,
    baseRadius: Float,
    waveAmp: Float,
    frequency: Int,
    timePhase: Float,
    color: Color,
    outlineColor: Color
) {
    val path = Path()
    val steps = 180
    val angleStep = (2 * PI / steps).toFloat()

    for (i in 0..steps) {
        val angle = i * angleStep
        // Modulate radius with high frequency and dynamic phase shifts
        val radiusModulation = sin(angle * frequency + timePhase)
        val r = baseRadius + waveAmp * radiusModulation
        
        val x = cx + r * cos(angle)
        val y = cy + r * sin(angle)
        
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()

    // Fill fluid body with transparent neon glow
    drawPath(
        path = path,
        color = color
    )
    
    // Draw crisp animated outline
    drawPath(
        path = path,
        color = outlineColor,
        style = Stroke(width = 1.5.dp.toPx())
    )
}

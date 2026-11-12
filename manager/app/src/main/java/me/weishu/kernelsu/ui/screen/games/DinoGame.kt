package me.weishu.kernelsu.ui.screen.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.min
import kotlin.random.Random
import me.weishu.kernelsu.R

// All positional constants are fractions of canvas dimensions.
private const val DINO_X = 0.12f       // left edge of dino
private const val DINO_W = 0.07f       // width as fraction of canvas width
private const val DINO_H = 0.13f       // standing height as fraction of canvas height
private const val DINO_SLIDE_H = 0.06f // crouched height
private const val GROUND_Y = 0.78f     // y where ground surface starts
private const val CACTUS_W = 0.06f     // cactus width as fraction of canvas width
private const val BIRD_W = 0.09f       // flying obstacle width as fraction of canvas width
private const val BIRD_TOP = 0.55f     // wing top; high enough that jumping cannot clear it
private const val BIRD_BOTTOM = 0.71f  // wing bottom; low enough that crouching passes under
private const val BASE_SPEED = 0.35f   // canvas-widths per second at start
private const val MAX_SPEED = 0.80f    // upper speed cap
private const val GRAVITY = 2.8f       // canvas-height per second squared
private const val JUMP_VEL = 1.05f     // upward velocity on tap (canvas-height per second)
private const val MILESTONE = 100      // score between the buzzes that mark the run

data class DinoObstacle(
    val x: Float,       // left edge, fraction of canvas width
    val height: Float,  // fraction of canvas height; max 0.15 keeps it always clearable
)

data class DinoBird(
    val x: Float,           // left edge, fraction of canvas width
    val wingPhase: Float,   // 0..1 flapping cycle
)

data class DinoCloud(
    val x: Float,
    val y: Float,
    val scale: Float,
)

@Stable
class DinoGameState(
    initialHighScore: Int = 0,
    onHighScoreUpdated: (Int) -> Unit = {},
) : MiniGameStateBase {
    var dinoJump by mutableFloatStateOf(0f)
        private set
    var velocity by mutableFloatStateOf(0f)
        private set
    var obstacles by mutableStateOf<List<DinoObstacle>>(emptyList())
        private set
    var birds by mutableStateOf<List<DinoBird>>(emptyList())
        private set
    private val points = GameScore(initialHighScore, onHighScoreUpdated)
    override val haptics = GameHaptics()
    override val score get() = points.value
    override val highScore get() = points.high
    override var isGameOver by mutableStateOf(false)
        private set
    var isStarted by mutableStateOf(false)
        private set
    var legPhase by mutableIntStateOf(0)
        private set
    override var isPaused by mutableStateOf(false)
        private set
    var clouds by mutableStateOf<List<DinoCloud>>(emptyList())
        private set

    /** True while the down button is held; crouching only applies on the ground. */
    var isSliding by mutableStateOf(false)
        private set

    val isSlidingNow: Boolean get() = isSliding && dinoJump == 0f
    val dinoHeight: Float get() = if (isSlidingNow) DINO_SLIDE_H else DINO_H

    private var lastTickMs = 0L
    private var lastObstacleMs = 0L
    private var lastBirdMs = 0L
    private var lastCloudMs = 0L
    private var distanceTraveled = 0f
    private var elapsedSec = 0f

    fun tap() {
        if (isGameOver) return
        if (!isStarted) {
            isStarted = true
            GameAudio.start()
        }
        if (dinoJump == 0f) {
            isSliding = false
            velocity = JUMP_VEL
        }
    }

    fun startSlide() {
        if (isGameOver) return
        if (!isStarted) {
            isStarted = true
            GameAudio.start()
        }
        isSliding = true
    }

    fun stopSlide() {
        isSliding = false
    }

    fun tick(nowMs: Long) {
        if (!isStarted || isGameOver || isPaused) return
        val dt = if (lastTickMs == 0L) 0.016f else (nowMs - lastTickMs).coerceIn(1, 50) / 1000f
        lastTickMs = nowMs
        elapsedSec += dt

        val speed = min(BASE_SPEED + elapsedSec * 0.03f, MAX_SPEED)
        distanceTraveled += speed * dt
        val previous = points.value
        points.value = (distanceTraveled * 100).toInt()
        if (points.value / MILESTONE > previous / MILESTONE) haptics.reward()

        if (dinoJump == 0f) legPhase = ((nowMs / 150) % 2).toInt()

        // Jump physics: velocity is upward (positive), gravity reduces it.
        if (dinoJump > 0f || velocity > 0f) {
            velocity -= GRAVITY * dt
            dinoJump = (dinoJump + velocity * dt).coerceAtLeast(0f)
            if (dinoJump == 0f) velocity = 0f
        }

        if (lastObstacleMs == 0L) lastObstacleMs = nowMs
        val spawnGap = maxOf(1400L, (2800L - elapsedSec * 30).toLong())
        if (nowMs - lastObstacleMs >= spawnGap) {
            obstacles = obstacles + DinoObstacle(
                x = 1.02f,
                height = Random.nextFloat() * 0.05f + 0.10f, // 0.10..0.15 (always clearable)
            )
            lastObstacleMs = nowMs
        }

        val dinoLeft = DINO_X + DINO_W * 0.1f
        val dinoRight = DINO_X + DINO_W * 0.9f
        val dinoBottom = GROUND_Y - dinoJump - DINO_H * 0.1f
        val dinoTop = GROUND_Y - dinoJump - dinoHeight + dinoHeight * 0.1f
        var hit = false

        val moved = mutableListOf<DinoObstacle>()
        for (obs in obstacles) {
            val nx = obs.x - speed * dt
            if (nx + CACTUS_W <= 0f) continue
            if (dinoRight > nx && dinoLeft < nx + CACTUS_W && dinoBottom > GROUND_Y - obs.height) hit = true
            moved += obs.copy(x = nx)
        }
        obstacles = moved

        // Flying obstacles sit at head height: jump over is impossible, crouch under is required.
        birds = birds.mapNotNull { bird ->
            val nx = bird.x - speed * 0.8f * dt
            if (nx + BIRD_W <= 0f) {
                null
            } else {
                if (dinoRight > nx && dinoLeft < nx + BIRD_W &&
                    dinoBottom > BIRD_TOP && dinoTop < BIRD_BOTTOM
                ) {
                    hit = true
                }
                bird.copy(x = nx, wingPhase = (bird.wingPhase + dt * 2.5f) % 1f)
            }
        }
        if (lastBirdMs == 0L) lastBirdMs = nowMs
        val birdGap = maxOf(3200L, (5200L - elapsedSec * 25).toLong())
        val birdClear = obstacles.none { it.x > 0.55f }
        if (elapsedSec > 8f && birdClear && nowMs - lastBirdMs >= birdGap) {
            birds = birds + DinoBird(x = 1.05f, wingPhase = 0f)
            lastBirdMs = nowMs
        }

        if (hit) {
            points.commit()
            isGameOver = true
            GameAudio.gameOver()
        }

        if (lastCloudMs == 0L) lastCloudMs = nowMs
        clouds = clouds.mapNotNull { c ->
            val nx = c.x - speed * 0.15f * dt
            if (nx + c.scale * 0.22f < 0f) null else c.copy(x = nx)
        }
        if (nowMs - lastCloudMs >= 7000L) {
            clouds = clouds + DinoCloud(
                x = 1.08f,
                y = Random.nextFloat() * 0.20f + 0.04f,
                scale = Random.nextFloat() * 0.45f + 0.65f,
            )
            lastCloudMs = nowMs
        }
    }

    override fun restart() {
        dinoJump = 0f
        velocity = 0f
        obstacles = emptyList()
        birds = emptyList()
        points.reset()
        isGameOver = false
        isStarted = false
        isPaused = false
        isSliding = false
        lastTickMs = 0L
        lastObstacleMs = 0L
        lastBirdMs = 0L
        distanceTraveled = 0f
        elapsedSec = 0f
        legPhase = 0
        clouds = listOf(
            DinoCloud(0.25f, Random.nextFloat() * 0.16f + 0.04f, Random.nextFloat() * 0.4f + 0.7f),
            DinoCloud(0.68f, Random.nextFloat() * 0.16f + 0.04f, Random.nextFloat() * 0.4f + 0.7f),
        )
        lastCloudMs = 0L
    }

    override val isPlaying get() = isStarted && !isGameOver && !isPaused

    override fun pause() { if (isStarted && !isGameOver) isPaused = true }
    override fun resume() { isPaused = false; lastTickMs = 0L }
}

@Composable
fun DinoGame(state: DinoGameState) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                androidx.compose.runtime.withFrameMillis { state.tick(it) }
            }
        }
    }
    DinoCanvas(state = state, modifier = Modifier.fillMaxSize())
}

/** Jump and slide buttons, shown under the play field. */
@Composable
fun DinoControls(state: DinoGameState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameHoldButton(
            icon = Icons.Rounded.KeyboardArrowDown,
            contentDescription = stringResource(R.string.mini_game_slide),
            onPress = state::startSlide,
            onRelease = state::stopSlide,
        )
        GameControlButton(
            icon = Icons.Rounded.KeyboardArrowUp,
            contentDescription = stringResource(R.string.mini_game_jump),
            onClick = state::tap,
        )
    }
}

@Composable
private fun DinoCanvas(state: DinoGameState, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DinoBg)
            .pointerInput(Unit) { detectTapGestures(onPress = { state.tap() }) },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val gy = GROUND_Y * h

            drawRect(
                brush = Brush.verticalGradient(listOf(DinoSkyTop, DinoSkyHorizon), startY = 0f, endY = gy),
                topLeft = Offset.Zero,
                size = Size(w, gy),
            )
            drawRect(DinoGround, Offset(0f, gy), Size(w, h - gy))
            drawLine(DinoGroundLine, Offset(0f, gy), Offset(w, gy), strokeWidth = 2f)

            for (cloud in state.clouds) {
                val cx = cloud.x * w
                val cy = cloud.y * h
                val r = cloud.scale * w * 0.055f
                drawCircle(DinoCloudColor, r, Offset(cx, cy))
                drawCircle(DinoCloudColor, r * 0.72f, Offset(cx - r * 0.85f, cy + r * 0.15f))
                drawCircle(DinoCloudColor, r * 0.72f, Offset(cx + r * 0.85f, cy + r * 0.15f))
                drawCircle(DinoCloudColor, r * 0.60f, Offset(cx - r * 0.42f, cy - r * 0.22f))
                drawCircle(DinoCloudColor, r * 0.55f, Offset(cx + r * 0.55f, cy - r * 0.18f))
            }

            for (obs in state.obstacles) {
                val ox = obs.x * w
                val ow = CACTUS_W * w
                val oh = obs.height * h
                drawRect(DinoCactus, Offset(ox + ow * 0.35f, gy - oh), Size(ow * 0.30f, oh))
                drawRect(DinoCactus, Offset(ox, gy - oh * 0.62f), Size(ow * 0.37f, oh * 0.18f))
                drawRect(DinoCactus, Offset(ox + ow * 0.63f, gy - oh * 0.52f), Size(ow * 0.37f, oh * 0.18f))
            }

            for (bird in state.birds) {
                val bx = bird.x * w
                val bw = BIRD_W * w
                val top = BIRD_TOP * h
                val bottom = BIRD_BOTTOM * h
                val midY = (top + bottom) / 2f
                val flap = (kotlin.math.sin(bird.wingPhase * 2f * Math.PI.toFloat()) + 1f) / 2f
                val upperTip = top + (bottom - top) * 0.12f * flap
                val lowerTip = bottom - (bottom - top) * 0.12f * flap

                drawPath(
                    path = Path().apply {
                        moveTo(bx + bw * 0.48f, midY)
                        lineTo(bx + bw * 0.10f, upperTip)
                        lineTo(bx + bw * 0.82f, midY)
                        close()
                    },
                    color = DinoBirdWing,
                )
                drawPath(
                    path = Path().apply {
                        moveTo(bx + bw * 0.48f, midY)
                        lineTo(bx + bw * 0.10f, lowerTip)
                        lineTo(bx + bw * 0.82f, midY)
                        close()
                    },
                    color = DinoBirdWing,
                )
                drawRect(DinoBirdBody, Offset(bx + bw * 0.20f, midY - h * 0.012f), Size(bw * 0.55f, h * 0.024f))
                drawPath(
                    path = Path().apply {
                        moveTo(bx + bw * 0.75f, midY - h * 0.020f)
                        lineTo(bx + bw * 1.00f, midY)
                        lineTo(bx + bw * 0.75f, midY + h * 0.020f)
                        close()
                    },
                    color = DinoBirdBeak,
                )
                drawCircle(DinoBirdEye, h * 0.008f, Offset(bx + bw * 0.70f, midY - h * 0.010f))
            }

            val dx = DINO_X * w
            val dh = state.dinoHeight * h
            val dy = (GROUND_Y - state.dinoHeight) * h - state.dinoJump * h
            val dw = DINO_W * w
            val dinoBottom = dy + dh

            if (state.isSlidingNow) {
                // Crouched silhouette: long body, head forward, legs tucked.
                drawRect(DinoBody, Offset(dx, dy + dh * 0.30f), Size(dw * 1.05f, dh * 0.62f))
                drawRect(DinoBody, Offset(dx + dw * 0.55f, dy + dh * 0.05f), Size(dw * 0.50f, dh * 0.42f))
                drawRect(DinoBody, Offset(dx + dw * 0.05f, dy + dh * 0.78f), Size(dw * 0.16f, dh * 0.22f))
                drawRect(DinoBody, Offset(dx + dw * 0.34f, dy + dh * 0.78f), Size(dw * 0.16f, dh * 0.22f))
                drawCircle(DinoBg, dw * 0.105f, Offset(dx + dw * 0.88f, dy + dh * 0.15f))
                drawCircle(DinoEye, dw * 0.065f, Offset(dx + dw * 0.90f, dy + dh * 0.16f))
                return@Canvas
            }

            drawRect(DinoBody, Offset(dx, dy + dh * 0.33f), Size(dw * 0.16f, dh * 0.18f))
            drawRect(DinoBody, Offset(dx, dy + dh * 0.49f), Size(dw * 0.10f, dh * 0.14f))
            drawRect(DinoBody, Offset(dx + dw * 0.05f, dy + dh * 0.44f), Size(dw * 0.68f, dh * 0.35f))
            drawRect(DinoBody, Offset(dx + dw * 0.38f, dy + dh * 0.26f), Size(dw * 0.20f, dh * 0.22f))
            drawRect(DinoBody, Offset(dx + dw * 0.34f, dy), Size(dw * 0.66f, dh * 0.30f))
            drawRect(DinoBody, Offset(dx + dw * 0.60f, dy + dh * 0.28f), Size(dw * 0.40f, dh * 0.11f))
            drawRect(DinoBody, Offset(dx + dw * 0.65f, dy + dh * 0.37f), Size(dw * 0.27f, dh * 0.09f))
            drawCircle(DinoBg, dw * 0.105f, Offset(dx + dw * 0.81f, dy + dh * 0.10f))
            drawCircle(DinoEye, dw * 0.065f, Offset(dx + dw * 0.83f, dy + dh * 0.11f))
            drawRect(DinoBody, Offset(dx + dw * 0.51f, dy + dh * 0.57f), Size(dw * 0.15f, dh * 0.10f))
            drawRect(DinoBody, Offset(dx + dw * 0.60f, dy + dh * 0.65f), Size(dw * 0.12f, dh * 0.07f))

            val legW = dw * 0.19f
            val legH = dh * 0.27f
            val footW = dw * 0.27f
            val footH = dh * 0.10f
            val backLegX = dx + dw * 0.12f
            val frontLegX = dx + dw * 0.34f
            if (state.dinoJump > 0f) {
                drawRect(DinoBody, Offset(backLegX, dinoBottom - legH), Size(legW, legH - footH))
                drawRect(DinoBody, Offset(backLegX, dinoBottom - footH), Size(footW, footH))
                drawRect(DinoBody, Offset(frontLegX, dinoBottom - legH * 0.82f), Size(legW, legH * 0.82f - footH))
                drawRect(DinoBody, Offset(frontLegX, dinoBottom - footH), Size(footW, footH))
            } else {
                val backRaise = if (state.legPhase == 1) dh * 0.12f else 0f
                val frontRaise = if (state.legPhase == 0) dh * 0.12f else 0f
                drawRect(DinoBody, Offset(backLegX, dinoBottom - legH - backRaise), Size(legW, legH - footH))
                drawRect(DinoBody, Offset(backLegX, dinoBottom - footH - backRaise), Size(footW, footH))
                drawRect(DinoBody, Offset(frontLegX, dinoBottom - legH - frontRaise), Size(legW, legH - footH))
                drawRect(DinoBody, Offset(frontLegX, dinoBottom - footH - frontRaise), Size(footW, footH))
            }
        }

        if (!state.isStarted && !state.isGameOver) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.mini_game_dino_tap_to_start),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DinoBg.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DinoBody.copy(alpha = 0.22f))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private val DinoBg = Color(0xFFF5F0E8)
private val DinoGround = Color(0xFFEADA90)
private val DinoGroundLine = Color(0xFFC8B060)
private val DinoCactus = Color(0xFF3E7D40)
private val DinoBody = Color(0xFF424242)
private val DinoEye = Color(0xFF212121)
private val DinoSkyTop = Color(0xFF5DA0CF)
private val DinoSkyHorizon = Color(0xFFB4D6EC)
private val DinoCloudColor = Color(0xFFFFFFFF)
private val DinoBirdWing = Color(0xFF4A7A99)
private val DinoBirdBody = Color(0xFF33566B)
private val DinoBirdBeak = Color(0xFFE0A000)
private val DinoBirdEye = Color(0xFF1A1A1A)

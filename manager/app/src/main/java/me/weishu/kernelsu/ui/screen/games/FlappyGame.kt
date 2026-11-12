package me.weishu.kernelsu.ui.screen.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.random.Random
import me.weishu.kernelsu.R

// Bird is fixed at 22% from the left; Y coordinates are fractions of canvas height.
// X coordinates (pipes, BIRD_X) are fractions of canvas width.
private const val BIRD_X = 0.22f
private const val BIRD_RADIUS = 0.05f      // fraction of canvas height
private const val PIPE_WIDTH = 0.15f       // fraction of canvas width
private const val PIPE_GAP = 0.30f         // gap height as fraction of canvas height
private const val PIPE_SPEED = 0.32f       // canvas-widths per second
private const val PIPE_SPACING = 0.64f     // distance between consecutive pipe left edges
private const val GRAVITY = 2.0f           // height-fraction per second squared
private const val TAP_IMPULSE = -0.65f     // height-fraction per second, upward
private const val HIT_SHRINK = 0.75f       // forgiving collision radius

data class FlappyPipe(
    val x: Float,           // left edge, fraction of canvas width
    val gapCenter: Float,   // gap center, fraction of canvas height
    val passed: Boolean = false,
)

@Stable
class FlappyGameState(
    initialHighScore: Int = 0,
    onHighScoreUpdated: (Int) -> Unit = {},
) : MiniGameStateBase {
    var birdY by mutableFloatStateOf(0.45f)
        private set
    var velocity by mutableFloatStateOf(0f)
        private set
    var pipes by mutableStateOf<List<FlappyPipe>>(emptyList())
        private set
    private val points = GameScore(initialHighScore, onHighScoreUpdated)
    override val haptics = GameHaptics()
    override val score get() = points.value
    override val highScore get() = points.high
    override var isGameOver by mutableStateOf(false)
        private set
    var isStarted by mutableStateOf(false)
        private set
    override var isPaused by mutableStateOf(false)
        private set

    private var lastTickMs = 0L

    fun tap() {
        if (isGameOver) return
        if (!isStarted) {
            isStarted = true
            GameAudio.start()
        }
        velocity = TAP_IMPULSE
    }

    fun tick(nowMs: Long) {
        if (!isStarted || isGameOver || isPaused) return
        val dt = if (lastTickMs == 0L) 0.016f else (nowMs - lastTickMs).coerceIn(1, 50) / 1000f
        lastTickMs = nowMs

        velocity += GRAVITY * dt
        birdY += velocity * dt

        if (birdY - BIRD_RADIUS < 0f || birdY + BIRD_RADIUS > 1f) {
            endRound()
            return
        }

        // Spawn once the previous pipe has moved far enough - keeps spacing consistent at any frame rate.
        if (pipes.isEmpty() || pipes.last().x <= 1.05f - PIPE_SPACING) {
            pipes = pipes + FlappyPipe(x = 1.05f, gapCenter = Random.nextFloat() * 0.38f + 0.26f)
        }

        val hitR = BIRD_RADIUS * HIT_SHRINK
        var hit = false
        pipes = pipes.mapNotNull { pipe ->
            val nx = pipe.x - PIPE_SPEED * dt
            if (nx + PIPE_WIDTH < 0f) return@mapNotNull null
            val nowPassed = !pipe.passed && (nx + PIPE_WIDTH) < BIRD_X
            if (nowPassed) {
                points.value++
                haptics.tick()
                GameAudio.score()
            }
            if (BIRD_X + hitR > nx && BIRD_X - hitR < nx + PIPE_WIDTH) {
                val gapTop = pipe.gapCenter - PIPE_GAP / 2f
                val gapBottom = pipe.gapCenter + PIPE_GAP / 2f
                if (birdY - hitR < gapTop || birdY + hitR > gapBottom) hit = true
            }
            pipe.copy(x = nx, passed = pipe.passed || nowPassed)
        }
        if (hit) {
            endRound()
        }
    }

    private fun endRound() {
        points.commit()
        isGameOver = true
        GameAudio.gameOver()
    }

    override fun restart() {
        birdY = 0.45f
        velocity = 0f
        pipes = emptyList()
        points.reset()
        isGameOver = false
        isStarted = false
        isPaused = false
        lastTickMs = 0L
    }

    override val isPlaying get() = isStarted && !isGameOver && !isPaused

    override fun pause() { if (isStarted && !isGameOver) isPaused = true }
    override fun resume() { isPaused = false; lastTickMs = 0L }
}

@Composable
fun FlappyBirdGame(state: FlappyGameState) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // Runs every vsync while visible; tick() is a no-op when paused or over.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                androidx.compose.runtime.withFrameMillis { state.tick(it) }
            }
        }
    }
    FlappyCanvas(state = state, modifier = Modifier.fillMaxSize())
}

@Composable
private fun FlappyCanvas(state: FlappyGameState, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(FlappySkyColor)
            .pointerInput(Unit) { detectTapGestures(onPress = { state.tap() }) },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val capH = h * 0.035f
            val capExtra = w * 0.022f

            drawRect(
                color = FlappyGroundColor,
                topLeft = Offset(0f, h * 0.94f),
                size = Size(w, h * 0.06f),
            )

            for (pipe in state.pipes) {
                val pLeft = pipe.x * w
                val pW = PIPE_WIDTH * w
                val gapTop = (pipe.gapCenter - PIPE_GAP / 2f) * h
                val gapBottom = (pipe.gapCenter + PIPE_GAP / 2f) * h

                if (gapTop > 0f) {
                    drawRect(FlappyPipeFill, topLeft = Offset(pLeft, 0f), size = Size(pW, gapTop))
                    drawRect(
                        FlappyPipeCap,
                        topLeft = Offset(pLeft - capExtra, gapTop - capH),
                        size = Size(pW + capExtra * 2f, capH),
                    )
                }
                if (gapBottom < h) {
                    drawRect(FlappyPipeFill, topLeft = Offset(pLeft, gapBottom), size = Size(pW, h - gapBottom))
                    drawRect(
                        FlappyPipeCap,
                        topLeft = Offset(pLeft - capExtra, gapBottom),
                        size = Size(pW + capExtra * 2f, capH),
                    )
                }
            }

            val bx = BIRD_X * w
            val by = state.birdY * h
            val br = BIRD_RADIUS * h
            val angle = (state.velocity * 50f).coerceIn(-25f, 90f)

            rotate(degrees = angle, pivot = Offset(bx, by)) {
                drawCircle(FlappyBirdBody, radius = br, center = Offset(bx, by))
                drawCircle(Color.White, radius = br * 0.38f, center = Offset(bx + br * 0.3f, by - br * 0.15f))
                drawCircle(FlappyPupil, radius = br * 0.2f, center = Offset(bx + br * 0.38f, by - br * 0.15f))
                drawPath(
                    path = Path().apply {
                        moveTo(bx + br * 0.85f, by - br * 0.18f)
                        lineTo(bx + br * 1.55f, by)
                        lineTo(bx + br * 0.85f, by + br * 0.18f)
                        close()
                    },
                    color = FlappyBirdBeak,
                )
            }
        }

        if (!state.isStarted && !state.isGameOver) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.mini_game_flappy_tap_to_start),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.28f))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private val FlappySkyColor = Color(0xFF70C5CE)
private val FlappyGroundColor = Color(0xFFDED895)
private val FlappyPipeFill = Color(0xFF73C02A)
private val FlappyPipeCap = Color(0xFF5A9820)
private val FlappyBirdBody = Color(0xFFFDD835)
private val FlappyBirdBeak = Color(0xFFFF8F00)
private val FlappyPupil = Color(0xFF212121)

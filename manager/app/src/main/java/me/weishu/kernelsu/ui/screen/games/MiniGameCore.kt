package me.weishu.kernelsu.ui.screen.games

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.weishu.kernelsu.R

/** The mini-games reachable from the dashboard easter egg. */
enum class MiniGame(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
    val accent: Color,
    val aspectRatio: Float = 1f,
) {
    FLAPPY(
        R.string.mini_game_flappy,
        R.string.mini_game_flappy_subtitle,
        Icons.Rounded.Air,
        Color(0xFF73C02A),
    ),
    SNAKE(
        R.string.mini_game_snake,
        R.string.mini_game_snake_subtitle,
        Icons.Rounded.Gesture,
        Color(0xFF28C7C7),
    ),
    DINO(
        R.string.mini_game_dino,
        R.string.mini_game_dino_subtitle,
        Icons.Rounded.DirectionsRun,
        Color(0xFFC8B060),
        aspectRatio = 1.7f,
    ),
}

/** Shared contract every game state implements, used by the picker/game host. */
interface MiniGameStateBase {
    val score: Int
    val highScore: Int
    val isPlaying: Boolean
    val isGameOver: Boolean
    val isPaused: Boolean
    val haptics: GameHaptics

    fun restart()
    fun pause()
    fun resume()
}

/** Running score plus the record it is measured against. */
@Stable
class GameScore(initialHighScore: Int, private val onHighScoreUpdated: (Int) -> Unit) {
    var value by mutableIntStateOf(0)
    var high by mutableIntStateOf(initialHighScore)
        private set

    fun commit() {
        if (value > high) {
            high = value
            onHighScoreUpdated(high)
        }
    }

    fun reset() {
        value = 0
    }
}

enum class GameHaptic { Tick, Reward }

/** Small moments the games mark for the player to feel. */
@Stable
class GameHaptics {
    private val _events = MutableSharedFlow<GameHaptic>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<GameHaptic> = _events.asSharedFlow()

    fun tick() = _events.tryEmit(GameHaptic.Tick)
    fun reward() = _events.tryEmit(GameHaptic.Reward)
}

/**
 * Holds one state instance per game (built lazily) and remembers the picker
 * selection. High scores are persisted in shared preferences.
 */
@Stable
class MiniGameSession(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("noroisu_mini_games", Context.MODE_PRIVATE)

    private fun highScore(game: MiniGame): Int = prefs.getInt("high_${game.name}", 0)

    private fun saveHighScore(game: MiniGame, score: Int) {
        prefs.edit().putInt("high_${game.name}", score).apply()
    }

    var soundEnabled by mutableStateOf(prefs.getBoolean("sound_enabled", true))
        private set

    init {
        // Keep the audio object in sync with the saved preference.
        GameAudio.enabled = soundEnabled
    }

    fun toggleSound() {
        val enabled = !soundEnabled
        soundEnabled = enabled
        GameAudio.enabled = enabled
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
    }

    val flappy by lazy {
        FlappyGameState(highScore(MiniGame.FLAPPY)) { saveHighScore(MiniGame.FLAPPY, it) }
    }
    val snake by lazy {
        SnakeGameState(highScore(MiniGame.SNAKE)) { saveHighScore(MiniGame.SNAKE, it) }
    }
    val dino by lazy {
        DinoGameState(highScore(MiniGame.DINO)) { saveHighScore(MiniGame.DINO, it) }
    }

    var selectedGame by mutableStateOf<MiniGame?>(null)
        private set

    fun stateOf(game: MiniGame): MiniGameStateBase = when (game) {
        MiniGame.FLAPPY -> flappy
        MiniGame.SNAKE -> snake
        MiniGame.DINO -> dino
    }

    fun selectGame(game: MiniGame) {
        stateOf(game).restart()
        selectedGame = game
    }

    fun closeGame() {
        selectedGame = null
    }
}

// ---------------------------------------------------------------------------
// Picker
// ---------------------------------------------------------------------------

@Composable
internal fun MiniGamePicker(
    onSelect: (MiniGame) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(MiniGame.entries, key = { it.name }) { game ->
            Card(
                onClick = { onSelect(game) },
                modifier = Modifier.height(140.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(game.accent.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = game.icon,
                            contentDescription = null,
                            tint = game.accent,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(game.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(game.subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared game chrome
// ---------------------------------------------------------------------------

@Composable
internal fun GameScoreRow(
    score: Int,
    highScore: Int,
    soundEnabled: Boolean,
    onToggleSound: () -> Unit,
    onRestart: () -> Unit,
    onChangeGame: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = stringResource(R.string.mini_game_score, score),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Text(
            text = stringResource(R.string.mini_game_best, highScore),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleSound) {
            Icon(
                imageVector = if (soundEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                contentDescription = stringResource(
                    if (soundEnabled) R.string.mini_game_sound_off else R.string.mini_game_sound_on,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRestart) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.mini_game_restart),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onChangeGame) {
            Icon(
                imageVector = Icons.Rounded.SportsEsports,
                contentDescription = stringResource(R.string.mini_game_change),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun GameOverOverlay(
    score: Int,
    highScore: Int,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        // surfaceContainer stays visible on the minimal theme (surface is transparent there).
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.mini_game_game_over),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.mini_game_score, score),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (score >= highScore && score > 0) {
                    stringResource(R.string.mini_game_new_record)
                } else {
                    stringResource(R.string.mini_game_best, highScore)
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (score >= highScore && score > 0) {
                    Color(0xFFEDC22E)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            FilledTonalButton(onClick = onRestart) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.mini_game_try_again))
            }
        }
    }
}

/** Plays the game's haptic moments and a buzz when a round ends. */
@Composable
internal fun GameHapticsEffect(state: MiniGameStateBase) {
    val view = LocalView.current
    LaunchedEffect(state) {
        state.haptics.events.collect { haptic ->
            view.performHapticFeedback(
                when (haptic) {
                    GameHaptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
                    GameHaptic.Reward ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            HapticFeedbackConstants.CONFIRM
                        } else {
                            HapticFeedbackConstants.CONTEXT_CLICK
                        }
                },
            )
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.isGameOver }.collect { over ->
            if (over) {
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.REJECT
                    } else {
                        HapticFeedbackConstants.LONG_PRESS
                    },
                )
            }
        }
    }
}

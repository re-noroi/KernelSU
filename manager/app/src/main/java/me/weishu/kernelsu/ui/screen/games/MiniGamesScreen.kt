package me.weishu.kernelsu.ui.screen.games

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.navigation3.LocalNavigator

/**
 * Easter-egg mini-game hub, reachable by tapping the dashboard icon four times
 * while on the minimal theme.
 */
@Composable
fun MiniGamesScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val session = remember(context) { MiniGameSession(context) }
    val isMinimal = LocalUiMode.current == UiMode.Minimal
    val onBack = {
        if (session.selectedGame != null) session.closeGame() else navigator.pop()
    }
    // While a game is open, back returns to the picker instead of leaving the hub.
    BackHandler(enabled = session.selectedGame != null) { session.closeGame() }

    Scaffold(
        containerColor = if (isMinimal) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.mini_games_title),
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMinimal) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            tint = if (isMinimal) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = session.selectedGame,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "mini_game_picker_game",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { selected ->
            if (selected == null) {
                MiniGamePicker(onSelect = { session.selectGame(it) })
            } else {
                GameHost(
                    session = session,
                    game = selected,
                    soundEnabled = session.soundEnabled,
                    onToggleSound = { session.toggleSound() },
                )
            }
        }
    }
}

@Composable
private fun GameHost(
    session: MiniGameSession,
    game: MiniGame,
    soundEnabled: Boolean,
    onToggleSound: () -> Unit,
) {
    val state = session.stateOf(game)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GameScoreRow(
            score = state.score,
            highScore = state.highScore,
            soundEnabled = soundEnabled,
            onToggleSound = onToggleSound,
            onRestart = {
                state.restart()
                GameAudio.start()
            },
            onChangeGame = { session.closeGame() },
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val ratio = game.aspectRatio
            // Strip reserved for the pad, so big buttons never cover the game.
            val padStrip = if (game.needsControls) 176.dp else 0.dp
            val width = minOf(maxWidth, (maxHeight - padStrip).coerceAtLeast(0.dp) * ratio)

            Box(
                modifier = Modifier
                    .size(width, width / ratio)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                when (game) {
                    MiniGame.FLAPPY -> FlappyBirdGame(state = session.flappy)
                    MiniGame.SNAKE -> SnakeGame(state = session.snake)
                    MiniGame.DINO -> DinoGame(state = session.dino)
                    MiniGame.MINESWEEPER -> MinesweeperGame(state = session.minesweeper)
                    MiniGame.GAME_2048 -> Game2048(state = session.game2048)
                }

                GameHapticsEffect(state)

                if (state.isGameOver) {
                    val win = (state as? MinesweeperState)?.won == true ||
                        (state as? Game2048State)?.won == true
                    GameOverOverlay(
                        score = state.score,
                        highScore = state.highScore,
                        onRestart = { state.restart() },
                        modifier = Modifier.matchParentSize(),
                        title = if (win) {
                            stringResource(R.string.mini_game_you_win)
                        } else {
                            stringResource(R.string.mini_game_game_over)
                        },
                    )
                }
            }
        if (game.needsControls) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (game) {
                        MiniGame.SNAKE -> SnakeControls(state = session.snake)
                        MiniGame.DINO -> DinoControls(state = session.dino)
                        else -> Unit
                    }
                }
            }
        }
    }
}

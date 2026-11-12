package me.weishu.kernelsu.ui.screen.games

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R

private const val MS_COLS = 8
private const val MS_ROWS = 8
private const val MS_MINES = 10
private const val MS_CELLS = MS_COLS * MS_ROWS

data class MinesweeperCell(
    val mine: Boolean = false,
    val revealed: Boolean = false,
    val flagged: Boolean = false,
    val adjacent: Int = 0,
)

@Stable
class MinesweeperState(
    initialHighScore: Int = 0,
    onHighScoreUpdated: (Int) -> Unit = {},
) : MiniGameStateBase {
    var cells by mutableStateOf(List(MS_CELLS) { MinesweeperCell() })
        private set
    var started by mutableStateOf(false)
        private set
    var won by mutableStateOf(false)
        private set
    private val points = GameScore(initialHighScore, onHighScoreUpdated)
    override val haptics = GameHaptics()
    override val score get() = points.value
    override val highScore get() = points.high
    override var isGameOver by mutableStateOf(false)
        private set
    override val isPaused get() = false
    override val isPlaying get() = started && !isGameOver

    /** Mines still to be flagged, clamped so the header never goes negative. */
    val minesLeft: Int get() = (MS_MINES - cells.count { it.flagged }).coerceAtLeast(0)

    fun tap(index: Int) {
        if (isGameOver) return
        val cell = cells[index]
        if (cell.flagged || cell.revealed) return

        if (!started) {
            started = true
            val generated = placeMines(index)
            // Keep any flags placed before the first reveal.
            cells = generated.mapIndexed { i, c ->
                if (cells[i].flagged && !c.mine) c.copy(flagged = true) else c
            }
        }
        if (cells[index].mine) {
            cells = cells.map { c -> if (c.mine) c.copy(revealed = true) else c }
            points.commit()
            isGameOver = true
            GameAudio.gameOver()
            return
        }

        cells = revealFlood(cells, index)
        haptics.tick()
        if (cells.count { it.revealed } == MS_CELLS - MS_MINES) {
            won = true
            // The "score" of a minesweeper round is the running win count.
            points.value = points.high + 1
            points.commit()
            isGameOver = true
            haptics.reward()
            GameAudio.score()
        }
    }

    fun flag(index: Int) {
        if (isGameOver) return
        val cell = cells[index]
        if (cell.revealed) return
        val updated = cells.toMutableList()
        updated[index] = cell.copy(flagged = !cell.flagged)
        cells = updated
        haptics.tick()
    }

    override fun restart() {
        cells = List(MS_CELLS) { MinesweeperCell() }
        started = false
        won = false
        isGameOver = false
        points.reset()
    }

    override fun pause() = Unit
    override fun resume() = Unit

    private fun placeMines(safeIndex: Int): List<MinesweeperCell> {
        val safe = neighborsOf(safeIndex) + safeIndex
        val minePositions = (0 until MS_CELLS)
            .filter { it !in safe }
            .shuffled()
            .take(MS_MINES)
            .toHashSet()
        val result = MutableList(MS_CELLS) { i -> MinesweeperCell(mine = i in minePositions) }
        for (i in 0 until MS_CELLS) {
            result[i] = result[i].copy(adjacent = neighborsOf(i).count { result[it].mine })
        }
        return result
    }

    private fun revealFlood(board: List<MinesweeperCell>, start: Int): List<MinesweeperCell> {
        val result = board.toMutableList()
        val queue = ArrayDeque<Int>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val cell = result[i]
            if (cell.revealed || cell.flagged) continue
            result[i] = cell.copy(revealed = true)
            if (cell.adjacent == 0) {
                for (n in neighborsOf(i)) {
                    if (!result[n].revealed && !result[n].flagged) queue.add(n)
                }
            }
        }
        return result
    }

    private fun neighborsOf(index: Int): List<Int> {
        val row = index / MS_COLS
        val col = index % MS_COLS
        val result = mutableListOf<Int>()
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = row + dr
                val c = col + dc
                if (r in 0 until MS_ROWS && c in 0 until MS_COLS) result += r * MS_COLS + c
            }
        }
        return result
    }
}

@Composable
fun MinesweeperGame(state: MinesweeperState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(MsBoard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        ) {
            for (row in 0 until MS_ROWS) {
                Row(modifier = Modifier.weight(1f)) {
                    for (col in 0 until MS_COLS) {
                        val index = row * MS_COLS + col
                        val cell = state.cells[index]
                        val cellBg = when {
                            cell.revealed && cell.mine -> MsMine
                            cell.revealed -> MsRevealed
                            else -> MsHidden
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(cellBg)
                                .pointerInput(index) {
                                    detectTapGestures(
                                        onTap = { state.tap(index) },
                                        onLongPress = { state.flag(index) },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                cell.flagged && !cell.revealed ->
                                    Icon(
                                        imageVector = Icons.Rounded.Flag,
                                        contentDescription = null,
                                        tint = MsFlag,
                                        modifier = Modifier.size(14.dp),
                                    )
                                cell.revealed && cell.mine ->
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(MsMineDot),
                                    )
                                cell.revealed && cell.adjacent > 0 ->
                                    Text(
                                        text = cell.adjacent.toString(),
                                        color = adjacentColor(cell.adjacent),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                            }
                        }
                    }
                }
            }
        }

        if (!state.started && !state.isGameOver) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.mini_game_minesweeper_start),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MsRevealed.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MsHidden.copy(alpha = 0.92f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private fun adjacentColor(value: Int): Color = when (value) {
    1 -> Color(0xFF1976D2)
    2 -> Color(0xFF2E7D32)
    3 -> Color(0xFFC62828)
    4 -> Color(0xFF6A1B9A)
    5 -> Color(0xFF00695C)
    else -> Color(0xFF4E342E)
}

private val MsBoard = Color(0xFF4A6072)
private val MsHidden = Color(0xFF7C93A8)
private val MsRevealed = Color(0xFFE9E4D8)
private val MsMine = Color(0xFFC0392B)
private val MsMineDot = Color(0xFF2B2B2B)
private val MsFlag = Color(0xFFE74C3C)

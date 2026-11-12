package me.weishu.kernelsu.ui.screen.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private const val BOARD_SIZE = 4
private const val TILE_COUNT = BOARD_SIZE * BOARD_SIZE

enum class TileDirection { UP, DOWN, LEFT, RIGHT }

@Stable
class Game2048State(
    initialHighScore: Int = 0,
    onHighScoreUpdated: (Int) -> Unit = {},
) : MiniGameStateBase {
    var tiles by mutableStateOf(List(TILE_COUNT) { 0 })
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
    override val isPlaying get() = !isGameOver

    fun move(direction: TileDirection) {
        if (isGameOver) return
        val next = MutableList(TILE_COUNT) { 0 }
        var gained = 0
        for (i in 0 until BOARD_SIZE) {
            val indices = lineIndices(direction, i)
            val (merged, lineGain) = mergeLine(indices.map { tiles[it] })
            gained += lineGain
            for (c in 0 until BOARD_SIZE) next[indices[c]] = merged[c]
        }
        if (next == tiles) return

        points.value += gained
        if (gained > 0) haptics.tick()
        val updated = addRandomTile(next)
        tiles = updated

        if (!won && updated.any { it >= 2048 }) {
            won = true
            points.commit()
            isGameOver = true
            haptics.reward()
            GameAudio.score()
        } else if (!canMove(updated)) {
            points.commit()
            isGameOver = true
            GameAudio.gameOver()
        }
    }

    override fun restart() {
        tiles = addRandomTile(addRandomTile(List(TILE_COUNT) { 0 }))
        won = false
        isGameOver = false
        points.reset()
    }

    override fun pause() = Unit
    override fun resume() = Unit

    private fun lineIndices(direction: TileDirection, line: Int): List<Int> = when (direction) {
        TileDirection.LEFT -> (0 until BOARD_SIZE).map { line * BOARD_SIZE + it }
        TileDirection.RIGHT -> (0 until BOARD_SIZE).map { line * BOARD_SIZE + (BOARD_SIZE - 1 - it) }
        TileDirection.UP -> (0 until BOARD_SIZE).map { it * BOARD_SIZE + line }
        TileDirection.DOWN -> (0 until BOARD_SIZE).map { (BOARD_SIZE - 1 - it) * BOARD_SIZE + line }
    }

    /** Slides and merges one line, returning the resulting line and the points gained. */
    private fun mergeLine(line: List<Int>): Pair<List<Int>, Int> {
        val values = line.filter { it != 0 }
        val merged = mutableListOf<Int>()
        var gained = 0
        var i = 0
        while (i < values.size) {
            if (i + 1 < values.size && values[i] == values[i + 1]) {
                val value = values[i] * 2
                merged.add(value)
                gained += value
                i += 2
            } else {
                merged.add(values[i])
                i++
            }
        }
        while (merged.size < BOARD_SIZE) merged.add(0)
        return merged to gained
    }

    private fun addRandomTile(board: List<Int>): List<Int> {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return board
        val updated = board.toMutableList()
        updated[empty.random()] = if (Random.nextFloat() < 0.9f) 2 else 4
        return updated
    }

    private fun canMove(board: List<Int>): Boolean {
        if (board.any { it == 0 }) return true
        for (row in 0 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                val value = board[row * BOARD_SIZE + col]
                if (row + 1 < BOARD_SIZE && board[(row + 1) * BOARD_SIZE + col] == value) return true
                if (col + 1 < BOARD_SIZE && board[row * BOARD_SIZE + col + 1] == value) return true
            }
        }
        return false
    }
}

@Composable
fun Game2048(state: Game2048State) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(Twenty48Board)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in 0 until BOARD_SIZE) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (col in 0 until BOARD_SIZE) {
                    val value = state.tiles[row * BOARD_SIZE + col]
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(tileColor(value)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (value > 0) {
                            Text(
                                text = value.toString(),
                                color = tileTextColor(value),
                                fontWeight = FontWeight.Bold,
                                fontSize = if (value < 1000) 22.sp else 16.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Game2048Controls(state: Game2048State, modifier: Modifier = Modifier) {
    GamePad(
        onUp = { state.move(TileDirection.UP) },
        onDown = { state.move(TileDirection.DOWN) },
        onLeft = { state.move(TileDirection.LEFT) },
        onRight = { state.move(TileDirection.RIGHT) },
        modifier = modifier,
    )
}

private fun tileColor(value: Int): Color = when (value) {
    0 -> Color(0xFFCDC1B4)
    2 -> Color(0xFFEEE4DA)
    4 -> Color(0xFFEDE0C8)
    8 -> Color(0xFFF2B179)
    16 -> Color(0xFFF59563)
    32 -> Color(0xFFF67C5F)
    64 -> Color(0xFFF65E3B)
    128 -> Color(0xFFEDCF72)
    256 -> Color(0xFFEDCC61)
    512 -> Color(0xFFEDC850)
    1024 -> Color(0xFFEDC53F)
    2048 -> Color(0xFFEDC22E)
    else -> Color(0xFF3C3A32)
}

private fun tileTextColor(value: Int): Color =
    if (value <= 4) Color(0xFF776E65) else Color(0xFFF9F6F2)

private val Twenty48Board = Color(0xFFBBADA0)

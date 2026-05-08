package com.example.myreversi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.myreversi.ui.theme.MyReversiTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver

// ==========================================
// 1. Game Models & Logic
// ==========================================

enum class CellColor {
    NONE, BLACK, WHITE;

    fun opponent(): CellColor = when (this) {
        BLACK -> WHITE
        WHITE -> BLACK
        NONE -> NONE
    }

    fun toUiColor(): Color = when (this) {
        BLACK -> Color.Black
        WHITE -> Color.White
        NONE -> Color.Transparent
    }
}

data class GameState(
    val board: Array<Array<CellColor>>,
    val currentPlayer: CellColor,
    var availableMoves: Set<Pair<Int, Int>>,
    val gameOver: Boolean,
    val winner: CellColor?,
    val lastMove: Pair<Int, Int>?,
    val blackScore: Int,
    val whiteScore: Int,
    val history: List<Array<Array<CellColor>>> = emptyList(),
    val blackUndoCount: Int = 0,
    val whiteUndoCount: Int = 0
) {
    constructor() : this(
        board = Array(8) { Array(8) { CellColor.NONE } },
        currentPlayer = CellColor.BLACK,
        availableMoves = emptySet(),
        gameOver = false,
        winner = null,
        lastMove = null,
        blackScore = 2,
        whiteScore = 2,
        history = emptyList(),
        blackUndoCount = 0,
        whiteUndoCount = 0
    ) {
        this.board[3][4] = CellColor.WHITE
        this.board[4][3] = CellColor.WHITE
        this.board[3][3] = CellColor.BLACK
        this.board[4][4] = CellColor.BLACK
        this.availableMoves = ReversiGameLogic.getAvailableMoves(this.board, this.currentPlayer)
    }

    companion object {
        val Saver: Saver<GameState, *> = Saver<GameState, Map<String, Any>>(
            save = { state ->
                mapOf(
                    "board" to state.board.map { row -> row.map { it.name } },
                    "currentPlayer" to state.currentPlayer.name,
                    "gameOver" to state.gameOver,
                    "winner" to (state.winner?.name ?: "null"),
                    "blackScore" to state.blackScore,
                    "whiteScore" to state.whiteScore,
                    "blackUndoCount" to state.blackUndoCount,
                    "whiteUndoCount" to state.whiteUndoCount,
                    "history" to state.history.map { hBoard -> hBoard.map { row -> row.map { it.name } } }
                )
            },
            restore = { map ->
                val boardStrings = map["board"] as List<List<String>>
                val board = boardStrings.map { row -> row.map { CellColor.valueOf(it) }.toTypedArray() }.toTypedArray()
                val historyStrings = map["history"] as List<List<List<String>>>
                val history = historyStrings.map { hBoard -> hBoard.map { row -> row.map { CellColor.valueOf(it) }.toTypedArray() }.toTypedArray() }
                val currentPlayer = CellColor.valueOf(map["currentPlayer"] as String)
                val winnerStr = map["winner"] as String
                val winner = if (winnerStr == "null") null else CellColor.valueOf(winnerStr)

                GameState(
                    board = board,
                    currentPlayer = currentPlayer,
                    availableMoves = ReversiGameLogic.getAvailableMoves(board, currentPlayer),
                    gameOver = map["gameOver"] as Boolean,
                    winner = winner,
                    lastMove = null,
                    blackScore = map["blackScore"] as Int,
                    whiteScore = map["whiteScore"] as Int,
                    history = history,
                    blackUndoCount = map["blackUndoCount"] as Int,
                    whiteUndoCount = map["whiteUndoCount"] as Int
                )
            }
        )
    }
}

object ReversiGameLogic {
    private val DIRECTIONS = listOf(
        Pair(-1, -1), Pair(-1, 0), Pair(-1, 1),
        Pair(0, -1),           Pair(0, 1),
        Pair(1, -1),  Pair(1, 0), Pair(1, 1)
    )

    fun getNextTurn(current: CellColor) = if (current == CellColor.BLACK) CellColor.WHITE else CellColor.BLACK

    fun getFlippablePieces(board: Array<Array<CellColor>>, r: Int, c: Int, color: CellColor): List<Pair<Int, Int>> {
        val flippable = mutableListOf<Pair<Int, Int>>()
        for (dir in DIRECTIONS) {
            var cr = r + dir.first
            var cc = c + dir.second
            val potential = mutableListOf<Pair<Int, Int>>()
            while (cr in 0..7 && cc in 0..7 && board[cr][cc] == color.opponent()) {
                potential.add(Pair(cr, cc))
                cr += dir.first
                cc += dir.second
            }
            if (cr in 0..7 && cc in 0..7 && board[cr][cc] == color && potential.isNotEmpty()) {
                flippable.addAll(potential)
            }
        }
        return flippable
    }

    fun getAvailableMoves(board: Array<Array<CellColor>>, color: CellColor): Set<Pair<Int, Int>> {
        val moves = mutableSetOf<Pair<Int, Int>>()
        for (r in 0..7) {
            for (c in 0..7) {
                if (board[r][c] == CellColor.NONE) {
                    if (getFlippablePieces(board, r, c, color).isNotEmpty()) {
                        moves.add(Pair(r, c))
                    }
                }
            }
        }
        return moves
    }

    fun placePiece(board: Array<Array<CellColor>>, r: Int, c: Int, color: CellColor): Array<Array<CellColor>> {
        val toFlip = getFlippablePieces(board, r, c, color)
        if (toFlip.isEmpty()) return board
        val newBoard = board.map { it.copyOf() }.toTypedArray()
        newBoard[r][c] = color
        toFlip.forEach { (fr, fc) -> newBoard[fr][fc] = color }
        return newBoard
    }

    fun countPieces(board: Array<Array<CellColor>>) =
        board.flatten().groupBy { it }.mapValues { it.value.count() }
}

// ==========================================
// 2. UI Components
// ==========================================

@Composable
fun PlayerCard(player: CellColor, isActive: Boolean, score: Int, undoCount: Int, onUndo: () -> Unit, canUndo: Boolean) {
    val textColor = if (player == CellColor.BLACK) Color.Black else Color.DarkGray
    val bgColor = if (player == CellColor.BLACK) Color.LightGray else Color.White
    val outlineColor = if (isActive) MaterialTheme.colorScheme.primary else Color.LightGray

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor.copy(alpha = 0.5f))
            .then(if (isActive) Modifier.border(2.dp, outlineColor, RoundedCornerShape(12.dp)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Canvas(modifier = Modifier.size(32.dp)) {
            val r = size.minDimension / 2
            drawCircle(
                radius = r,
                brush = when(player) {
                    CellColor.BLACK -> Brush.radialGradient(listOf(Color(0xFF404040), Color.Black))
                    CellColor.WHITE -> Brush.radialGradient(listOf(Color.White, Color(0xFFBDBDBD)))
                    else -> Brush.sweepGradient(listOf(Color.Red, Color.Blue))
                }
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (player == CellColor.BLACK) "BLACK" else "WHITE",
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Score: $score",
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Button(
            onClick = onUndo,
            enabled = canUndo,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Undo ${3 - undoCount}", fontSize = 12.sp)
        }
    }
}

@Composable
fun ReversiBoard(gameState: GameState, onPlay: (Int, Int) -> Unit) {
    Canvas(
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .shadow(8.dp)
            .background(Color(0xFF2E7D32))
            .pointerInput(gameState.availableMoves) {
                detectTapGestures { offset ->
                    val r = (offset.y / size.height * 8).toInt().coerceIn(0, 7)
                    val c = (offset.x / size.width * 8).toInt().coerceIn(0, 7)
                    onPlay(r, c)
                }
            }
    ) {
            val cellPx = size.width / 8f
            // White grid lines
            for (i in 1..7) {
                val pos = i * cellPx
                drawLine(Color.White.copy(alpha = 0.5f), Offset(pos, 0f), Offset(pos, size.height), 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, pos), Offset(size.width, pos), 1.dp.toPx())
            }

            // White dots (Star points) at (2,2), (2,6), (6,2), (6,6) - 0-indexed: 2 and 6
            val dotRadius = 4.dp.toPx()
            val dotPositions = listOf(2, 6)
            for (r in dotPositions) {
                for (c in dotPositions) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.8f),
                        radius = dotRadius,
                        center = Offset(c * cellPx, r * cellPx)
                    )
                }
            }

            for (r in 0 until 8) {
                for (c in 0 until 8) {
                    val cell = gameState.board[r][c]
                    if (cell != CellColor.NONE) {
                        val radius = cellPx * 0.4f
                        val center = Offset(c * cellPx + cellPx / 2, r * cellPx + cellPx / 2)
                        
                        // Shadow for depth
                        //drawCircle(
                        //    color = Color.Black.copy(alpha = 0.3f),
                        //    radius = radius,
                        //    center = Offset(center.x + 2.dp.toPx(), center.y + 2.dp.toPx())
                        //)
                        
                        // Piece body with shiny radial gradient
                        drawCircle(
                            radius = radius,
                            center = center,
                            brush = when(cell) {
                                CellColor.BLACK -> Brush.radialGradient(
                                    colors = listOf(Color(0xFF808080), Color(0xFF202020), Color.Black),
                                    center = center,
                                    radius = radius
                                )
                                CellColor.WHITE -> Brush.radialGradient(
                                    colors = listOf(Color.White, Color(0xFFF5F5F5), Color(0xFFB0B0B0)),
                                    center = center,
                                    radius = radius
                                )
                                else -> Brush.radialGradient(listOf(Color.Transparent, Color.Transparent))
                            }
                        )
                    }
                }
            }
            gameState.availableMoves.forEach { (r, c) ->
                if (gameState.board[r][c] == CellColor.NONE) {
                    drawCircle(
                        color = gameState.currentPlayer.toUiColor().copy(alpha = 0.3f),
                        radius = cellPx * 0.12f,
                        center = Offset(c * cellPx + cellPx / 2, r * cellPx + cellPx / 2)
                    )
                }
            }
        }
}

@Composable
fun MyReversiGame() {
    var gameState by rememberSaveable(stateSaver = GameState.Saver) { mutableStateOf(GameState()) }
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Game") },
            text = { Text("Are you sure you want to reset the game?") },
            confirmButton = {
                Button(onClick = {
                    gameState = GameState()
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    BoxWithConstraints {
        fun play(r: Int, c: Int) {
            if (gameState.gameOver) return
            val newBoard = ReversiGameLogic.placePiece(gameState.board, r, c, gameState.currentPlayer)
            if (newBoard != gameState.board) {
                val newHistory = (gameState.history + listOf(gameState.board.map { it.copyOf() }.toTypedArray())).takeLast(10)
                val nextPlayer = ReversiGameLogic.getNextTurn(gameState.currentPlayer)
                val newAvailableMoves = ReversiGameLogic.getAvailableMoves(newBoard, nextPlayer)
                var finalMoves = newAvailableMoves
                var finalPlayer = nextPlayer
                var finalGameOver = false
                var finalWinner = gameState.winner
                if (newAvailableMoves.isEmpty()) {
                    val opponentMoves = ReversiGameLogic.getAvailableMoves(newBoard, gameState.currentPlayer)
                    if (opponentMoves.isEmpty()) {
                        finalGameOver = true
                        val scores = ReversiGameLogic.countPieces(newBoard)
                        val b = scores[CellColor.BLACK] ?: 0
                        val w = scores[CellColor.WHITE] ?: 0
                        finalWinner = if (b > w) CellColor.BLACK else if (w > b) CellColor.WHITE else null
                    } else {
                        finalPlayer = gameState.currentPlayer
                        finalMoves = opponentMoves
                    }
                }
                val newScores = ReversiGameLogic.countPieces(newBoard)
                gameState = GameState(
                    board = newBoard,
                    currentPlayer = finalPlayer,
                    availableMoves = finalMoves,
                    gameOver = finalGameOver,
                    winner = finalWinner,
                    lastMove = Pair(r, c),
                    blackScore = newScores[CellColor.BLACK] ?: 0,
                    whiteScore = newScores[CellColor.WHITE] ?: 0,
                    history = newHistory,
                    blackUndoCount = gameState.blackUndoCount,
                    whiteUndoCount = gameState.whiteUndoCount
                )
            }
        }

        fun undo() {
            val canUndo = if (gameState.currentPlayer == CellColor.BLACK) {
                gameState.whiteUndoCount < 3 // White just moved, now it's Black's turn, so White wants to undo
            } else {
                gameState.blackUndoCount < 3 // Black just moved, now it's White's turn, so Black wants to undo
            }
            if (gameState.history.isNotEmpty() && canUndo) {
                val previousBoard = gameState.history.last()
                val newHistory = gameState.history.dropLast(1)
                val prevPlayer = ReversiGameLogic.getNextTurn(gameState.currentPlayer)
                val scores = ReversiGameLogic.countPieces(previousBoard)
                gameState = GameState(
                    board = previousBoard,
                    currentPlayer = prevPlayer,
                    availableMoves = ReversiGameLogic.getAvailableMoves(previousBoard, prevPlayer),
                    gameOver = false,
                    winner = null,
                    lastMove = null,
                    blackScore = scores[CellColor.BLACK] ?: 0,
                    whiteScore = scores[CellColor.WHITE] ?: 0,
                    history = newHistory,
                    blackUndoCount = if (prevPlayer == CellColor.BLACK) gameState.blackUndoCount + 1 else gameState.blackUndoCount,
                    whiteUndoCount = if (prevPlayer == CellColor.WHITE) gameState.whiteUndoCount + 1 else gameState.whiteUndoCount
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (gameState.gameOver) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text("Game Over!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = when {
                                gameState.winner == CellColor.BLACK -> "Black Wins!"
                                gameState.winner == CellColor.WHITE -> "White Wins!"
                                else -> "Draw!"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { gameState = GameState() }) { Text("Play Again") }
                    }
                }
            } else {
                // Top Player (White)
                Box(modifier = Modifier.rotate(180f)) {
                    PlayerCard(
                        player = CellColor.WHITE,
                        isActive = gameState.currentPlayer == CellColor.WHITE,
                        score = gameState.whiteScore,
                        undoCount = gameState.whiteUndoCount,
                        onUndo = { undo() },
                        canUndo = gameState.whiteUndoCount < 3 && gameState.history.isNotEmpty() && gameState.currentPlayer == CellColor.BLACK
                    )
                }

                // Middle: Board and Reset
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    ReversiBoard(gameState, onPlay = { r, c -> play(r, c) })

                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.padding(top = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Game", fontSize = 12.sp)
                    }
                }

                // Bottom Player (Black)
                PlayerCard(
                    player = CellColor.BLACK,
                    isActive = gameState.currentPlayer == CellColor.BLACK,
                    score = gameState.blackScore,
                    undoCount = gameState.blackUndoCount,
                    onUndo = { undo() },
                    canUndo = gameState.blackUndoCount < 3 && gameState.history.isNotEmpty() && gameState.currentPlayer == CellColor.WHITE
                )
            }
        }
    }
}

// ==========================================
// 3. MainActivity
// ==========================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            MyReversiTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MyReversiGame()
                }
            }
        }
    }
}

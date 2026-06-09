package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.MatchRecord
import com.example.data.MatchRepository
import com.example.network.AppNetworkManager
import com.example.network.GameMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlayMode {
    LOCAL, LAN_HOST, LAN_JOIN, ONLINE_HOST, ONLINE_JOIN
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "me" or "opponent"
    val text: String,
    val timeLabel: String = "Now"
)

data class GameUiState(
    val board: List<String> = List(9) { "" },
    val isXTurn: Boolean = true,
    val winner: String = "", // "X", "O", "Draw", or ""
    val winningLine: List<Int>? = null,
    val userName: String = "Player 1",
    val hostName: String = "Host",
    val guestName: String = "Guest",
    val mySymbol: String = "X", // Host is default X, Guest is O
    val selectedMode: PlayMode = PlayMode.LOCAL,
    val networkState: AppNetworkManager.NetworkState = AppNetworkManager.NetworkState.Idle,
    val roomCode: String = "",
    val scoreX: Int = 0,
    val scoreO: Int = 0,
    val scoreDraws: Int = 0,
    val isReady: Boolean = false,
    val isMyTurn: Boolean = true,
    val chatMessages: List<ChatMessage> = emptyList(),
    val latestErrorMessage: String? = null
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "tictactoe-db"
    ).fallbackToDestructiveMigration().build()

    private val repository = MatchRepository(db.matchRecordDao())

    // UI state
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    // Room database history Flow
    val matchHistory: StateFlow<List<MatchRecord>> = repository.allMatches
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Retrieve standard initial name
        viewModelScope.launch {
            // Listen to network inputs
            launch {
                AppNetworkManager.incomingMessages.collect { msg ->
                    handleIncomingMessage(msg)
                }
            }

            // Listen to network connection states
            launch {
                AppNetworkManager.connectionState.collect { state ->
                    handleNetworkStateChange(state)
                }
            }
        }
    }

    private fun handleNetworkStateChange(state: AppNetworkManager.NetworkState) {
        _uiState.update { current ->
            val ready = state is AppNetworkManager.NetworkState.Connected
            val isHost = current.selectedMode == PlayMode.LAN_HOST || current.selectedMode == PlayMode.ONLINE_HOST
            val symbol = if (isHost) "X" else "O"
            val myTurn = if (isHost) current.isXTurn else !current.isXTurn

            current.copy(
                networkState = state,
                isReady = ready,
                mySymbol = symbol,
                isMyTurn = if (ready) myTurn else true
            )
        }
    }

    private fun handleIncomingMessage(msg: GameMessage) {
        when (msg.type) {
            "JOIN" -> {
                // Host receives this from guest
                _uiState.update { current ->
                    current.copy(
                        guestName = if (msg.message.isNotEmpty()) msg.message else "Guest",
                        isReady = true
                    )
                }
                // Send back acknowledgment with host's board Sync & name
                sendSyncAck()
            }
            "JOIN_ACK" -> {
                // Guest receives sync
                _uiState.update { current ->
                    val hostName = if (msg.hostName.isNotEmpty()) msg.hostName else "Host"
                    val guestName = if (msg.guestName.isNotEmpty()) msg.guestName else "Guest"
                    val isMyTurn = !msg.board.isEmpty() && (!current.isXTurn) // "O" is guest

                    current.copy(
                        hostName = hostName,
                        guestName = guestName,
                        board = if (msg.board.isNotEmpty()) msg.board else current.board,
                        isReady = true
                    )
                }
                checkForGameOver()
            }
            "MOVE" -> {
                val index = msg.index
                val opponentSymbol = if (_uiState.value.mySymbol == "X") "O" else "X"
                if (index in 0..8 && _uiState.value.board[index].isEmpty()) {
                    _uiState.update { current ->
                        val newBoard = current.board.toMutableList()
                        newBoard[index] = opponentSymbol
                        val nextTurn = !current.isXTurn
                        current.copy(
                            board = newBoard,
                            isXTurn = nextTurn,
                            isMyTurn = true // Turn comes back to me
                        )
                    }
                    checkForGameOver()
                }
            }
            "RESET" -> {
                performLocalReset(sendToOpponent = false)
            }
            "CHAT" -> {
                _uiState.update { current ->
                    val chatList = current.chatMessages.toMutableList()
                    chatList.add(ChatMessage(sender = "opponent", text = msg.message))
                    current.copy(chatMessages = chatList)
                }
            }
        }
    }

    private fun sendSyncAck() {
        val state = _uiState.value
        val msg = GameMessage(
            sender = "host",
            type = "JOIN_ACK",
            hostName = state.hostName,
            guestName = state.guestName,
            board = state.board
        )
        AppNetworkManager.sendMessage(msg, viewModelScope)
    }

    // --- USER INTERACTIONS ---

    fun updatePlayerName(name: String) {
        _uiState.update { current ->
            if (current.selectedMode == PlayMode.LAN_HOST || current.selectedMode == PlayMode.ONLINE_HOST) {
                current.copy(userName = name, hostName = name)
            } else {
                current.copy(userName = name, guestName = name)
            }
        }
        // Send a join status update if guest is already connected and name of player changes
        if (_uiState.value.isReady) {
            sendSyncAck()
        }
    }

    fun selectPlayMode(mode: PlayMode) {
        _uiState.update { current ->
            current.copy(
                selectedMode = mode,
                board = List(9) { "" },
                isXTurn = true,
                winner = "",
                winningLine = null,
                isReady = (mode == PlayMode.LOCAL),
                chatMessages = emptyList(),
                isMyTurn = true
            )
        }
        AppNetworkManager.disconnect()

        when (mode) {
            PlayMode.LAN_HOST -> {
                AppNetworkManager.startLanHost(viewModelScope)
            }
            PlayMode.ONLINE_HOST -> {
                val generatedRoom = generateRandomRoomCode()
                _uiState.update { it.copy(roomCode = generatedRoom) }
                AppNetworkManager.startOnlineMatch(generatedRoom, "host", viewModelScope)
            }
            else -> {}
        }
    }

    fun joinLanGame(ipAddress: String) {
        viewModelScope.launch {
            AppNetworkManager.connectToLanHost(ipAddress, viewModelScope)
        }
    }

    fun joinOnlineGame(code: String) {
        val cleanedCode = code.trim().uppercase()
        _uiState.update { it.copy(roomCode = cleanedCode) }
        viewModelScope.launch {
            AppNetworkManager.startOnlineMatch(cleanedCode, "guest", viewModelScope)
        }
    }

    fun cellClicked(index: Int) {
        val state = _uiState.value
        if (state.board[index].isNotEmpty()) return // Cell occupied
        if (state.winner.isNotEmpty()) return // Game already won/tied

        // In remote mode, must check if it is my turn
        if (state.selectedMode != PlayMode.LOCAL) {
            if (!state.isReady) return
            if (!state.isMyTurn) return
        }

        // Apply cell move
        _uiState.update { current ->
            val symbol = if (current.selectedMode == PlayMode.LOCAL) {
                if (current.isXTurn) "X" else "O"
            } else {
                current.mySymbol
            }

            val newBoard = current.board.toMutableList()
            newBoard[index] = symbol
            val nextTurn = !current.isXTurn

            current.copy(
                board = newBoard,
                isXTurn = nextTurn,
                isMyTurn = false // Yield turn
            )
        }

        // Send move to partner in remote games
        if (state.selectedMode != PlayMode.LOCAL) {
            val msg = GameMessage(
                sender = if (state.mySymbol == "X") "host" else "guest",
                type = "MOVE",
                index = index
            )
            AppNetworkManager.sendMessage(msg, viewModelScope)
        }

        checkForGameOver()
    }

    fun makeChat(text: String) {
        if (text.trim().isEmpty()) return
        val current = _uiState.value
        val msg = GameMessage(
            sender = if (current.mySymbol == "X") "host" else "guest",
            type = "CHAT",
            message = text.trim()
        )
        _uiState.update { ui ->
            val chatList = ui.chatMessages.toMutableList()
            chatList.add(ChatMessage(sender = "me", text = text.trim()))
            ui.copy(chatMessages = chatList)
        }
        AppNetworkManager.sendMessage(msg, viewModelScope)
    }

    fun playAgain() {
        performLocalReset(sendToOpponent = true)
    }

    private fun performLocalReset(sendToOpponent: Boolean) {
        _uiState.update { current ->
            val isHost = current.selectedMode == PlayMode.LAN_HOST || current.selectedMode == PlayMode.ONLINE_HOST || current.selectedMode == PlayMode.LOCAL
            current.copy(
                board = List(9) { "" },
                winner = "",
                winningLine = null,
                isXTurn = true,
                isMyTurn = if (current.selectedMode == PlayMode.LOCAL) true else (current.mySymbol == "X")
            )
        }

        if (sendToOpponent && _uiState.value.selectedMode != PlayMode.LOCAL) {
            val msg = GameMessage(
                sender = if (_uiState.value.mySymbol == "X") "host" else "guest",
                type = "RESET"
            )
            AppNetworkManager.sendMessage(msg, viewModelScope)
        }
    }

    fun resetMatchHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
        _uiState.update { current ->
            current.copy(scoreX = 0, scoreO = 0, scoreDraws = 0)
        }
    }

    // --- ALGORITHMIC LOGIC ---

    private fun checkForGameOver() {
        val state = _uiState.value
        val board = state.board

        val winPatterns = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // Rows
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // Columns
            listOf(0, 4, 8), listOf(2, 4, 6)                 // Diagonals
        )

        for (pattern in winPatterns) {
            val a = board[pattern[0]]
            val b = board[pattern[1]]
            val c = board[pattern[2]]

            if (a.isNotEmpty() && a == b && b == c) {
                // We have a winner!
                _uiState.update { current ->
                    val earnsX = (a == "X")
                    current.copy(
                        winner = a,
                        winningLine = pattern,
                        scoreX = current.scoreX + if (earnsX) 1 else 0,
                        scoreO = current.scoreO + if (!earnsX) 1 else 0
                    )
                }
                saveMatchToDb(a)
                return
            }
        }

        // Check for draw
        if (board.none { it.isEmpty() }) {
            _uiState.update { current ->
                current.copy(
                    winner = "Draw",
                    scoreDraws = current.scoreDraws + 1
                )
            }
            saveMatchToDb("Draw")
        }
    }

    private fun saveMatchToDb(winner: String) {
        val state = _uiState.value
        val modeStr = when (state.selectedMode) {
            PlayMode.LOCAL -> "Local 2-Player"
            PlayMode.LAN_HOST, PlayMode.LAN_JOIN -> "Local Network (LAN)"
            PlayMode.ONLINE_HOST, PlayMode.ONLINE_JOIN -> "Online Match"
        }

        val px = if (state.selectedMode == PlayMode.LOCAL) "Player X" else state.hostName
        val po = if (state.selectedMode == PlayMode.LOCAL) "Player O" else state.guestName

        viewModelScope.launch {
            repository.insert(
                MatchRecord(
                    playerX = px,
                    playerO = po,
                    winner = winner,
                    gameMode = modeStr
                )
            )
        }
    }

    private fun generateRandomRoomCode(): String {
        val chars = "ABCDEFGHIJKLMNPQRSTUVWXYZ123456789" // Omitted confusing O/0
        return (1..5)
            .map { chars[kotlin.random.Random.nextInt(chars.length)] }
            .joinToString("")
    }

    override fun onCleared() {
        super.onCleared()
        AppNetworkManager.disconnect()
    }
}

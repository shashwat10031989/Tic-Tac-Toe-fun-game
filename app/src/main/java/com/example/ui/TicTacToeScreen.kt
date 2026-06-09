package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.MatchRecord
import com.example.network.AppNetworkManager
import com.example.network.AppNetworkManager.NetworkState
import com.example.viewmodel.GameUiState
import com.example.viewmodel.GameViewModel
import com.example.viewmodel.PlayMode
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.scale

// Style Accent Colors
val NeonCyan = Color(0xFFD0BCFF) // Transformed to Purple-X
val NeonOrange = Color(0xFFEFB8C8) // Transformed to Pink-O
val DarkBackground = Color(0xFF1C1B1F) // Immersive Charcoal Background
val SurfaceCard = Color(0xFF2B2930) // Elevated Dark Surface
val AccentBorder = Color(0xFF49454F) // Border divider color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicTacToeScreen(
    viewModel: GameViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val matchHistory by viewModel.matchHistory.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Match Play, 1 = History/Scores

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "TIC ",
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonCyan,
                            fontSize = 24.sp
                        )
                        Text(
                            text = "TAC ",
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonOrange,
                            fontSize = 24.sp
                        )
                        Text(
                            text = "TOE",
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 24.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkBackground
                ),
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.resetMatchHistory()
                        },
                        modifier = Modifier.testTag("reset_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Lifetime Scores",
                            tint = Color.Gray
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkBackground,
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Play Match") },
                    label = { Text("Game Arena") }
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "Match History") },
                    label = { Text("Scores & Logs") }
                )
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (activeTab == 0) {
                GameArenaTab(uiState = uiState, viewModel = viewModel)
            } else {
                HistoryTab(matchHistory = matchHistory, viewModel = viewModel)
            }
        }
    }
}

@Composable
fun GameArenaTab(
    uiState: GameUiState,
    viewModel: GameViewModel
) {
    var isEditingName by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(uiState.userName) }

    // --- PLAYER IDENTITY CARD ---
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, AccentBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Player Alias",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                if (isEditingName) {
                    TextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("name_input_field"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (tempName.trim().isNotEmpty()) {
                                viewModel.updatePlayerName(tempName.trim())
                            }
                            isEditingName = false
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                } else {
                    Text(
                        text = uiState.userName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            IconButton(
                onClick = {
                    if (isEditingName) {
                        if (tempName.trim().isNotEmpty()) {
                            viewModel.updatePlayerName(tempName.trim())
                        }
                    } else {
                        tempName = uiState.userName
                    }
                    isEditingName = !isEditingName
                },
                modifier = Modifier.testTag("edit_name_button")
            ) {
                Icon(
                    imageVector = if (isEditingName) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = "Edit Name",
                    tint = if (isEditingName) NeonCyan else Color.White
                )
            }
        }
    }

    // --- GAME MODE SELECTOR ---
    Text(
        text = "Select Gaming Mode",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = Color.LightGray,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .padding(vertical = 8.dp)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModeTabButton(
            label = "Local 2P",
            icon = Icons.Default.People,
            isActive = uiState.selectedMode == PlayMode.LOCAL,
            modifier = Modifier.weight(1f).testTag("mode_local_btn"),
            onClick = { viewModel.selectPlayMode(PlayMode.LOCAL) }
        )
        ModeTabButton(
            label = "LAN Wi-Fi",
            icon = Icons.Default.Router,
            isActive = uiState.selectedMode == PlayMode.LAN_HOST || uiState.selectedMode == PlayMode.LAN_JOIN,
            modifier = Modifier.weight(1f).testTag("mode_lan_btn"),
            onClick = { viewModel.selectPlayMode(PlayMode.LAN_HOST) }
        )
        ModeTabButton(
            label = "Online",
            icon = Icons.Default.Cloud,
            isActive = uiState.selectedMode == PlayMode.ONLINE_HOST || uiState.selectedMode == PlayMode.ONLINE_JOIN,
            modifier = Modifier.weight(1f).testTag("mode_online_btn"),
            onClick = { viewModel.selectPlayMode(PlayMode.ONLINE_HOST) }
        )
    }

    // --- REMOTE CONNECTION PANELS ---
    if (uiState.selectedMode != PlayMode.LOCAL) {
        ConnectionWizard(uiState = uiState, viewModel = viewModel)
    }

    // --- SESSION SCORES ---
    ScoreDashboard(uiState = uiState)

    // --- GAUNTLET ARENA (PLAY GRID) ---
    GameGrid(uiState = uiState, viewModel = viewModel)

    // --- REMATCH CONTROLS ---
    if (uiState.winner.isNotEmpty()) {
        RematchPanel(uiState = uiState, viewModel = viewModel)
    }

    // --- CONVERSATION DRAWER (CHAT BOX) ---
    if (uiState.selectedMode != PlayMode.LOCAL && uiState.isReady) {
        ChatPanel(uiState = uiState, viewModel = viewModel)
    }
}

@Composable
fun ModeTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (isActive) Color(0xFFD0BCFF) else Color(0xFF49454F),
        border = BorderStroke(1.dp, if (isActive) Color(0xFFD0BCFF) else AccentBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color(0xFF381E72) else Color(0xFFE6E1E5),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (isActive) Color(0xFF381E72) else Color(0xFFE6E1E5),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ConnectionWizard(
    uiState: GameUiState,
    viewModel: GameViewModel
) {
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, AccentBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // LAN or ONLINE Switcher header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isLan = uiState.selectedMode == PlayMode.LAN_HOST || uiState.selectedMode == PlayMode.LAN_JOIN
                val modeLabel = if (isLan) "LAN Wi-Fi" else "Online Play"

                Text(
                    text = "$modeLabel Coordinator",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Sub-tabs for Host vs Join Choice
                TextButton(
                    onClick = {
                        val newMode = if (isLan) PlayMode.LAN_HOST else PlayMode.ONLINE_HOST
                        viewModel.selectPlayMode(newMode)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (uiState.selectedMode == PlayMode.LAN_HOST || uiState.selectedMode == PlayMode.ONLINE_HOST) NeonCyan else Color.Gray
                    )
                ) {
                    Text("Host", fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = {
                        val newMode = if (isLan) PlayMode.LAN_JOIN else PlayMode.ONLINE_JOIN
                        viewModel.selectPlayMode(newMode)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (uiState.selectedMode == PlayMode.LAN_JOIN || uiState.selectedMode == PlayMode.ONLINE_JOIN) NeonCyan else Color.Gray
                    )
                ) {
                    Text("Join", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // RENDER REQUISITE CONNECTOR
            when (uiState.selectedMode) {
                PlayMode.LAN_HOST -> {
                    val hostState = uiState.networkState as? NetworkState.LanHosting
                    Text(
                        text = "1. Turn on local Wi-Fi Hotspot or connect to the same Wi-Fi network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "2. Ask friend to Join using this Device IP:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = hostState?.ipAddress ?: "Resolving Local IP...",
                                style = MaterialTheme.typography.titleMedium,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Port: ${hostState?.port ?: AppNetworkManager.LAN_PORT}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        IconButton(
                            onClick = {
                                hostState?.ipAddress?.let {
                                    clipboardManager.setText(AnnotatedString(it))
                                }
                            },
                            enabled = hostState != null
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy IP", tint = Color.LightGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    NetworkStateBadge(uiState.networkState)
                }

                PlayMode.LAN_JOIN -> {
                    var ipInput by remember { mutableStateOf("") }

                    Text(
                        text = "Enter host IP address printed on your friend's screen:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ipInput,
                            onValueChange = { ipInput = it },
                            placeholder = { Text("e.g. 192.168.1.50", color = Color.DarkGray) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("lan_ip_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                keyboardController?.hide()
                                if (ipInput.trim().isNotEmpty()) {
                                    viewModel.joinLanGame(ipInput.trim())
                                }
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray
                            )
                        )

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                if (ipInput.trim().isNotEmpty()) {
                                    viewModel.joinLanGame(ipInput.trim())
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            modifier = Modifier.testTag("lan_connect_button")
                        ) {
                            Icon(Icons.Default.Link, contentDescription = "Connect", tint = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    NetworkStateBadge(uiState.networkState)
                }

                PlayMode.ONLINE_HOST -> {
                    Text(
                        text = "Send this Room Code to your online friend to join match:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.roomCode,
                            style = MaterialTheme.typography.headlineMedium,
                            color = NeonOrange,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            letterSpacing = 4.sp
                        )

                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(uiState.roomCode))
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", tint = Color.LightGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    NetworkStateBadge(uiState.networkState)
                }

                PlayMode.ONLINE_JOIN -> {
                    var roomCodeInput by remember { mutableStateOf("") }

                    Text(
                        text = "Enter the 5-letter Room Code from your friend:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = roomCodeInput,
                            onValueChange = { roomCodeInput = it.take(6).uppercase() },
                            placeholder = { Text("ROOM CODE", color = Color.DarkGray) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("online_code_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                keyboardController?.hide()
                                if (roomCodeInput.trim().isNotEmpty()) {
                                    viewModel.joinOnlineGame(roomCodeInput)
                                }
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray
                            )
                        )

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                if (roomCodeInput.trim().isNotEmpty()) {
                                    viewModel.joinOnlineGame(roomCodeInput)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                            modifier = Modifier.testTag("online_connect_button")
                        ) {
                            Icon(Icons.Default.CloudQueue, contentDescription = "Join game", tint = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Join Room", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    NetworkStateBadge(uiState.networkState)
                }
                else -> {}
            }
        }
    }
}

@Composable
fun NetworkStateBadge(state: NetworkState) {
    val (label, containerColor, contentColor) = when (state) {
        is NetworkState.Idle -> Triple("Ready", Color.Gray.copy(alpha = 0.2f), Color.White)
        is NetworkState.LanHosting -> Triple("Hosting on LAN...", NeonCyan.copy(alpha = 0.15f), NeonCyan)
        is NetworkState.LanConnecting -> Triple("Connecting to IP...", NeonCyan.copy(alpha = 0.15f), NeonCyan)
        is NetworkState.OnlineConnecting -> Triple("Joining online room...", NeonOrange.copy(alpha = 0.15f), NeonOrange)
        is NetworkState.Connected -> Triple("Connected - Opponent: ${state.opponentName}!", Color(0xFF4CAF50).copy(alpha = 0.15f), Color(0xFF4CAF50))
        is NetworkState.Disconnected -> Triple(state.reason, Color(0xFFF44336).copy(alpha = 0.15f), Color(0xFFF44336))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isPulse = state is NetworkState.LanHosting || state is NetworkState.LanConnecting || state is NetworkState.OnlineConnecting
            if (isPulse) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(color = contentColor, radius = 5.dp.toPx() * scale)
                }
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(color = contentColor, radius = 4.dp.toPx())
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ScoreDashboard(uiState: GameUiState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .padding(bottom = 16.dp),
        color = SurfaceCard,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, AccentBorder.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Player X Column
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    color = NeonCyan,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "X",
                            color = Color(0xFF381E72),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (uiState.selectedMode == PlayMode.LOCAL) "Player X" else "Me (${uiState.mySymbol})",
                    fontSize = 12.sp,
                    color = Color(0xFFE6E1E5),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = uiState.scoreX.toString(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
            }

            // VS / Ties Column
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "VS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF938F99)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF49454F).copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, AccentBorder.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Ties: ${uiState.scoreDraws}",
                        fontSize = 11.sp,
                        color = Color(0xFFE6E1E5),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Player O Column
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    color = NeonOrange,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "O",
                            color = Color(0xFF492532),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (uiState.selectedMode == PlayMode.LOCAL) "Player O" else "Opponent (${if (uiState.mySymbol == "X") "O" else "X"})",
                    fontSize = 12.sp,
                    color = Color(0xFFE6E1E5),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = uiState.scoreO.toString(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonOrange
                )
            }
        }
    }
}

@Composable
fun GameGrid(
    uiState: GameUiState,
    viewModel: GameViewModel
) {
    // Determine info message to display
    val infoMsg = when {
        uiState.selectedMode != PlayMode.LOCAL && !uiState.isReady -> {
            "Awaiting guest connection to commence..."
        }
        uiState.winner.isNotEmpty() -> {
            if (uiState.winner == "Draw") "Match is a Tie!" else "Winner: Player ${uiState.winner}!"
        }
        uiState.selectedMode == PlayMode.LOCAL -> {
            "Player ${if (uiState.isXTurn) "X" else "O"}'s Turn"
        }
        else -> {
            if (uiState.isMyTurn) "It's your turn!" else "Awaiting opponent's move..."
        }
    }

    Text(
        text = infoMsg,
        color = if (uiState.winner.isEmpty()) Color.White else (if (uiState.winner == "X") NeonCyan else if (uiState.winner == "O") NeonOrange else Color.LightGray),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 380.dp)
            .aspectRatio(1f)
            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .border(BorderStroke(2.dp, AccentBorder), RoundedCornerShape(20.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Draw standard Board lines or layout via Row/Column
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (row in 0..2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 0..2) {
                        val index = row * 3 + col
                        val value = uiState.board[index]
                        val isWinningCell = uiState.winningLine?.contains(index) == true

                        CellButton(
                            value = value,
                            isWinning = isWinningCell,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("cell_btn_$index"),
                            onClick = { viewModel.cellClicked(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CellButton(
    value: String,
    isWinning: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // Pulse animation for winning cell
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_win")
    val winGlowAlpha by if (isWinning) {
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowing"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Surface(
        modifier = modifier
            .scale(scale.value)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = {
                    scope.launch {
                        scale.animateTo(0.85f, tween(100))
                        scale.animateTo(1f, spring(stiffness = Spring.StiffnessLow))
                    }
                    onClick()
                }
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isWinning) {
            if (value == "X") NeonCyan.copy(alpha = 0.25f) else NeonOrange.copy(alpha = 0.25f)
        } else {
            SurfaceCard
        },
        border = BorderStroke(
            1.5.dp,
            if (isWinning) {
                if (value == "X") NeonCyan else NeonOrange
            } else if (value.isNotEmpty()) {
                Color.DarkGray
            } else {
                AccentBorder
            }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    scaleIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(100))
                },
                label = "cell_content"
            ) { symbol ->
                when (symbol) {
                    "X" -> {
                        Text(
                            text = "X",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            textAlign = TextAlign.Center
                        )
                    }
                    "O" -> {
                        Text(
                            text = "O",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonOrange,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        Spacer(modifier = Modifier.size(1.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RematchPanel(
    uiState: GameUiState,
    viewModel: GameViewModel
) {
    val buttonBgColor = if (uiState.winner == "X") NeonCyan else if (uiState.winner == "O") NeonOrange else Color(0xFFD0BCFF)
    val buttonContentColor = if (uiState.winner == "X") Color(0xFF381E72) else if (uiState.winner == "O") Color(0xFF492532) else Color(0xFF381E72)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, AccentBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (uiState.winner == "Draw") "Brilliant Match! It's a Tie Game." else "Congratulations Player ${uiState.winner} for the Victory!",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { viewModel.playAgain() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonBgColor,
                    contentColor = buttonContentColor
                ),
                modifier = Modifier.testTag("play_again_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Play Again", tint = buttonContentColor)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "REMATCH",
                    color = buttonContentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun ChatPanel(
    uiState: GameUiState,
    viewModel: GameViewModel
) {
    var chatText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    // Auto-scroll chat to bottom
    LaunchedEffect(uiState.chatMessages.size) {
        if (uiState.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.chatMessages.size - 1)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, AccentBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "In-Match Direct Chat",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Message Flow Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (uiState.chatMessages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No messages exchanged yet. Send a whisper!",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(uiState.chatMessages) { chat ->
                            val isMe = chat.sender == "me"
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                            ) {
                                Surface(
                                    color = if (isMe) NeonCyan.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(
                                        topStart = 10.dp,
                                        topEnd = 10.dp,
                                        bottomStart = if (isMe) 10.dp else 0.dp,
                                        bottomEnd = if (isMe) 0.dp else 10.dp
                                    ),
                                    border = BorderStroke(
                                        0.5.dp,
                                        if (isMe) NeonCyan.copy(alpha = 0.4f) else Color.Gray.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Text(
                                        text = chat.text,
                                        color = if (isMe) NeonCyan else Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Chat Input Line
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = chatText,
                    onValueChange = { chatText = it },
                    placeholder = { Text("Whisper or chat...", fontSize = 12.sp, color = Color.Gray) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("chat_input_field"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (chatText.trim().isNotEmpty()) {
                            viewModel.makeChat(chatText)
                            chatText = ""
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray
                    )
                )

                IconButton(
                    onClick = {
                        if (chatText.trim().isNotEmpty()) {
                            viewModel.makeChat(chatText)
                            chatText = ""
                        }
                    },
                    modifier = Modifier.testTag("chat_send_button")
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send Chat", tint = NeonCyan)
                }
            }
        }
    }
}

@Composable
fun HistoryTab(
    matchHistory: List<MatchRecord>,
    viewModel: GameViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 500.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, AccentBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Lifetime Stats Board",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                IconButton(
                    onClick = { viewModel.resetMatchHistory() },
                    modifier = Modifier.testTag("clear_history_btn")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Clear Match History", tint = Color.Red)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ANALYZE HISTORICAL RECORD INFO
            val totalMatches = matchHistory.size
            val winsX = matchHistory.count { it.winner == "X" }
            val winsO = matchHistory.count { it.winner == "O" }
            val draws = matchHistory.count { it.winner == "Draw" }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatValueItem(title = "Total Matches", value = totalMatches.toString(), color = Color.White)
                StatValueItem(title = "X Triumphs", value = winsX.toString(), color = NeonCyan)
                StatValueItem(title = "O Triumphs", value = winsO.toString(), color = NeonOrange)
                StatValueItem(title = "Tied Battles", value = draws.toString(), color = Color.LightGray)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Archived Chronological Arena Battles",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (matchHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = "No Game Logs",
                            tint = Color.DarkGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No recorded logs in SQLite table. Start play!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                    }
                }
            } else {
                val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    matchHistory.take(20).forEach { item ->
                        HistoryItemRow(match = item, formatter = dateFormat)
                    }
                }
            }
        }
    }
}

@Composable
fun StatValueItem(
    title: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
fun HistoryItemRow(
    match: MatchRecord,
    formatter: SimpleDateFormat
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.2f),
        border = BorderStroke(0.5.dp, AccentBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Mode Badge
                Surface(
                    color = when (match.winner) {
                        "X" -> NeonCyan.copy(alpha = 0.1f)
                        "O" -> NeonOrange.copy(alpha = 0.1f)
                        else -> Color.Gray.copy(alpha = 0.1f)
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = match.gameMode,
                        fontSize = 8.sp,
                        color = when (match.winner) {
                            "X" -> NeonCyan
                            "O" -> NeonOrange
                            else -> Color.LightGray
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${match.playerX} vs ${match.playerO}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = formatter.format(Date(match.timestamp)),
                    fontSize = 8.sp,
                    color = Color.DarkGray
                )
            }

            // Winner badge
            Surface(
                color = when (match.winner) {
                    "X" -> NeonCyan.copy(alpha = 0.15f)
                    "O" -> NeonOrange.copy(alpha = 0.15f)
                    else -> Color.Gray.copy(alpha = 0.15f)
                },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    0.5.dp,
                    when (match.winner) {
                        "X" -> NeonCyan
                        "O" -> NeonOrange
                        else -> Color.Gray
                    }
                )
            ) {
                Text(
                    text = if (match.winner == "Draw") "DRAW" else "WINNER: ${match.winner}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = when (match.winner) {
                        "X" -> NeonCyan
                        "O" -> NeonOrange
                        else -> Color.White
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

package com.example.network

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.*
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.TimeUnit

object AppNetworkManager {
    private const val TAG = "AppNetworkManager"
    const val LAN_PORT = 9876

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    val gameMessageAdapter = moshi.adapter(GameMessage::class.java)

    // Flow of incoming messages for the ViewModel to subscribe to
    private val _incomingMessages = MutableSharedFlow<GameMessage>(extraBufferCapacity = 100)
    val incomingMessages: SharedFlow<GameMessage> = _incomingMessages

    // Flow of connection status changes
    private val _connectionState = MutableSharedFlow<NetworkState>(extraBufferCapacity = 10)
    val connectionState: SharedFlow<NetworkState> = _connectionState

    // Active sockets/connections
    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var socketWriter: PrintWriter? = null
    private var socketReader: BufferedReader? = null

    // OkHttp Client & Online Sockets
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for websockets
        .build()
    private var activeWebSocket: WebSocket? = null
    private var onlineRoomCode: String? = null

    sealed class NetworkState {
        object Idle : NetworkState()
        data class LanHosting(val ipAddress: String, val port: Int) : NetworkState()
        data class LanConnecting(val targetIp: String) : NetworkState()
        data class OnlineConnecting(val roomCode: String) : NetworkState()
        data class Connected(val mode: String, val opponentName: String) : NetworkState() // "LAN" or "Online"
        data class Disconnected(val reason: String) : NetworkState()
    }

    // --- GET IP ADDRESS ---
    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress) {
                        val ip = address.hostAddress ?: ""
                        // Filter for common local subnets
                        if (ip.indexOf(':') < 0 && (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172."))) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP", e)
        }
        return "127.0.0.1" // Fallback
    }

    // --- LAN HOST OPERATION ---
    fun startLanHost(scope: CoroutineScope) {
        disconnect()
        scope.launch(Dispatchers.IO) {
            try {
                val ip = getLocalIpAddress()
                val server = ServerSocket(LAN_PORT)
                serverSocket = server
                _connectionState.emit(NetworkState.LanHosting(ip, LAN_PORT))
                Log.d(TAG, "LAN Host started on $ip:$LAN_PORT")

                val socket = server.accept() // Blocks until client joins
                activeSocket = socket
                setupSocketReaderAndWriter(socket, "LAN Host", scope)

                _connectionState.emit(NetworkState.Connected("LAN", "Guest"))
            } catch (e: Exception) {
                Log.e(TAG, "LAN host failed/canceled", e)
                _connectionState.emit(NetworkState.Disconnected(e.message ?: "Host match canceled"))
            }
        }
    }

    // --- LAN GUEST OPERATION ---
    fun connectToLanHost(targetIp: String, scope: CoroutineScope) {
        disconnect()
        scope.launch(Dispatchers.IO) {
            try {
                _connectionState.emit(NetworkState.LanConnecting(targetIp))
                Log.d(TAG, "Trying to connect to LAN host at $targetIp:$LAN_PORT")
                val socket = Socket(targetIp, LAN_PORT)
                activeSocket = socket
                setupSocketReaderAndWriter(socket, "LAN Guest", scope)

                _connectionState.emit(NetworkState.Connected("LAN", "Host"))
                Log.d(TAG, "Successfully connected to LAN Host!")
            } catch (e: Exception) {
                Log.e(TAG, "LAN Client connect failed", e)
                _connectionState.emit(NetworkState.Disconnected(e.message ?: "Could not connect to host"))
            }
        }
    }

    private fun setupSocketReaderAndWriter(socket: Socket, role: String, scope: CoroutineScope) {
        val writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream())), true)
        socketWriter = writer
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        socketReader = reader

        scope.launch(Dispatchers.IO) {
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val msgJson = line ?: continue
                    Log.d(TAG, "$role received raw: $msgJson")
                    val gameMsg = gameMessageAdapter.fromJson(msgJson)
                    if (gameMsg != null) {
                        _incomingMessages.emit(gameMsg)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Socket read error on $role", e)
            } finally {
                disconnect()
            }
        }
    }

    // --- ONLINE PLAY (ntfy.sh via WebSockets) ---
    fun startOnlineMatch(roomCode: String, role: String, scope: CoroutineScope) {
        disconnect()
        onlineRoomCode = roomCode
        val wsTopicUrl = "wss://ntfy.sh/tictactoe-qmxhwj-room-${roomCode.lowercase()}/ws"
        Log.d(TAG, "Connecting to online match at $wsTopicUrl")

        val request = Request.Builder()
            .url(wsTopicUrl)
            .build()

        _connectionState.tryEmit(NetworkState.OnlineConnecting(roomCode))

        activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Online WS match open for $role!")
                _connectionState.tryEmit(NetworkState.Connected("Online", if (role == "host") "Guest" else "Host"))

                // Immediately send notification to trigger peer synchronization
                if (role == "guest") {
                    sendOnlineMessage(GameMessage(sender = "guest", type = "JOIN", message = "Guest connected"), roomCode, scope)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Online WS incoming raw: $text")
                try {
                    // ntfy ws format is:
                    // {"id":"x","time":x,"event":"message","topic":"x","message":"{...}"}
                    val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                    val mapAdapter = moshi.adapter<Map<String, Any>>(mapType)
                    val jsonMap = mapAdapter.fromJson(text)
                    val event = jsonMap?.get("event") as? String
                    if (event == "message") {
                        val nestedMsgBody = jsonMap["message"] as? String
                        if (!nestedMsgBody.isNullOrEmpty()) {
                            val gameMsg = gameMessageAdapter.fromJson(nestedMsgBody)
                            if (gameMsg != null) {
                                scope.launch {
                                    _incomingMessages.emit(gameMsg)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding online message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Online WS failure", t)
                _connectionState.tryEmit(NetworkState.Disconnected("Connection lost or server error"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Online WS closed")
                _connectionState.tryEmit(NetworkState.Disconnected("Room closed"))
            }
        })
    }

    fun sendOnlineMessage(msg: GameMessage, roomCode: String, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val jsonPayload = gameMessageAdapter.toJson(msg)
                val targetUrl = "https://ntfy.sh/tictactoe-qmxhwj-room-${roomCode.lowercase()}"
                Log.d(TAG, "Online POST Payload URL: $targetUrl content: $jsonPayload")

                val request = Request.Builder()
                    .url(targetUrl)
                    .post(jsonPayload.toRequestBody("text/plain".toMediaType()))
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Failed to publish online move: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error POSTing online move", e)
            }
        }
    }

    // --- TRANSMIT GENERAL GAME MSG ---
    fun sendMessage(msg: GameMessage, scope: CoroutineScope) {
        val writer = socketWriter
        if (writer != null) {
            // LAN mode
            scope.launch(Dispatchers.IO) {
                try {
                    val json = gameMessageAdapter.toJson(msg)
                    Log.d(TAG, "LAN send raw: $json")
                    writer.println(json)
                } catch (e: Exception) {
                    Log.e(TAG, "LAN write failed", e)
                }
            }
        } else {
            // Check Online mode
            val code = onlineRoomCode
            if (activeWebSocket != null && code != null) {
                sendOnlineMessage(msg, code, scope)
            }
        }
    }

    // --- SHUT DOWN / CLEAN UP ---
    fun disconnect() {
        Log.d(TAG, "Disconnect/Reset triggered")
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        try {
            activeSocket?.close()
        } catch (_: Exception) {}
        activeSocket = null
        socketWriter = null
        socketReader = null

        activeWebSocket?.close(1000, "User disconnected")
        activeWebSocket = null
        onlineRoomCode = null

        _connectionState.tryEmit(NetworkState.Idle)
    }
}

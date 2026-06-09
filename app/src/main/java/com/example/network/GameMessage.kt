package com.example.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GameMessage(
    val sender: String, // "host" or "guest"
    val type: String,   // "JOIN", "JOIN_ACK", "MOVE", "RESET", "CHAT", "LEAVE"
    val index: Int = -1, // Cell index 0-8 for MOVE
    val message: String = "", // Chat text or custom payload
    val board: List<String> = emptyList(), // Current board state
    val hostName: String = "",
    val guestName: String = ""
)

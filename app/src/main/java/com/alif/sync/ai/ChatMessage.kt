package com.alif.sync.ai

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

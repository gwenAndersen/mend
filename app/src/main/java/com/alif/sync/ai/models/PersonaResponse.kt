package com.alif.sync.ai.models

import kotlinx.serialization.Serializable

@Serializable
data class PersonaResponse(
    val text: String,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val source: String = "general"
)

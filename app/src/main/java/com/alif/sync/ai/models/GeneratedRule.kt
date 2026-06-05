package com.alif.sync.ai.models

import kotlinx.serialization.Serializable

@Serializable
data class GeneratedRule(
    val condition: String,
    val response: String,
    val explanation: String,
    val layer: String? = null // "Layer 1" (offline) or "Layer 2" (online)
)

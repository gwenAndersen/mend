package com.alif.sync.ai.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class RuleWrapper(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "pending", "approved", "rejected"
    val targetAi: String = "marketing",
    val rule: GeneratedRule
)

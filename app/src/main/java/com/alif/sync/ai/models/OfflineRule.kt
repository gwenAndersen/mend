package com.alif.sync.ai.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class MatchStrategy {
    MATCH_ALL,
    MATCH_ANY
}

@Serializable
data class Condition(
    val type: String,
    val value: JsonElement 
)

@Serializable
data class OfflineRule(
    val ruleName: String,
    val conditions: List<Condition>,
    val matchStrategy: MatchStrategy,
    val response: String
)

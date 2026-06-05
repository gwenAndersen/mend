package com.alif.sync.ai.models

import kotlinx.serialization.Serializable

@Serializable
data class MarketingRules(
    val stages: List<Stage>
)

@Serializable
data class Stage(
    val stage: String,
    val keywords: List<String>,
    val prompt: String,
    val responses: List<String>
)

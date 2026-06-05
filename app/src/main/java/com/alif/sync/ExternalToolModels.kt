package com.alif.sync

import kotlinx.serialization.Serializable

@Serializable
data class ExternalTool(
    val id: String,
    val name: String,
    val subtitle: String,
    val iconName: String = "Settings",
    val packageName: String
)

@Serializable
data class ExternalToolAction(
    val label: String,
    val action: String,
    val extra: String? = null
)

@Serializable
data class ExternalToolContent(
    val toolId: String,
    val title: String,
    val description: String,
    val actions: List<ExternalToolAction> = emptyList()
)

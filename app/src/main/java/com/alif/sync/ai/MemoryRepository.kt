package com.alif.sync.ai

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

@Serializable
data class UserMemory(
    val lastSummary: String = "",
    val facts: List<String> = emptyList(),
    val psychologicalState: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)

class MemoryRepository(private val context: Context) {
    private val fileName = "user_memory.json"
    private val externalDir = File("/storage/emulated/0/alyf/buseness/")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun saveMemory(memory: UserMemory) {
        try {
            if (!externalDir.exists()) externalDir.mkdirs()
            val file = File(externalDir, fileName)
            val jsonString = json.encodeToString(UserMemory.serializer(), memory)
            file.writeText(jsonString)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun loadMemory(): UserMemory {
        val file = File(externalDir, fileName)
        if (!file.exists()) return UserMemory()

        return try {
            val jsonString = file.readText()
            json.decodeFromString(UserMemory.serializer(), jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            UserMemory()
        }
    }
}

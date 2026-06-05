package com.alif.sync.ai

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

class ChatRepository(private val context: Context) {
    private val fileName = "chat_history.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun saveMessages(messages: List<ChatMessage>) {
        try {
            val jsonString = json.encodeToString(ListSerializer(ChatMessage.serializer()), messages)
            context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
                it.write(jsonString.toByteArray())
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun loadMessages(): List<ChatMessage> {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return emptyList()

        return try {
            val jsonString = file.readText()
            json.decodeFromString(ListSerializer(ChatMessage.serializer()), jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun clearHistory() {
        val file = File(context.filesDir, fileName)
        if (file.exists()) file.delete()
    }
}

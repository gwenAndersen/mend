package com.alif.sync.ai

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alif.sync.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)
    private val memoryRepository = MemoryRepository(application)
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY
    private val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        loadHistory()
        initializeMemoryFromAssets()
    }

    private fun initializeMemoryFromAssets() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentMemory = memoryRepository.loadMemory()
            if (currentMemory.lastSummary.isEmpty()) {
                try {
                    val assetJson = getApplication<Application>().assets.open("user_context.json").bufferedReader().use { it.readText() }
                    val assetObj = JSONObject(assetJson)
                    val memory = UserMemory(
                        lastSummary = assetObj.optString("summary"),
                        facts = emptyList(),
                        psychologicalState = "Loaded from initial context"
                    )
                    memoryRepository.saveMemory(memory)
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Failed to init memory from assets", e)
                }
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val history = repository.loadMessages()
            _messages.value = history
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
            _messages.value = emptyList()
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        val userMessage = ChatMessage(text = userText, isUser = true)
        val currentList = _messages.value + userMessage
        _messages.value = currentList
        _isLoading.value = true

        repository.saveMessages(currentList)

        viewModelScope.launch {
            val responseText = generateContent(userText, currentList)
            
            // Handle [SAVE_MEMORY] tags
            val cleanedResponse = handleMemoryTags(responseText)
            
            val aiMessage = ChatMessage(text = cleanedResponse, isUser = false)
            val newList = _messages.value + aiMessage
            _messages.value = newList
            _isLoading.value = false
            repository.saveMessages(newList)
        }
    }

    private fun handleMemoryTags(responseText: String): String {
        var cleanedResponse = responseText
        
        // Handle [SAVE_MEMORY: ...] - Simple fact appending
        val memoryTag = "[SAVE_MEMORY:"
        if (cleanedResponse.contains(memoryTag)) {
            val startIndex = cleanedResponse.indexOf(memoryTag)
            val endIndex = cleanedResponse.indexOf("]", startIndex)
            if (endIndex != -1) {
                val memoryContent = cleanedResponse.substring(startIndex + memoryTag.length, endIndex).trim()
                cleanedResponse = cleanedResponse.removeRange(startIndex, endIndex + 1).trim()
                
                viewModelScope.launch(Dispatchers.IO) {
                    val currentMemory = memoryRepository.loadMemory()
                    val updatedFacts = (currentMemory.facts + memoryContent).distinct().takeLast(30) // Keep last 30 facts
                    memoryRepository.saveMemory(currentMemory.copy(facts = updatedFacts, lastUpdated = System.currentTimeMillis()))
                    Log.d("ChatViewModel", "Fact updated: $memoryContent")
                }
            }
        }

        // Handle [SAVE_MEMORY_JSON: ...] - Full context update for efficiency
        val jsonTag = "[SAVE_MEMORY_JSON:"
        if (cleanedResponse.contains(jsonTag)) {
            val startIndex = cleanedResponse.indexOf(jsonTag)
            val endIndex = cleanedResponse.indexOf("]", startIndex)
            if (endIndex != -1) {
                val jsonContent = cleanedResponse.substring(startIndex + jsonTag.length, endIndex).trim()
                cleanedResponse = cleanedResponse.removeRange(startIndex, endIndex + 1).trim()
                
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val newMemory = Json.decodeFromString(UserMemory.serializer(), jsonContent)
                        memoryRepository.saveMemory(newMemory.copy(lastUpdated = System.currentTimeMillis()))
                        Log.d("ChatViewModel", "Memory consolidated via JSON")
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Failed to parse SAVE_MEMORY_JSON", e)
                    }
                }
            }
        }

        return cleanedResponse
    }

    private suspend fun generateContent(prompt: String, history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        try {
            // Efficiency: Take only last 12 messages for history to save tokens
            val previousMessages = history.dropLast(1).takeLast(12) 
            
            val historyText = previousMessages.joinToString("\n") {
                if (it.isUser) "U: ${it.text}" else "A: ${it.text}"
            }

            // Efficiency: Load only the last 10 facts if the list is long
            val memory = memoryRepository.loadMemory()
            val recentFacts = if (memory.facts.size > 10) memory.facts.takeLast(10) else memory.facts
            
            val userContext = """
                Sum: ${memory.lastSummary}
                Facts: ${recentFacts.joinToString(", ")}
                State: ${memory.psychologicalState}
            """.trimIndent()

            val finalPrompt = """
                App: Sync (Mental state/Priority tracker).
                Long-term Memory:
                $userContext

                Instructions:
                - Use [SAVE_MEMORY: fact] to remember specific new details.
                - If memory is cluttered, use [SAVE_MEMORY_JSON: {"lastSummary": "...", "facts": [], "psychologicalState": "..."}] to consolidate/wipe old facts.
                - Be concise. Bengali/English mixed.

                History:
                $historyText
                
                U: $prompt
                A:
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", finalPrompt)
                            })
                        })
                    })
                })
            }.toString()

            val request = Request.Builder()
                .url(GEMINI_API_URL)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    Log.e("ChatViewModel", "Gemini API Error: ${response.code} - $responseBody")
                    return@withContext "Error: ${response.code} - ${response.message}"
                }

                if (responseBody == null) return@withContext "Empty response"
                val jsonResponse = JSONObject(responseBody)
                
                val textContent: String = jsonResponse.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: "No content generated."

                return@withContext textContent
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Gemini API Exception: ${e.message}", e)
            return@withContext "Error: ${e.message}"
        }
    }
}

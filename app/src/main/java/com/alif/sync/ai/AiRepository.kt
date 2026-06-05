package com.alif.sync.ai

import android.util.Log
import com.alif.sync.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeminiResponse(
    val text: String,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0
)

class AiRepository {
    private val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY
    private val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateContent(prompt: String): GeminiResponse = withContext(Dispatchers.IO) {
        val inputTokens = prompt.length / 4 
        try {
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
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
                    Log.e("AiRepository", "Gemini API Error: ${response.code} - $responseBody")
                    return@withContext GeminiResponse(
                        text = "Error: ${response.code}",
                        inputTokens = inputTokens
                    )
                }

                if (responseBody == null) return@withContext GeminiResponse("Empty response", inputTokens)
                val jsonResponse = JSONObject(responseBody)
                
                val textContent: String = jsonResponse.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: "No content generated."

                return@withContext GeminiResponse(
                    text = textContent,
                    inputTokens = inputTokens,
                    outputTokens = textContent.length / 4
                )
            }
        } catch (e: Exception) {
            Log.e("AiRepository", "Gemini API Exception: ${e.message}", e)
            return@withContext GeminiResponse("Error: ${e.message}", inputTokens)
        }
    }
}

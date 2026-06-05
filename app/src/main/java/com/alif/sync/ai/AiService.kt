package com.alif.sync.ai

/*
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class AiService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private lateinit var personaEngine: PersonaEngine
    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate() {
        super.onCreate()
        val aiRepository = AiRepository()
        personaEngine = PersonaEngine(this, aiRepository)
    }

    private val binder = object : IAiService.Stub() {
        override fun generateReply(historyJson: String, persona: String, callback: IAiCallback) {
            scope.launch {
                try {
                    val history = json.decodeFromString(ListSerializer(ChatMessage.serializer()), historyJson)
                    personaEngine.setPersona(persona)
                    val personaResponse = personaEngine.generateReply(history)
                    val responseJson = json.encodeToString(com.alif.sync.ai.models.PersonaResponse.serializer(), personaResponse)
                    callback.onResponse(responseJson)
                } catch (e: Exception) {
                    Log.e("AiService", "Error generating reply: ${e.message}")
                    callback.onError(e.message ?: "Unknown error")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
*/

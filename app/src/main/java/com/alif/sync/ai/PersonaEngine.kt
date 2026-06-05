package com.alif.sync.ai

import android.content.Context
import android.util.Log
import com.alif.sync.ai.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File
import java.io.IOException

class PersonaEngine(private val context: Context, private val aiRepository: AiRepository) {

    private val json = Json { ignoreUnknownKeys = true }
    
    private var parsedMarketingRules: MarketingRules = MarketingRules(emptyList())
    private var generatedRules: List<GeneratedRule> = emptyList()
    private var offlineRules: List<OfflineRule> = emptyList()

    private var currentPersona = "marketing"

    suspend fun setPersona(persona: String) {
        if (currentPersona != persona) {
            currentPersona = persona
            loadRulesForPersona(persona)
        }
    }

    init {
        // We'll load rules when needed or in background
    }

    suspend fun loadRulesForPersona(persona: String) = withContext(Dispatchers.IO) {
        val rulesFileName = if (persona == "marketing") "marketing_rules.json" else "${persona}_rules.json"
        val offlineRulesFileName = if (persona == "marketing") "offline_rules.json" else "${persona}_offline.json"

        // Layer 3 Rules
        try {
            val jsonString = loadRulesJsonString(rulesFileName)
            if (jsonString.isNotBlank()) {
                parsedMarketingRules = json.decodeFromString(MarketingRules.serializer(), jsonString)
            }
        } catch (e: Exception) {
            Log.e("PersonaEngine", "Error loading rules for $persona: ${e.message}")
            parsedMarketingRules = MarketingRules(emptyList())
        }

        // Layer 2 Rules
        try {
            val generatedRulesFile = File("/storage/emulated/0/alyf/buseness/generated_rules.json")
            if (generatedRulesFile.exists()) {
                val jsonString = generatedRulesFile.readText()
                if (jsonString.isNotBlank()) {
                    val allWrappedRules = json.decodeFromString(ListSerializer(RuleWrapper.serializer()), jsonString)
                    generatedRules = allWrappedRules
                        .filter { it.status == "approved" && it.targetAi == persona }
                        .map { it.rule }
                }
            }
        } catch (e: Exception) {
            Log.e("PersonaEngine", "Error loading generated rules: ${e.message}")
        }

        // Layer 1 Rules
        try {
            val jsonString = loadRulesJsonString(offlineRulesFileName)
            if (jsonString.isNotBlank()) {
                offlineRules = json.decodeFromString(ListSerializer(OfflineRule.serializer()), jsonString)
            }
        } catch (e: Exception) {
            Log.e("PersonaEngine", "Error loading offline rules: ${e.message}")
        }
    }

    private fun loadRulesJsonString(fileName: String): String {
        val externalFile = File("/storage/emulated/0/alyf/buseness/$fileName")
        return if (externalFile.exists()) {
            try {
                externalFile.readText()
            } catch (e: IOException) {
                loadFromAssets(fileName)
            }
        } else {
            loadFromAssets(fileName)
        }
    }

    private fun loadFromAssets(fileName: String): String {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            ""
        }
    }

    suspend fun generateReply(history: List<ChatMessage>): PersonaResponse {
        val lastUserMessage = history.lastOrNull { it.isUser }?.text ?: ""
        
        // --- Layer 1: Offline Rules ---
        val offlineReply = evaluateOfflineRules(history)
        if (offlineReply != null) return PersonaResponse(text = offlineReply, source = "first_layer_offline")

        // --- Layer 2: Generated Rules ---
        val generatedRule = detectStageFromGeneratedRules(lastUserMessage)
        if (generatedRule != null) {
            val prompt = buildGeneratedRulePrompt(generatedRule, history)
            val response = aiRepository.generateContent(prompt)
            return PersonaResponse(text = response.text, inputTokens = response.inputTokens, outputTokens = response.outputTokens, source = "second_layer_generated")
        }

        // --- Layer 3: Marketing Rules ---
        val relevantStage = detectStage(lastUserMessage)
        if (relevantStage != null && parsedMarketingRules.stages.isNotEmpty()) {
            val prompt = buildMarketingRulePrompt(relevantStage, history)
            val response = aiRepository.generateContent(prompt)
            return PersonaResponse(text = response.text, inputTokens = response.inputTokens, outputTokens = response.outputTokens, source = "third_layer_rules")
        }

        // --- Fallback: General Chat ---
        val historyText = history.takeLast(10).joinToString("\n") {
            if (it.isUser) "User: ${it.text}" else "AI: ${it.text}"
        }
        val fallbackPrompt = "You are a helpful assistant. Context:\n$historyText\nAI:"
        val response = aiRepository.generateContent(fallbackPrompt)
        return PersonaResponse(text = response.text, inputTokens = response.inputTokens, outputTokens = response.outputTokens, source = "general")
    }

    private fun evaluateOfflineRules(history: List<ChatMessage>): String? {
        for (rule in offlineRules) {
            val conditionsMet = when (rule.matchStrategy) {
                MatchStrategy.MATCH_ALL -> rule.conditions.all { checkCondition(it, history) }
                MatchStrategy.MATCH_ANY -> rule.conditions.any { checkCondition(it, history) }
            }
            if (conditionsMet) return rule.response
        }
        return null
    }

    private fun checkCondition(condition: Condition, history: List<ChatMessage>): Boolean {
        val lastUserMessage = history.lastOrNull { it.isUser }?.text?.lowercase() ?: ""
        return try {
            when (condition.type) {
                "IS_FIRST_MESSAGE_FROM_USER" -> {
                    val conditionValue = json.decodeFromJsonElement<Boolean>(condition.value)
                    (history.count { it.isUser } == 1) == conditionValue
                }
                "CONTAINS_ANY_KEYWORD" -> {
                    val keywords = json.decodeFromJsonElement<List<String>>(condition.value)
                    keywords.any { keyword -> lastUserMessage.contains(keyword.lowercase()) }
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun detectStageFromGeneratedRules(userMessage: String): GeneratedRule? {
        val lowerCaseMessage = userMessage.lowercase()
        return generatedRules.firstOrNull { rule ->
            lowerCaseMessage.contains(rule.condition.lowercase())
        }
    }

    private fun detectStage(userMessage: String): Stage? {
        val lowerCaseMessage = userMessage.lowercase()
        for (stage in parsedMarketingRules.stages) {
            if (stage.keywords.any { keyword -> lowerCaseMessage.contains(keyword.lowercase()) }) {
                return stage
            }
        }
        return parsedMarketingRules.stages.firstOrNull { it.stage == "1. Initial Pitch" }
    }

    private fun buildGeneratedRulePrompt(rule: GeneratedRule, history: List<ChatMessage>): String {
        val historyText = history.joinToString("\n") { if (it.isUser) "Friend: ${it.text}" else "Me: ${it.text}" }
        val userContext = try {
            context.assets.open("user_context.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
        return """
            You are a helpful assistant for the 'Sync' app.
            
            --- User Global Context ---
            $userContext
            --- End Context ---

            A customer/friend has said something that matches the condition: "${rule.condition}".
            Based on the following instruction, please provide a reply in Bengali.

            Instruction: ${rule.explanation}
            Suggested Response Template: ${rule.response}

            --- Conversation History ---
            $historyText
            --- End of History ---

            Your final reply in Bengali:
        """.trimIndent()
    }

    private fun buildMarketingRulePrompt(stage: Stage, history: List<ChatMessage>): String {
        val historyText = history.joinToString("\n") { if (it.isUser) "Friend: ${it.text}" else "Me: ${it.text}" }
        val marketingRulesContent = "Current Stage: ${stage.stage}\nPrompt: ${stage.prompt}\nResponses: ${stage.responses.joinToString("\n")}"
        val userContext = try {
            context.assets.open("user_context.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }

        return """
            You are an expert marketing assistant for a social media boosting service.
            
            --- User Global Context ---
            $userContext
            --- End Context ---

            Your goal is to guide a potential customer through a sales funnel based on the rules provided.
            Your reply should be in Bengali, friendly, helpful, and aim to move the conversation to the next stage.

            --- Marketing Rules ---
            $marketingRulesContent
            --- End of Rules ---

            --- Conversation History ---
            $historyText
            --- End of History ---

            Your reply:
        """.trimIndent()
    }
}

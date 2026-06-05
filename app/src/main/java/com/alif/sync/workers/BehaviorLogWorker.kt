package com.alif.sync.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alif.sync.ai.AiRepository
import com.alif.sync.util.AppUsageTracker
import com.alif.sync.models.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class AiGoalUpdate(val id: String, val newProgress: Int)

@Serializable
data class AiAnalysisResponse(val summary: String, val goalUpdates: List<AiGoalUpdate> = emptyList())

class BehaviorLogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val aiRepository = AiRepository()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val logFile = File("/storage/emulated/0/alyf/buseness/behavior_log.json")
    private val trendsFile = File("/storage/emulated/0/alyf/buseness/behavior_trends.json")

    override suspend fun doWork(): Result {
        Log.d("BehaviorLogWorker", "Starting daily behavior logging...")
        
        try {
            val usageStats = AppUsageTracker.getUsageStats(applicationContext)
            if (usageStats.isEmpty()) {
                Log.d("BehaviorLogWorker", "No usage stats found for today.")
                // Still try to check for trends even if no usage stats for today
                checkForTrends()
                return Result.success()
            }

            // Load User Goals
            val goalsFile = File("/storage/emulated/0/alyf/buseness/user_goals.json")
            val goalsData = if (goalsFile.exists()) {
                val content = goalsFile.readText()
                if (content.isBlank()) UserGoalsData() else json.decodeFromString<UserGoalsData>(content)
            } else {
                UserGoalsData()
            }

            val goalsString = if (goalsData.goals.isEmpty()) "No explicit goals set." 
            else goalsData.goals.joinToString("\n") { "- ID: ${it.id}, Title: ${it.title}, Current Progress: ${it.progress}%" }

            val usageSummary = usageStats.sortedByDescending { it.totalTimeInForeground }
                .take(15)
                .joinToString("\n") { 
                    val minutes = it.totalTimeInForeground / 60000
                    "${it.packageName}: $minutes minutes"
                }

            val userContext = try {
                applicationContext.assets.open("user_context.json").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                ""
            }

            val memoryFile = File("/storage/emulated/0/alyf/buseness/user_memory.json")
            val userMemory = if (memoryFile.exists()) memoryFile.readText() else ""

            val chatMemoryFile = File("/storage/emulated/0/alyf/buseness/aryan_chat_memory.json")
            val recentChatContext = if (chatMemoryFile.exists()) {
                val content = chatMemoryFile.readText()
                if (content.isBlank()) ""
                else {
                    @Serializable
                    data class ChatMemoryEntry(val timestamp: Long, val user: String, val aryan: String)
                    @Serializable
                    data class ChatMemory(val entries: List<ChatMemoryEntry> = emptyList())
                    
                    val memory = json.decodeFromString<ChatMemory>(content)
                    memory.entries.take(5).joinToString("\n") { 
                        "User Reasoning/Excuse: ${it.user}\nAryan Response: ${it.aryan}"
                    }
                }
            } else ""

            val prompt = """
                You are Aryan, an AI life coach. 
                Use these memory layers to understand the user's state and long-term goals.
                
                --- Static Context ---
                $userContext
                
                --- Shared Long-term Memory ---
                $userMemory
                --- End Memory ---

                --- Recent Interaction Memory (User reasoning for behavior) ---
                $recentChatContext
                --- End of Memory ---

                Compare today's behavior with their goals, current state, and their own reasoning.
                
                --- User Goals ---
                $goalsString
                --- End of Goals ---

                --- App Usage Data (Today) ---
                $usageSummary
                --- End of Data ---
                
                Provide your analysis in JSON format:
                {
                  "summary": "3-4 sentences identifying alignment, friction, and an actionable insight. Address their reasoning if provided.",
                  "goalUpdates": [
                    { "id": "goal_id", "newProgress": 75 }
                  ]
                }
                
                For each goal, update the progress (0-100) based on how much today's behavior moved them toward it. 
                Be conservative with progress increases. If behavior was counter-productive, progress might stay same or even slightly decrease.

                JSON Output:
            """.trimIndent()

            val aiResponse = aiRepository.generateContent(prompt)
            val responseText = aiResponse.text.removeSurrounding("```json", "```").trim()
            
            val analysis = try {
                json.decodeFromString<AiAnalysisResponse>(responseText)
            } catch (e: Exception) {
                Log.e("BehaviorLogWorker", "Failed to parse AI response: $responseText", e)
                AiAnalysisResponse(summary = responseText) // Fallback to raw text
            }

            saveLog(analysis.summary)
            
            // Update Goal Progress
            if (analysis.goalUpdates.isNotEmpty()) {
                val updatedGoals = goalsData.goals.map { goal ->
                    val update = analysis.goalUpdates.find { it.id == goal.id }
                    if (update != null) {
                        goal.copy(progress = update.newProgress.coerceIn(0, 100))
                    } else {
                        goal
                    }
                }
                val newGoalsData = UserGoalsData(goals = updatedGoals)
                goalsFile.writeText(json.encodeToString(UserGoalsData.serializer(), newGoalsData))
            }

            checkForTrends()

            return Result.success()
        } catch (e: Exception) {
            Log.e("BehaviorLogWorker", "Error in BehaviorLogWorker", e)
            return Result.retry()
        }
    }

    private suspend fun checkForTrends() {
        try {
            val currentTrends = if (trendsFile.exists()) {
                val content = trendsFile.readText()
                if (content.isBlank()) BehaviorTrendData() else json.decodeFromString<BehaviorTrendData>(content)
            } else {
                BehaviorTrendData()
            }

            val lastWeeklyTrend = currentTrends.trends.filter { it.type == "weekly" }.maxByOrNull { it.timestamp }
            val lastMonthlyTrend = currentTrends.trends.filter { it.type == "monthly" }.maxByOrNull { it.timestamp }

            val now = System.currentTimeMillis()
            val weekInMillis = 7 * 24 * 60 * 60 * 1000L
            val monthInMillis = 30 * 24 * 60 * 60 * 1000L

            if (lastWeeklyTrend == null || (now - lastWeeklyTrend.timestamp) >= weekInMillis) {
                generateTrend("weekly", 7)
            }

            if (lastMonthlyTrend == null || (now - lastMonthlyTrend.timestamp) >= monthInMillis) {
                generateTrend("monthly", 30)
            }
        } catch (e: Exception) {
            Log.e("BehaviorLogWorker", "Error checking for trends", e)
        }
    }

    private suspend fun generateTrend(type: String, days: Int) {
        Log.d("BehaviorLogWorker", "Generating $type trend...")
        try {
            val logsData = if (logFile.exists()) {
                val content = logFile.readText()
                if (content.isBlank()) BehaviorLogData() else json.decodeFromString<BehaviorLogData>(content)
            } else {
                return
            }

            val relevantLogs = logsData.logs.sortedByDescending { it.timestamp }.take(days)
            if (relevantLogs.isEmpty()) return

            val logsSummary = relevantLogs.joinToString("\n\n") { "Date: ${it.date}\nSummary: ${it.summary}" }
            
            val prompt = """
                You are Aryan, an AI life coach analyzing a user's behavioral trends over the last $days days ($type report).
                
                --- Daily Summaries ---
                $logsSummary
                --- End of Summaries ---
                
                Provide a high-level $type trend analysis (4-5 sentences):
                1. Patterns: Identify recurring positive or negative patterns in their behavior.
                2. Progress: Evaluate their overall progress towards their goals based on these patterns.
                3. Strategy: Suggest a concrete strategic adjustment or focus for the next $type.

                $type Analysis:
            """.trimIndent()

            val aiResponse = aiRepository.generateContent(prompt)
            val summary = aiResponse.text

            val dateRange = "${relevantLogs.last().date} to ${relevantLogs.first().date}"
            saveTrend(type, dateRange, summary)
        } catch (e: Exception) {
            Log.e("BehaviorLogWorker", "Error generating $type trend", e)
        }
    }

    private fun saveTrend(type: String, dateRange: String, summary: String) {
        try {
            val currentTrends = if (trendsFile.exists()) {
                val content = trendsFile.readText()
                if (content.isBlank()) BehaviorTrendData() else json.decodeFromString<BehaviorTrendData>(content)
            } else {
                BehaviorTrendData()
            }

            val updatedTrends = currentTrends.trends.toMutableList()
            updatedTrends.add(BehaviorTrend(type = type, dateRange = dateRange, summary = summary))
            
            // Keep only last 12 trends (e.g., 12 months or 12 weeks)
            val finalTrends = updatedTrends.sortedByDescending { it.timestamp }.take(24)

            val newData = BehaviorTrendData(trends = finalTrends)
            trendsFile.writeText(json.encodeToString(BehaviorTrendData.serializer(), newData))
            Log.d("BehaviorLogWorker", "$type trend saved.")
        } catch (e: Exception) {
            Log.e("BehaviorLogWorker", "Error saving trend", e)
        }
    }

    private fun saveLog(summary: String) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = dateFormat.format(Date())

            val currentData = if (logFile.exists()) {
                val content = logFile.readText()
                if (content.isBlank()) BehaviorLogData() else json.decodeFromString<BehaviorLogData>(content)
            } else {
                BehaviorLogData()
            }

            // Check if log for today already exists, if so, update it, else add new
            val existingLogIndex = currentData.logs.indexOfFirst { it.date == today }
            val updatedLogs = currentData.logs.toMutableList()
            
            val newLog = DailyBehaviorLog(date = today, summary = summary)
            if (existingLogIndex != -1) {
                updatedLogs[existingLogIndex] = newLog
            } else {
                updatedLogs.add(newLog)
            }

            // Keep only last 30 days
            val finalLogs = updatedLogs.sortedByDescending { it.timestamp }.take(30)

            val newData = BehaviorLogData(logs = finalLogs)
            logFile.parentFile?.let {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }
            logFile.writeText(json.encodeToString(BehaviorLogData.serializer(), newData))
            Log.d("BehaviorLogWorker", "Behavior log saved for $today")
        } catch (e: Exception) {
            Log.e("BehaviorLogWorker", "Error saving behavior log", e)
        }
    }
}

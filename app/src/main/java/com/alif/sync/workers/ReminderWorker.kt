package com.alif.sync.workers

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alif.sync.ai.AiRepository
import com.alif.sync.util.AppUsageTracker
import com.alif.sync.models.*
import kotlinx.serialization.json.Json
import java.io.File

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val aiRepository = AiRepository()
    private val json = Json { ignoreUnknownKeys = true }
    private val goalsFile = File("/storage/emulated/0/alyf/buseness/user_goals.json")
    private val logFile = File("/storage/emulated/0/alyf/buseness/behavior_log.json")
    
    // Threshold for "excessive" use (e.g., 1 hour for any app)
    private val EXCESSIVE_USE_THRESHOLD_MS = 60 * 60 * 1000L 

    override suspend fun doWork(): Result {
        Log.d("ReminderWorker", "Checking for proactive reminders...")
        
        try {
            val calendar = java.util.Calendar.getInstance()
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            
            val isMorningPlanning = hour == 8 || hour == 9
            val isEveningReflection = hour == 21 || hour == 22
            
            val todayUsage = AppUsageTracker.getTodayUsageStats(applicationContext)
            val excessiveApps = todayUsage.filter { it.totalTimeInForeground > EXCESSIVE_USE_THRESHOLD_MS }
            
            val triggerContext = when {
                isMorningPlanning -> "Morning Planning (Setting intentions for the day)"
                isEveningReflection -> "Evening Reflection (Reviewing the day's alignment)"
                excessiveApps.isNotEmpty() -> "Excessive App Usage Detected"
                else -> return Result.success() // No trigger for this 30-min window
            }

            Log.d("ReminderWorker", "Trigger context: $triggerContext")

            // Load Goals
            val goalsString = if (goalsFile.exists()) {
                val content = goalsFile.readText()
                val goalsData = if (content.isBlank()) UserGoalsData() else json.decodeFromString<UserGoalsData>(content)
                if (goalsData.goals.isEmpty()) "No explicit goals set." 
                else goalsData.goals.joinToString("\n") { "- ${it.title}: ${it.description} (${it.progress}% progress)" }
            } else {
                "No explicit goals set."
            }

            // Load Behavior Logs (Last 5 logs for more context)
            val logsString = if (logFile.exists()) {
                val content = logFile.readText()
                val logData = if (content.isBlank()) BehaviorLogData() else json.decodeFromString<BehaviorLogData>(content)
                if (logData.logs.isEmpty()) "No recent behavior logs."
                else logData.logs.take(5).joinToString("\n") { "[${it.date}]: ${it.summary}" }
            } else {
                "No recent behavior logs."
            }

            val usageSummary = if (todayUsage.isEmpty()) "No data yet today." 
            else todayUsage.sortedByDescending { it.totalTimeInForeground }.take(5).joinToString("\n") { 
                val minutes = it.totalTimeInForeground / 60000
                "${it.packageName}: $minutes minutes"
            }

            // Load Recent Chat Memory (Last 5 exchanges with Aryan)
            val memoryFile = File("/storage/emulated/0/alyf/buseness/aryan_chat_memory.json")
            val recentChatContext = if (memoryFile.exists()) {
                val content = memoryFile.readText()
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

            val userContext = try {
                applicationContext.assets.open("user_context.json").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                ""
            }

            val prompt = """
                You are Aryan, an AI life coach. You are sending a proactive message to the user.
                
                --- User Global Context (Psychology and Priorities) ---
                $userContext
                --- End Context ---

                --- Context ---
                Trigger: $triggerContext
                
                --- Recent Interaction Memory (User Reasoning/Excuses) ---
                $recentChatContext
                --- End of Memory ---
                
                --- User Goals ---
                $goalsString
                --- End of Goals ---

                --- Recent Behavior Patterns ---
                $logsString
                --- End of Logs ---

                --- Top App Usage Today ---
                $usageSummary
                --- End of Usage ---
                
                Your task is to create a message that is:
                1. Context-Aware: Acknowledge if it's morning planning, evening reflection, or a mid-day usage alert.
                2. Goal-Focused: Relate the current situation to their specific goals and progress.
                3. Actionable: Suggest one small, immediate action or reflection.
                4. Memory-Informed: If the user gave an "excuse" or "reasoning" in the recent memory, address it or use it to shape the nudge.
                5. Tone: Encouraging, firm (if needed), and supportive. No more than 3 sentences.

                Message:
            """.trimIndent()

            val aiResponse = aiRepository.generateContent(prompt)
            val reminderMessage = aiResponse.text

            sendReminderToObserver(reminderMessage)

            return Result.success()
        } catch (e: Exception) {
            Log.e("ReminderWorker", "Error in ReminderWorker", e)
            return Result.retry()
        }
    }

    private fun sendReminderToObserver(message: String) {
        val intent = Intent("com.fahim.alyfobserver.SHOW_AI_REMINDER")
        intent.putExtra("reminder_text", message)
        intent.setPackage("com.fahim.alyfobserver")
        applicationContext.sendBroadcast(intent)
        Log.d("ReminderWorker", "Sent proactive reminder to Observer: $message")
    }
}

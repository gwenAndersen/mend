package com.alif.sync.util

import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

data class ProcessInfo(
    val user: String,
    val pid: Int,
    val rss: Long, // Resident Set Size in KB
    val name: String
)

object ProcessManager {

    fun getRunningProcesses(): List<ProcessInfo> {
        val processes = mutableListOf<ProcessInfo>()

        if (!Shizuku.pingBinder()) {
            Log.e("ProcessManager", "Shizuku binder is not available.")
            return processes
        }

        try {
            // Request RSS (Resident Set Size) in addition to other fields
            // RSS is usually in Kilobytes
            val cmd = arrayOf("ps", "-A", "-o", "USER,PID,RSS,NAME")
            val process = Shizuku.newProcess(cmd, null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            
            var line: String?
            // Skip header
            reader.readLine() 

            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    val parts = it.trim().split("\\s+".toRegex())
                    // Expecting at least 4 parts: USER, PID, RSS, NAME...
                    if (parts.size >= 4) {
                        try {
                            val user = parts[0]
                            val pid = parts[1].toInt()
                            val rss = parts[2].toLong()
                            // Name might contain spaces
                            val name = parts.subList(3, parts.size).joinToString(" ")
                            processes.add(ProcessInfo(user, pid, rss, name))
                        } catch (e: NumberFormatException) {
                            // Ignore lines that don't parse correctly
                        }
                    }
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            Log.e("ProcessManager", "Error getting running processes", e)
        }
        return processes
    }

    fun forceStopPackage(packageName: String): Boolean {
         if (!Shizuku.pingBinder()) return false

        try {
            val cmd = arrayOf("am", "force-stop", packageName)
            val process = Shizuku.newProcess(cmd, null, null)
            process.waitFor()
            return process.exitValue() == 0
        } catch (e: Exception) {
            Log.e("ProcessManager", "Error force stopping package $packageName", e)
            return false
        }
    }

    fun killProcess(pid: Int): Boolean {
        if (!Shizuku.pingBinder()) return false

        try {
            val cmd = arrayOf("kill", "-9", pid.toString())
            val process = Shizuku.newProcess(cmd, null, null)
            process.waitFor()
            return process.exitValue() == 0
        } catch (e: Exception) {
            Log.e("ProcessManager", "Error killing process $pid", e)
            return false
        }
    }
}

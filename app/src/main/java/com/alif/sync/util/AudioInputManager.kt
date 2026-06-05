package com.alif.sync.util

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

data class AudioInputDeviceInfo(
    val id: Int,
    val name: String,
    val type: Int,
    val address: String
)

object AudioInputManager {

    private fun debugHelp() {
        if (!Shizuku.pingBinder()) return
        Thread {
            try {
                val commands = listOf(arrayOf("cmd", "audio"), arrayOf("cmd", "media.audio_policy"))
                for (cmd in commands) {
                    val process = Shizuku.newProcess(cmd, null, null)
                    val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
                    val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
                    Log.d("AudioInputManager", "HELP for ${cmd.joinToString(" ")}:\nOUT: $output\nERR: $error")
                }
            } catch (e: Exception) {
                Log.e("AudioInputManager", "Error getting help", e)
            }
        }.start()
    }

    fun getAudioInputDevices(context: Context): List<AudioInputDeviceInfo> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return devices.map { device ->
            val typeName = when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Phone Mic"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired"
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
                else -> "External"
            }
            AudioInputDeviceInfo(
                id = device.id,
                name = "${device.productName} ($typeName) #${device.id}",
                type = device.type,
                address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) device.address ?: "" else ""
            )
        }
    }

    fun setPreferredInputDevice(context: Context, deviceId: Int): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val targetDevice = devices.find { it.id == deviceId }

        return if (targetDevice != null) {
            try {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val result = audioManager.setCommunicationDevice(targetDevice)
                    Log.d("AudioInputManager", "Set communication device to ${targetDevice.productName}: $result")
                    result
                } else {
                    @Suppress("DEPRECATION")
                    if (targetDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                        audioManager.startBluetoothSco()
                        audioManager.isBluetoothScoOn = true
                        true
                    } else {
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioInputManager", "Error setting communication device", e)
                false
            }
        } else false
    }

    fun forceSetAudioInput(context: Context, deviceId: Int): Boolean {
        debugHelp()
        if (!Shizuku.pingBinder()) return setPreferredInputDevice(context, deviceId)

        val devices = getAudioInputDevices(context)
        val targetDevice = devices.find { it.id == deviceId } ?: return false

        val deviceTypeHex = when (targetDevice.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "0x80000008"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "0x80000010"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "0x80000800"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "0x80004000"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "0x80000004"
            else -> null
        }

        try {
            // 1. Connection state
            if (deviceTypeHex != null) {
                val connectCmd = arrayOf("cmd", "media.audio_policy", "set-device-connection-state", deviceTypeHex, "1", targetDevice.address)
                Log.d("AudioInputManager", "Executing: ${connectCmd.joinToString(" ")}")
                Shizuku.newProcess(connectCmd, null, null).waitFor()
            }

            // 2. Aggressive Force Use
            val forceUseType = when (targetDevice.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "3"
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "1"
                else -> "0"
            }

            Shizuku.newProcess(arrayOf("cmd", "media.audio_policy", "set-force-use", "1", forceUseType), null, null).waitFor()
            Shizuku.newProcess(arrayOf("cmd", "media.audio_policy", "set-force-use", "0", forceUseType), null, null).waitFor()
            
            // 3. Final hint
            setPreferredInputDevice(context, deviceId)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun getCurrentInputDeviceName(context: Context): String {
        if (Shizuku.pingBinder()) {
            try {
                val process = Shizuku.newProcess(arrayOf("dumpsys", "audio"), null, null)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                var btName = ""
                var activeDevices = ""
                
                while (reader.readLine().also { line = it } != null) {
                    val l = line!!.trim()
                    if (l.startsWith("mBluetoothName=", ignoreCase = true)) btName = l.substringAfter("=")
                    if (l.contains("Devices:", ignoreCase = true) && !l.contains("Devices: none")) {
                         activeDevices = l.substringAfter("Devices:").trim()
                    }
                }
                if (activeDevices.isNotEmpty()) return activeDevices + (if (btName.isNotEmpty()) " ($btName)" else "")
            } catch (e: Exception) {}
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = audioManager.communicationDevice
            if (device != null) return "${device.productName} #${device.id}"
        }
        return "Internal/Default"
    }
}

package com.alif.sync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alif.sync.util.AudioInputDeviceInfo
import com.alif.sync.util.AudioInputManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf(emptyList<AudioInputDeviceInfo>()) }
    var currentDeviceName by remember { mutableStateOf("Detecting...") }
    var isRefreshing by remember { mutableStateOf(false) }

    fun refresh() {
        isRefreshing = true
        devices = AudioInputManager.getAudioInputDevices(context)
        currentDeviceName = AudioInputManager.getCurrentInputDeviceName(context)
        isRefreshing = false
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Microphone Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("System Active Input:", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = currentDeviceName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Available Sources",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices) { device ->
                    DeviceItem(
                        device = device,
                        isActive = currentDeviceName.contains(device.name.substringBefore(" #")),
                        onSelect = {
                            AudioInputManager.forceSetAudioInput(context, device.id)
                            refresh()
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "Note: Shizuku is used to force system-wide routing. Changes might take a few seconds to reflect in dumpsys.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun DeviceItem(
    device: AudioInputDeviceInfo,
    isActive: Boolean,
    onSelect: () -> Unit
) {
    OutlinedCard(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        border = if (isActive) CardDefaults.outlinedCardBorder().copy(width = 2.dp) else CardDefaults.outlinedCardBorder(),
        colors = if (isActive) CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)) else CardDefaults.outlinedCardColors()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "ID: ${device.id} | Address: ${if (device.address.isEmpty()) "Internal" else device.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

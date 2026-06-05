package com.alif.sync.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alif.sync.util.ProcessInfo
import com.alif.sync.util.ProcessManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessListScreen(onBack: () -> Unit) {
    var allProcesses by remember { mutableStateOf<List<ProcessInfo>>(emptyList()) }
    var displayedProcesses by remember { mutableStateOf<List<ProcessInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    var userFilter by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.MEMORY) }
    
    val scope = rememberCoroutineScope()

    // Load processes
    val refreshProcesses = {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                val rawList = ProcessManager.getRunningProcesses()
                allProcesses = rawList
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshProcesses()
    }

    // Filter and Sort logic
    LaunchedEffect(allProcesses, userFilter, sortOption) {
        var list = allProcesses
        
        // Filter
        if (userFilter.isNotBlank()) {
            list = list.filter { 
                it.user.contains(userFilter, ignoreCase = true) || 
                it.name.contains(userFilter, ignoreCase = true)
            }
        }
        
        // Sort
        list = when (sortOption) {
            SortOption.MEMORY -> list.sortedByDescending { it.rss }
            SortOption.NAME -> list.sortedBy { it.name }
            SortOption.PID -> list.sortedBy { it.pid }
        }
        
        displayedProcesses = list
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with Back and Refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = onBack) {
                Text("Back")
            }
            Text("Process Manager", fontSize = 20.sp)
            Button(onClick = { refreshProcesses() }) {
                Text("Refresh")
            }
        }

        // Controls: Filter and Sort
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                OutlinedTextField(
                    value = userFilter,
                    onValueChange = { userFilter = it },
                    label = { Text("Filter by User or Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Sort by:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SortChip(
                        label = "Memory", 
                        selected = sortOption == SortOption.MEMORY, 
                        onClick = { sortOption = SortOption.MEMORY }
                    )
                    SortChip(
                        label = "Name", 
                        selected = sortOption == SortOption.NAME, 
                        onClick = { sortOption = SortOption.NAME }
                    )
                    SortChip(
                        label = "PID", 
                        selected = sortOption == SortOption.PID, 
                        onClick = { sortOption = SortOption.PID }
                    )
                }
            }
        }
        
        // Status Bar
        Text(
            text = "Showing ${displayedProcesses.size} processes",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall
        )

        if (isLoading && allProcesses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(displayedProcesses, key = { it.pid }) { process ->
                    ProcessItem(process = process, onRefresh = { refreshProcesses() })
                }
            }
        }
    }
}

@Composable
fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else null
    )
}

enum class SortOption {
    MEMORY, NAME, PID
}

@Composable
fun ProcessItem(process: ProcessInfo, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSystemProcess = process.user == "system"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = process.name, 
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatMemory(process.rss),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Text(
                text = "PID: ${process.pid} | User: ${process.user}", 
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (isSystemProcess) {
                     Text(
                        text = "Protected System Process",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    if (process.name.contains(".")) {
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val success = ProcessManager.forceStopPackage(process.name)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, if (success) "Force Stopped ${process.name}" else "Failed to stop", Toast.LENGTH_SHORT).show()
                                        onRefresh()
                                    }
                                }
                            },
                            modifier = Modifier.padding(end = 8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("Force Stop")
                        }
                    }
                    
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val success = ProcessManager.killProcess(process.pid)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, if (success) "Killed PID ${process.pid}" else "Failed to kill", Toast.LENGTH_SHORT).show()
                                    onRefresh()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("Kill")
                    }
                }
            }
        }
    }
}

fun formatMemory(kb: Long): String {
    return if (kb > 1024) {
        String.format(Locale.US, "%.1f MB", kb / 1024.0)
    } else {
        "$kb KB"
    }
}
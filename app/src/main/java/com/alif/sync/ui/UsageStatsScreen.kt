package com.alif.sync.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alif.sync.util.UsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UsageStatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var usageList by remember { mutableStateOf<List<UsageStatsHelper.AppUsageInfo>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(UsageStatsHelper.hasUsageStatsPermission(context)) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            withContext(Dispatchers.IO) {
                val list = UsageStatsHelper.getUsageStats(context)
                withContext(Dispatchers.Main) {
                    usageList = list
                    isLoading = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = onBack) { Text("Back") }
            Text("App Usage (24h)", fontSize = 20.sp)
            if (hasPermission) {
                Button(onClick = { 
                    scope.launch {
                        isLoading = true
                        val list = withContext(Dispatchers.IO) { UsageStatsHelper.getUsageStats(context) }
                        usageList = list
                        isLoading = false
                    }
                }) { Text("Refresh") }
            }
        }

        if (!hasPermission) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Usage Access Permission Required")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        scope.launch {
                            val success = withContext(Dispatchers.IO) {
                                UsageStatsHelper.grantUsageStatsPermission(context)
                            }
                            if (success) {
                                Toast.makeText(context, "Permission Granted via Shizuku", Toast.LENGTH_SHORT).show()
                                hasPermission = UsageStatsHelper.hasUsageStatsPermission(context)
                            } else {
                                Toast.makeText(context, "Failed to grant permission via Shizuku", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text("Grant via Shizuku")
                    }
                }
            }
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(usageList) { app ->
                    AppUsageItem(app)
                }
            }
        }
    }
}

@Composable
fun AppUsageItem(app: UsageStatsHelper.AppUsageInfo) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            app.icon?.let { drawable ->
                val bitmap = drawableToBitmap(drawable)
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(app.appName, style = MaterialTheme.typography.titleMedium)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                Text("Time: ${formatDuration(app.totalTimeInForeground)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text("Last Used: ${formatDate(app.lastTimeUsed)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return if (hours > 0) "${hours}h ${minutes % 60}m" else "${minutes}m ${seconds % 60}s"
}

fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        return drawable.bitmap
    }
    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth.coerceAtLeast(1),
        drawable.intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

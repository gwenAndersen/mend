package alyf.brief

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import alyf.brief.ui.theme.NewAndroidProjectTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NewAndroidProjectTheme(dynamicColor = false) {
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    floatingActionButton = {
                        FloatingActionButton(onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Hello, there!")
                            }
                        }) {
                            Icon(Icons.Filled.Star, contentDescription = "Example Button")
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // Your content here
                    }
                }
            }
        }
    }
}
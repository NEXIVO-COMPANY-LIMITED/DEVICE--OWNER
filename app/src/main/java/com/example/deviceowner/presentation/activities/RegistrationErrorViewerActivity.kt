package com.example.deviceowner.presentation.activities

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import com.example.deviceowner.utils.RegistrationErrorLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Lists registration error HTML files in DeviceOwner/RegistrationErrors and opens them on the phone.
 * Helps Device Owner / support understand what caused the error and how to fix it.
 */
class RegistrationErrorViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeviceOwnerTheme {
                RegistrationErrorViewerScreen(
                    onBack = { finish() }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RegistrationErrorViewerScreen(onBack: () -> Unit) {
        var errorFiles by remember { mutableStateOf<List<File>>(emptyList()) }
        var selectedFile by remember { mutableStateOf<File?>(null) }
        var folderPath by remember { mutableStateOf("") }
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            errorFiles = RegistrationErrorLogger.listErrorFiles(context)
            folderPath = RegistrationErrorLogger.getErrorFolderPath(context)
        }

        if (selectedFile != null) {
            // Show HTML in WebView
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(selectedFile!!.name) },
                    navigationIcon = {
                        IconButton(onClick = { selectedFile = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to list")
                        }
                    }
                )
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = false
                            settings.domStorageEnabled = false
                        }
                    },
                    update = { webView ->
                        selectedFile?.let { file ->
                            if (file.exists()) {
                                val html = file.readText(Charsets.UTF_8)
                                webView.loadDataWithBaseURL(
                                    "file:///",
                                    html,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Registration errors") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Folder: $folderPath",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (errorFiles.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No registration errors",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Errors from registration (e.g. ClassCastException) are saved here as HTML with cause and how to fix.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Tap an error to view (what caused it and how to fix):",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(errorFiles) { file ->
                                RegistrationErrorItem(
                                    file = file,
                                    onClick = { selectedFile = file }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RegistrationErrorItem(file: File, onClick: () -> Unit) {
        val dateStr = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(file.lastModified()))
        } catch (_: Exception) {
            file.name
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.nameWithoutExtension.replace("registration_error_", "Error "),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = dateStr,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

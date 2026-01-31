package com.example.deviceowner.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.utils.logging.LogManager
import com.example.deviceowner.utils.logging.LogViewer

/**
 * Activity for viewing application logs
 */
class LogViewerActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                LogViewerScreen()
            }
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LogViewerScreen() {
        var selectedCategory by remember { mutableStateOf<LogManager.LogCategory?>(null) }
        var showSummary by remember { mutableStateOf(true) }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Device Owner Logs") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            LogViewer.shareLogs(this@LogViewerActivity, selectedCategory)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share Logs")
                        }
                        IconButton(onClick = { 
                            showSummary = !showSummary
                            selectedCategory = null
                        }) {
                            Icon(Icons.Default.Info, contentDescription = "Summary")
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
                if (showSummary) {
                    SummaryView()
                } else if (selectedCategory != null) {
                    LogContentView(selectedCategory!!)
                } else {
                    CategoryListView { category ->
                        selectedCategory = category
                        showSummary = false
                    }
                }
            }
        }
    }
    
    @Composable
    fun SummaryView() {
        val summary = remember { LogViewer.getLogSummary() }
        val statistics = remember { LogViewer.getLogStatistics() }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Log Statistics",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    val stats = statistics as Map<String, Any>
                    Text("Total Files: ${stats["totalFiles"]}")
                    Text("Total Size: ${stats["totalSize"]}")
                    Text("Directory: ${stats["logDirectory"]}")
                    Text("Generated: ${stats["generatedAt"]}")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Full Summary",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Text(
                        text = summary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    )
                }
            }
        }
    }
    
    @Composable
    fun CategoryListView(onCategorySelected: (LogManager.LogCategory) -> Unit) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LogManager.LogCategory.values()) { category ->
                val files = LogManager.getLogFiles(category)
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = { onCategorySelected(category) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = category.name.replace("_", " "),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${files.size} files",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                        
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "View logs",
                            tint = Color.Gray
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    fun LogContentView(category: LogManager.LogCategory) {
        val logContent = remember(category) { 
            LogViewer.getFormattedLogContent(category, 500) 
        }
        
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category.name.replace("_", " "),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Button(
                    onClick = { LogViewer.shareLogs(this@LogViewerActivity, category) }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share")
                }
            }
            
            Card(
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = logContent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }
        }
    }
}
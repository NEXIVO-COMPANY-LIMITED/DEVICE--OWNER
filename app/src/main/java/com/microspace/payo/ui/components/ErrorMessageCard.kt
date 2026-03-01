package com.microspace.payo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Customer-friendly error message card for registration form
 */
@Composable
fun ErrorMessageCard(
    message: String,
    suggestion: String? = null,
    isRetryable: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFEBEE)  // Light red background
        ),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            Color(0xFFEF5350)  // Red border
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main message with icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = "Error",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(28.dp)
                )
                
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color(0xFFD32F2F)
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Suggestion with steps
            if (!suggestion.isNullOrEmpty()) {
                Divider(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFEF5350).copy(alpha = 0.2f),
                    thickness = 1.dp
                )
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "What to do:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF424242)
                        )
                    )
                    
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF616161)
                        )
                    )
                }
            }
            
            // Retry hint
            if (isRetryable) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "ðŸ’¡",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Check your internet connection and try again",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFE65100)
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Success message card for registration form
 */
@Composable
fun SuccessMessageCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFC8E6C9)  // Light green background
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color(0xFF4CAF50)  // Green border
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "Success",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(24.dp)
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF1B5E20)
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}





package com.example.deviceowner.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class WelcomeActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WelcomeScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen() {
    // Infinite animation for gradient
    val infiniteTransition = rememberInfiniteTransition(label = "gradient")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientOffset"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF667EEA),
                        Color(0xFF764BA2),
                        Color(0xFF667EEA)
                    ),
                    startY = gradientOffset * 800,
                    endY = (gradientOffset + 1) * 800
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            // Welcome Icon with Animation
            WelcomeIcon()
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Welcome Text
            Text(
                text = "Device Registration Disabled",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "All device registration functionality has been removed",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Main Content Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(32.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // Status Card
                    DisabledStatusCard()
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Security Features
                    SecurityFeaturesCard()
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Footer Message
            Text(
                text = "Device registration, data collection, and\nserver communication have been removed",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun WelcomeIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "icon")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.1f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Block,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )
    }
}

@Composable
fun DisabledStatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFFFF9800).copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column {
                    Text(
                        text = "Registration Disabled",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        text = "Status: Functionality Removed",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

@Composable
fun SecurityFeaturesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFF4F46E5),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Security Features",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            }
            
            SecurityFeatureItem(
                icon = Icons.Default.Shield,
                title = "Enterprise Protection",
                description = "Basic restrictions active",
                color = Color(0xFF10B981)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            SecurityFeatureItem(
                icon = Icons.Default.Lock,
                title = "Device Lock Policy",
                description = "Security restrictions only",
                color = Color(0xFF10B981)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            SecurityFeatureItem(
                icon = Icons.Default.Block,
                title = "Data Collection",
                description = "Completely disabled",
                color = Color(0xFFEF4444)
            )
        }
    }
}

@Composable
fun SecurityFeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E293B)
            )
            Text(
                text = description,
                fontSize = 13.sp,
                color = Color(0xFF64748B)
            )
        }
        Icon(
            imageVector = if (color == Color(0xFFEF4444)) Icons.Default.Block else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
    }
}
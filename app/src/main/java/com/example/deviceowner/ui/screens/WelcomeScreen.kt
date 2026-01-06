package com.example.deviceowner.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.example.deviceowner.R

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    // Entrance animation (fade + slide)
    val entrance by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 700,
            easing = FastOutSlowInEasing
        ),
        label = "entranceAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    alpha = entrance
                    translationY = (1f - entrance) * 40f
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Spacer(modifier = Modifier.height(16.dp))

            // ===================== CONTENT =====================
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                // App Logo
                Surface(
                    shape = CircleShape,
                    shadowElevation = 12.dp,
                    modifier = Modifier.size(140.dp),
                    color = colors.primary
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .background(Color.White)
                            .padding(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.nexivo),
                            contentDescription = "Nexivo Logo",
                            modifier = Modifier.size(110.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(44.dp))

                // Title
                Text(
                    text = "Welcome to Sponsor",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        fontSize = 38.sp
                    ),
                    color = colors.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Subtitle
                Text(
                    text = "Flexible financing solutions for computers, laptops, and electronic devices",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Features
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SponsorFeatureItem(
                        title = "Device Financing",
                        description = "Fast and easy loans for computers and electronics"
                    )
                    SponsorFeatureItem(
                        title = "Flexible Payments",
                        description = "Customized repayment plans that fit your income"
                    )
                    SponsorFeatureItem(
                        title = "Secure & Trusted",
                        description = "Fraud-protected, transparent approval process"
                    )
                }
            }

            // ===================== BOTTOM =====================
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        "Get Started",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Authorized agents only • Secure access",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onBackground.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SponsorFeatureItem(
    title: String,
    description: String
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(colors.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
                fontSize = 16.sp
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onBackground.copy(alpha = 0.7f),
                lineHeight = 20.sp,
                fontSize = 14.sp
            )
        }
    }
}

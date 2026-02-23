package com.example.deviceowner.ui.activities.status

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.config.FrpConfig
import com.example.deviceowner.core.frp.manager.FrpManager
import com.example.deviceowner.core.frp.manager.FrpStatus

/**
 * FRP Status Activity - Enterprise Factory Reset Protection status dashboard.
 * Shows activation countdown, account info, and Google Play Services status.
 * 
 * NOTE: FRP code is currently not in use. Commenting out for future reference.
 */
/*
class FrpStatusActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FrpStatusScreen()
            }
        }
    }
}
*/

/*
// NOTE: FRP code is currently not in use. Commenting out for future reference.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrpStatusScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val frpManager = remember { FrpManager(context) }
    var status by remember { mutableStateOf(frpManager.getFrpStatus()) }

    LaunchedEffect(Unit) {
        status = frpManager.getFrpStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FRP Status", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            StatusCard(
                title = "FRP Protection",
                subtitle = if (status.enabled) "Active" else "Not configured",
                icon = Icons.Default.Security,
                isSuccess = status.enabled
            )

            Spacer(modifier = Modifier.height(12.dp))

            StatusCard(
                title = "72-Hour Activation",
                subtitle = if (status.fullyActivated) {
                    "✓ Fully activated"
                } else if (status.enabled) {
                    "${status.hoursRemaining} hours remaining"
                } else {
                    "Not started"
                },
                icon = Icons.Default.Schedule,
                isSuccess = status.fullyActivated
            )

            Spacer(modifier = Modifier.height(12.dp))

            StatusCard(
                title = "Company Account",
                subtitle = FrpConfig.COMPANY_FRP_ACCOUNT_ID.take(8) + "***",
                icon = Icons.Default.AccountCircle,
                isSuccess = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            StatusCard(
                title = "Google Play Services",
                subtitle = if (status.gmsAvailable) "Available" else "Not available",
                icon = Icons.Default.Android,
                isSuccess = status.gmsAvailable
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Enterprise FRP protects this device after factory reset. " +
                        "Only the company account (${FrpConfig.COMPANY_FRP_ACCOUNT_ID.take(6)}***) can unlock.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
*/

/*
// NOTE: FRP code is currently not in use. Commenting out for future reference.
@Composable
private fun StatusCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSuccess: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSuccess)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSuccess) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
*/

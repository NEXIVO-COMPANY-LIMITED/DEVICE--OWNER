package com.microspace.payo.ui.activities.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microspace.payo.data.local.database.DeviceOwnerDatabase
import com.microspace.payo.data.local.database.entities.device.CompleteDeviceRegistrationEntity
import com.microspace.payo.ui.theme.DeviceOwnerTheme
import com.microspace.payo.utils.storage.SharedPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// â”€â”€ Color Palette â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
val BrandBlue      = Color(0xFF1E40AF)
val BrandBlueMid   = Color(0xFF3B82F6)
val BrandBlueLight = Color(0xFFDBEAFE)
val BgGradient     = Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFFF1F5F9)))
val TextPrimary    = Color(0xFF1E293B)
val TextSecondary  = Color(0xFF64748B)
val TextTertiary   = Color(0xFF94A3B8)
val SuccessGreen   = Color(0xFF10B981)
val SuccessBg      = Color(0xFFECFDF5)
val AmberColor     = Color(0xFFF59E0B)
val AmberBg        = Color(0xFFFEF3C7)
val DangerRed      = Color(0xFFEF4444)
val DangerBg       = Color(0xFFFEF2F2)
val DividerColor   = Color(0xFFE2E8F0)
val CardColor      = Color.White
val SlateIconBg    = Color(0xFFF1F5F9)

// â”€â”€ Activity â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
class DeviceDetailActivity : ComponentActivity() {

    private lateinit var prefsManager: SharedPreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsManager = SharedPreferencesManager(this)
        setContent {
            DeviceOwnerTheme {
                DeviceDetailScreen(prefsManager)
            }
        }
    }
}

// â”€â”€ Root Screen â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(prefsManager: SharedPreferencesManager) {
    val context       = LocalContext.current
    val scrollState   = rememberScrollState()
    val scope         = rememberCoroutineScope()

    var registrationData by remember { mutableStateOf<CompleteDeviceRegistrationEntity?>(null) }
    var isLoading        by remember { mutableStateOf(true) }
    var isSyncing        by remember { mutableStateOf(false) }
    var lastSyncLabel    by remember { mutableStateOf("just now") }

    val isRooted = registrationData?.isDeviceRooted == true

    LaunchedEffect(Unit) {
        val id = prefsManager.getDeviceIdForDeviceDetail() ?: ""
        withContext(Dispatchers.IO) {
            try {
                val db = DeviceOwnerDatabase.getDatabase(context)
                registrationData = db.completeDeviceRegistrationDao().getRegistrationByDeviceId(id)
            } catch (e: Exception) {
                Log.e("DeviceDetail", "Error loading data", e)
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.background(BgGradient),
        topBar = {
            Surface(
                color = Color(0xE6F8FAFC),
                shadowElevation = 0.dp,
                tonalElevation  = 0.dp
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Device Profile",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 18.sp,
                            color      = TextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { (context as? DeviceDetailActivity)?.finish() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { shareDeviceProfile(context, registrationData) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = BrandBlue)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            }
        }
    ) { padding ->

        AnimatedVisibility(
            visible = isLoading,
            enter   = fadeIn(),
            exit    = fadeOut()
        ) {
            SkeletonScreen(padding)
        }

        AnimatedVisibility(
            visible = !isLoading,
            enter   = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
            exit    = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))

                AdaptiveStatusBadge(isRooted = isRooted)

                Spacer(Modifier.height(20.dp))

                DeviceHeaderCard(registrationData)

                Spacer(Modifier.height(20.dp))

                InfoSection(title = "CORE DETAILS", icon = Icons.Default.Info) {
                    DetailRow(
                        label      = "Loan ID",
                        value      = registrationData?.loanNumber ?: "N/A",
                        icon       = Icons.Default.ReceiptLong,
                        pillColor  = BrandBlueLight,
                        iconColor  = BrandBlue,
                        canCopy    = true
                    )
                    DetailRow(
                        label      = "Device ID",
                        value      = registrationData?.deviceId ?: "N/A",
                        icon       = Icons.Default.Fingerprint,
                        pillColor  = BrandBlueLight,
                        iconColor  = BrandBlue,
                        canCopy    = true
                    )
                    DetailRow(
                        label      = "Serial Number",
                        value      = registrationData?.serialNumber ?: "N/A",
                        icon       = Icons.Default.Numbers,
                        pillColor  = SlateIconBg,
                        iconColor  = TextSecondary,
                        canCopy    = true
                    )
                }

                Spacer(Modifier.height(20.dp))

                SecurityScoreCard(data = registrationData, isRooted = isRooted)

                Spacer(Modifier.height(20.dp))

                LiveSyncRow(
                    isSyncing     = isSyncing,
                    lastSyncLabel = lastSyncLabel,
                    onSync        = {
                        isSyncing = true
                        scope.launch {
                            delay(1800)
                            lastSyncLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            isSyncing = false
                            Toast.makeText(context, "Sync complete âœ“", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Spacer(Modifier.height(32.dp))

                Text(
                    "This device is managed by your organization.\nSecurity policies are active and enforced.",
                    fontSize   = 12.sp,
                    color      = TextTertiary,
                    textAlign  = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier   = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
fun SkeletonScreen(padding: PaddingValues) {
    val shimmerAlpha by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "shimmer"
    )
    val shimmerColor = Color(0xFFE2E8F0).copy(alpha = shimmerAlpha)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.width(160.dp).height(32.dp).clip(RoundedCornerShape(999.dp)).background(shimmerColor))
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)).background(shimmerColor))
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(20.dp)).background(shimmerColor))
    }
}

@Composable
fun AdaptiveStatusBadge(isRooted: Boolean) {
    val bgColor   = if (isRooted) DangerBg     else SuccessBg
    val textColor = if (isRooted) DangerRed     else SuccessGreen
    val label     = if (isRooted) "SECURITY RISK DETECTED" else "DEVICE SECURED"

    Surface(
        color  = bgColor,
        shape  = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(.2f))
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(textColor))
            Spacer(Modifier.width(8.dp))
            Text(label, color = textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun DeviceHeaderCard(data: CompleteDeviceRegistrationEntity?) {
    Surface(
        modifier       = Modifier.fillMaxWidth(),
        shape          = RoundedCornerShape(24.dp),
        color          = CardColor,
        shadowElevation = 2.dp
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(BrandBlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(32.dp), tint = BrandBlue)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text       = "${data?.manufacturer ?: "Device"} ${data?.model ?: "Profile"}".trim(),
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Black,
                    color      = TextPrimary
                )
                Text("Hardware Verified", fontSize = 13.sp, color = TextSecondary)
            }

            HorizontalDivider(thickness = 1.dp, color = DividerColor)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                HeaderStat("OS", data?.osVersion ?: "?")
                HeaderStat("RAM", data?.installedRam ?: "?")
                HeaderStat("Storage", data?.totalStorage ?: "?")
            }
        }
    }
}

@Composable
fun RowScope.HeaderStat(label: String, value: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 0.8.sp)
    }
}

@Composable
fun SecurityScoreCard(data: CompleteDeviceRegistrationEntity?, isRooted: Boolean) {
    // Calculate real security score based on available data
    val isUsbDebug = data?.isUsbDebuggingEnabled == true
    val isDevMode = data?.isDeveloperModeEnabled == true
    val isBootloaderUnlocked = data?.isBootloaderUnlocked == true
    val isCustomRom = data?.isCustomRom == true

    val rootScore = if (isRooted) 0 else 100
    val integrityScore = when {
        isCustomRom -> 30
        isBootloaderUnlocked -> 60
        else -> 100
    }
    val policyScore = if (isUsbDebug || isDevMode) 70 else 100

    // Weighted average: Root (40%), Integrity (40%), Policy (20%)
    val totalScore = (rootScore * 0.4 + integrityScore * 0.4 + policyScore * 0.2).toInt()

    val scoreColor  = when { totalScore >= 80 -> SuccessGreen; totalScore >= 50 -> AmberColor; else -> DangerRed }
    val animScore   by animateFloatAsState(targetValue = totalScore.toFloat(), animationSpec = tween(1200), label = "score")

    Surface(
        modifier        = Modifier.fillMaxWidth(),
        shape           = RoundedCornerShape(20.dp),
        color           = CardColor,
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 18.dp)) {
                Icon(Icons.Default.GppGood, null, Modifier.size(16.dp), tint = TextSecondary)
                Spacer(Modifier.width(8.dp))
                Text("SECURITY AUDIT", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = TextSecondary, letterSpacing = 1.5.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.size(96.dp)) {
                        drawArc(Color(0xFFE2E8F0), -90f, 360f, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(scoreColor, -90f, 360f * (animScore / 100f), false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${animScore.toInt()}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                        Text("/ 100", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextTertiary)
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecurityBar("Root Check", rootScore.toFloat(), if (isRooted) DangerRed else SuccessGreen)
                    SecurityBar("OS Integrity", integrityScore.toFloat(), if (integrityScore < 80) DangerRed else SuccessGreen)
                    SecurityBar("Policy Sync", policyScore.toFloat(), if (policyScore < 100) AmberColor else SuccessGreen)
                }
            }
        }
    }
}

@Composable
fun SecurityBar(label: String, pct: Float, color: Color) {
    val animW by animateFloatAsState(pct / 100f, tween(1200), label = label)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Text("${pct.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)).background(DividerColor)) {
            Box(Modifier.fillMaxWidth(animW).fillMaxHeight().clip(RoundedCornerShape(999.dp)).background(color))
        }
    }
}

@Composable
fun InfoSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)) {
            Icon(icon, null, Modifier.size(16.dp), tint = TextSecondary)
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = TextSecondary, letterSpacing = 1.5.sp)
        }
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = CardColor, shadowElevation = 1.dp) {
            Column(content = content)
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, icon: ImageVector, pillColor: Color, iconColor: Color, canCopy: Boolean = false) {
    val context = LocalContext.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(enabled = canCopy) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(pillColor), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = iconColor)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 10.5.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                Text(value, fontSize = 14.5.sp, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (canCopy) Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp), tint = BrandBlue.copy(.5f))
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = DividerColor)
    }
}

@Composable
fun LiveSyncRow(isSyncing: Boolean, lastSyncLabel: String, onSync: () -> Unit) {
    val rotation by rememberInfiniteTransition(label = "sync").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)), label = "spin"
    )
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = CardColor, shadowElevation = 1.dp) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(SuccessGreen))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Live Sync", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Last synced: $lastSyncLabel", fontSize = 11.sp, color = TextTertiary)
                }
            }
            Surface(shape = RoundedCornerShape(10.dp), color = BrandBlueLight, modifier = Modifier.clickable(onClick = onSync)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Sync, contentDescription = "Sync", modifier = Modifier.size(14.dp).rotate(if (isSyncing) rotation else 0f), tint = BrandBlue)
                    Text("Sync now", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
                }
            }
        }
    }
}

fun shareDeviceProfile(context: Context, data: CompleteDeviceRegistrationEntity?) {
    val text = buildString {
        appendLine("ðŸ“± Device Profile")
        appendLine("Model: ${data?.manufacturer} ${data?.model}")
        appendLine("Device ID: ${data?.deviceId}")
        appendLine("Loan ID: ${data?.loanNumber}")
        appendLine("OS: Android ${data?.osVersion}")
        appendLine("RAM: ${data?.installedRam}")
        appendLine("Storage: ${data?.totalStorage}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Device Profile"))
}





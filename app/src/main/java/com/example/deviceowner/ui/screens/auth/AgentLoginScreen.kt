package com.example.deviceowner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.R
import com.deviceowner.utils.ValidationUtils
import com.deviceowner.utils.ErrorHandler
import kotlinx.coroutines.launch

@Composable
fun AgentLoginScreen(
    onLoginSuccess: (loanId: String) -> Unit,
    onBackToWelcome: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var loanId by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {

        /* ===== BACK BUTTON (FIXED TOP) ===== */
        IconButton(
            onClick = onBackToWelcome,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = colors.primary
            )
        }

        /* ===== CENTER CONTENT ===== */
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            /* ===== LOGO ===== */
            Surface(
                shape = CircleShape,
                shadowElevation = 8.dp,
                color = colors.primary
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier
                        .size(110.dp)
                        .padding(8.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.nexivo),
                        contentDescription = "Nexivo Logo",
                        modifier = Modifier
                            .size(94.dp)
                            .padding(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            /* ===== TITLE ===== */
            Text(
                text = "Welcome Agent",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Fill in your information correctly",
                fontSize = 14.sp,
                color = colors.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            /* ===== FORM ===== */
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {

                LoginField(
                    value = loanId,
                    onValueChange = {
                        loanId = it
                        error = null
                    },
                    label = "Loan ID",
                    icon = Icons.Default.CreditCard,
                    imeAction = ImeAction.Done,
                    focusManager = focusManager,
                    onDone = {
                        attemptLogin(
                            loanId,
                            scope,
                            { isLoading = it },
                            { error = it },
                            { onLoginSuccess(loanId) }
                        )
                    }
                )

                AnimatedVisibility(error != null) {
                    Text(
                        text = error ?: "",
                        color = colors.error,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        attemptLogin(
                            loanId,
                            scope,
                            { isLoading = it },
                            { error = it },
                            { onLoginSuccess(loanId) }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            "Next",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        /* ===== FOOTER ===== */
        Text(
            text = "Authorized agents only • Secure access",
            fontSize = 12.sp,
            color = colors.onBackground.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    imeAction: ImeAction,
    focusManager: androidx.compose.ui.focus.FocusManager,
    onDone: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme

    Column {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onBackground
        )

        Spacer(Modifier.height(6.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            leadingIcon = {
                Icon(icon, contentDescription = null, tint = colors.onSurface)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(color = colors.onSurface),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = {
                    focusManager.clearFocus()
                    onDone?.invoke()
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.onSurface.copy(alpha = 0.5f),
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                cursorColor = colors.primary
            )
        )
    }
}

private fun attemptLogin(
    loanId: String,
    scope: kotlinx.coroutines.CoroutineScope,
    setLoading: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    onSuccess: () -> Unit
) {
    // Validate using utilities
    val loanIdValidation = ValidationUtils.validateLoanId(loanId)

    when {
        !loanIdValidation.first -> {
            setError(ErrorHandler.getErrorMessage(loanIdValidation.second))
            return
        }
    }

    scope.launch {
        setLoading(true)
        setError(null)

        // Simulate login delay
        kotlinx.coroutines.delay(500)

        setLoading(false)
        onSuccess()
    }
}

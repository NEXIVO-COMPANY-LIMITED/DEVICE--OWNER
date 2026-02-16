package com.example.deviceowner.presentation.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.deviceowner.data.repository.DeviceRegistrationRepository
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deviceowner.R
import com.example.deviceowner.ui.theme.DeviceOwnerTheme
import com.example.deviceowner.utils.helpers.LoanNumberValidator

class DeviceRegistrationActivity : ComponentActivity() {

    private lateinit var registrationRepository: DeviceRegistrationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registrationRepository = DeviceRegistrationRepository(this)
        setContent {
            DeviceOwnerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DeviceRegistrationScreen(
                        onLoanNumberSubmitted = { loanNumber ->
                            saveAndNavigateToDataCollection(loanNumber)
                        }
                    )
                }
            }
        }
    }

    private fun saveAndNavigateToDataCollection(loanNumber: String) {
        val trimmed = loanNumber.trim()
        lifecycleScope.launch {
            registrationRepository.saveLoanNumberForRegistration(trimmed)
            navigateToDataCollection(trimmed)
        }
    }

    private fun navigateToDataCollection(loanNumber: String) {
        val intent = Intent(
            this,
            com.example.deviceowner.ui.activities.data.DeviceDataCollectionActivity::class.java
        )
        intent.putExtra(
            com.example.deviceowner.ui.activities.data.DeviceDataCollectionActivity.EXTRA_LOAN_NUMBER,
            loanNumber
        )
        startActivity(intent)
        finish()
    }
}

private const val EXAMPLE_LOAN_NUMBER = "Enter your loan number"

@Composable
fun DeviceRegistrationScreen(
    onLoanNumberSubmitted: (String) -> Unit
) {
    var loanNumber by remember { mutableStateOf("") }
    var isValid by remember { mutableStateOf(false) }
    var submitAttempted by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showContent by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        showContent = true
    }

    // Only validate when needed, don't show errors while typing
    LaunchedEffect(loanNumber) {
        val validation = LoanNumberValidator.validateLoanNumber(loanNumber)
        isValid = validation.isValid
        errorMessage = validation.message
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = showContent,
            enter = slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = tween(500)
            ) + fadeIn(animationSpec = tween(500))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LogoSection()

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Device Registration",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.3).sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Enter your loan number to continue",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Loan Number Input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Loan Number",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    LoanNumberInputField(
                        value = loanNumber,
                        onValueChange = { newValue ->
                            loanNumber = LoanNumberValidator.formatLoanNumber(newValue)
                            submitAttempted = false // Reset error display when user types
                        },
                        isValid = isValid,
                        errorMessage = errorMessage,
                        showError = submitAttempted && !isValid,
                        onDone = {
                            submitAttempted = true
                            if (isValid) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onLoanNumberSubmitted(loanNumber)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                Button(
                    onClick = {
                        submitAttempted = true
                        if (isValid) {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            onLoanNumberSubmitted(loanNumber)
                        }
                    },
                    enabled = isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .shadow(
                            elevation = if (isValid) 10.dp else 0.dp,
                            shape = RoundedCornerShape(10.dp),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Continue",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                TermsText()

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun LogoSection() {
    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.nexivo),
            contentDescription = "PAYO Logo",
            modifier = Modifier
                .size(56.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(14.dp),
                    spotColor = Color.Black.copy(alpha = 0.15f)
                ),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun LoanNumberInputField(
    value: String,
    onValueChange: (String) -> Unit,
    isValid: Boolean,
    errorMessage: String,
    showError: Boolean,
    onDone: () -> Unit
) {
    val borderColor = when {
        value.isEmpty() -> MaterialTheme.colorScheme.outline
        showError && !isValid -> MaterialTheme.colorScheme.error
        isValid -> Color(0xFF10B981)
        else -> MaterialTheme.colorScheme.outline
    }

    val focusColor = when {
        value.isEmpty() -> MaterialTheme.colorScheme.primary
        showError && !isValid -> MaterialTheme.colorScheme.error
        isValid -> Color(0xFF10B981)
        else -> MaterialTheme.colorScheme.primary
    }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = EXAMPLE_LOAN_NUMBER,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Normal
                )
            },
            trailingIcon = {
                if (value.isNotEmpty() && showError) {
                    Icon(
                        imageVector = if (isValid) Icons.Filled.CheckCircle else Icons.Filled.Error,
                        contentDescription = null,
                        tint = if (isValid) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onDone() }
            ),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = focusColor,
                unfocusedBorderColor = borderColor,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // Only show error message when submit is attempted
        if (showError && !isValid) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

@Composable
private fun TermsText() {
    val annotatedString = buildAnnotatedString {
        withStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        ) {
            append("By continuing, you agree to our ")
        }
        withStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        ) {
            append("Terms")
        }
        withStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        ) {
            append(" and ")
        }
        withStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        ) {
            append("Privacy")
        }
    }

    Text(
        text = annotatedString,
        textAlign = TextAlign.Center,
        lineHeight = 14.sp
    )
}

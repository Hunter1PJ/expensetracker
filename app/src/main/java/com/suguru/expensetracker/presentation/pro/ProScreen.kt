package com.suguru.expensetracker.presentation.pro

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.presentation.components.BackgroundGlowDecoration
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(
    viewModel: ProViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    // Handle user message (toast or snackbar-like banner)
    var showSnackbarMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            showSnackbarMessage = message
            viewModel.clearUserMessage()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("pro_screen")
    ) {
        // Ambient background glow decoration at the top
        BackgroundGlowDecoration()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top App Bar / Back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ExpenseTrackerSpacing.md, vertical = ExpenseTrackerSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("pro_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.md))
                Text(
                    text = "ExpenseTracker Pro",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ExpenseTrackerSpacing.lg)
                    .widthIn(max = 560.dp)
                    .align(Alignment.CenterHorizontally),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.lg)
            ) {
                // PREMIUM HERO
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ExpenseTrackerRadius.card)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.15f),
                                    ExpenseTrackerTheme.extendedColors.surfaceElevated.copy(alpha = 0.5f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.22f),
                            shape = ExpenseTrackerRadius.card
                        )
                        .padding(ExpenseTrackerSpacing.xl)
                        .testTag("pro_hero"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                    ) {
                        // Visual Crown / Star badge
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = ExpenseTrackerTheme.extendedColors.primaryBright,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))

                        Text(
                            text = "ExpenseTracker Pro",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "More control. No subscription.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ExpenseTrackerTheme.extendedColors.primaryBright,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Unlock unlimited accounts, categories, budgets, and recurring rules.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = ExpenseTrackerSpacing.sm)
                        )
                    }
                }

                // VALUE PROP / OWNERSHIP STATUS
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pro_status")
                ) {
                    when (uiState.entitlement) {
                        is ProEntitlement.Pro -> {
                            Surface(
                                color = ExpenseTrackerTheme.extendedColors.financialPositive.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, ExpenseTrackerTheme.extendedColors.financialPositive.copy(alpha = 0.3f)),
                                shape = ExpenseTrackerRadius.card,
                                modifier = Modifier.fillMaxWidth().testTag("pro_success_state")
                            ) {
                                Row(
                                    modifier = Modifier.padding(ExpenseTrackerSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = ExpenseTrackerTheme.extendedColors.financialPositive
                                    )
                                    Column {
                                        Text(
                                            text = "ExpenseTracker Pro: Lifetime unlocked",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = "Thank you for supporting ExpenseTracker.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                        is ProEntitlement.Pending -> {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                shape = ExpenseTrackerRadius.card,
                                modifier = Modifier.fillMaxWidth().testTag("pro_pending_state")
                            ) {
                                Row(
                                    modifier = Modifier.padding(ExpenseTrackerSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = "Purchase pending",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = "Google Play is still processing your payment. Your Pro features will unlock automatically once completed.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ExpenseTrackerTheme.extendedColors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                        is ProEntitlement.Checking -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(ExpenseTrackerSpacing.md),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.sm))
                                Text("Checking license status...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        else -> {
                            // Free / Unavailable
                            Text(
                                text = "ExpenseTracker Pro: One purchase. Unlimited control.",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // CORE PRO BENEFITS
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ExpenseTrackerRadius.card)
                        .background(ExpenseTrackerTheme.extendedColors.surfaceElevated)
                        .border(1.dp, ExpenseTrackerTheme.extendedColors.cardBorder, ExpenseTrackerRadius.card)
                        .padding(ExpenseTrackerSpacing.md)
                        .testTag("pro_feature_list"),
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                ) {
                    val benefits = listOf(
                        BenefitItem(Icons.Default.AccountBalance, "Unlimited Accounts", "Connect as many cash, bank, or card accounts as you need."),
                        BenefitItem(Icons.Default.Category, "Unlimited Custom Categories", "Structure your budgeting precisely with personalized categories."),
                        BenefitItem(Icons.Default.ShowChart, "Unlimited Budgets", "Plan and monitor multiple budgets across periods and categories."),
                        BenefitItem(Icons.Default.Autorenew, "Unlimited Recurring Rules", "Automate standing orders, rent, or repeating subscriptions.")
                    )

                    benefits.forEach { benefit ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(ExpenseTrackerTheme.extendedColors.surfaceHighlight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = benefit.icon,
                                    contentDescription = null,
                                    tint = ExpenseTrackerTheme.extendedColors.primaryBright,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = benefit.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = benefit.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                            }
                        }
                    }
                }

                // PRICE & ERROR PRESENTATION / CTA ACTIONS
                if (uiState.entitlement is ProEntitlement.Unavailable) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ExpenseTrackerRadius.card)
                            .background(ExpenseTrackerTheme.extendedColors.surfaceElevated)
                            .border(1.dp, ExpenseTrackerTheme.extendedColors.financialDanger.copy(alpha = 0.3f), ExpenseTrackerRadius.card)
                            .padding(ExpenseTrackerSpacing.md)
                            .testTag("pro_error"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = ExpenseTrackerTheme.extendedColors.financialDanger
                        )
                        Text(
                            text = "Pro is temporarily unavailable",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Google Play couldn't load pricing right now. Please try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseTrackerTheme.extendedColors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.restorePurchases() },
                                modifier = Modifier.weight(1f).testTag("pro_restore_button"),
                                shape = ExpenseTrackerRadius.button
                            ) {
                                Text("Restore")
                            }
                            Button(
                                onClick = { viewModel.refresh() },
                                modifier = Modifier.weight(1f).testTag("pro_try_again_button"),
                                shape = ExpenseTrackerRadius.button
                            ) {
                                Text("Try Again")
                            }
                        }
                    }
                } else if (uiState.entitlement !is ProEntitlement.Pro && uiState.entitlement !is ProEntitlement.Pending) {
                    // CTA Purchase Actions
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                    ) {
                        val product = uiState.product
                        val hasProduct = product != null
                        val buttonText = if (hasProduct) {
                            "Unlock Pro — ${product!!.formattedPrice}"
                        } else {
                            "Loading price..."
                        }

                        Button(
                            onClick = { activity?.let { viewModel.launchPurchase(it) } },
                            enabled = hasProduct && !uiState.isPurchasing && !uiState.isRestoring,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("pro_purchase_button"),
                            shape = ExpenseTrackerRadius.button,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ExpenseTrackerTheme.extendedColors.primaryPurple,
                                contentColor = Color.White,
                                disabledContainerColor = ExpenseTrackerTheme.extendedColors.surfaceHighlight,
                                disabledContentColor = ExpenseTrackerTheme.extendedColors.textMuted
                            )
                        ) {
                            if (uiState.isPurchasing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = buttonText,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("pro_price")
                                )
                            }
                        }

                        // Restore Purchases Link
                        TextButton(
                            onClick = { viewModel.restorePurchases() },
                            enabled = !uiState.isPurchasing && !uiState.isRestoring,
                            modifier = Modifier.testTag("pro_restore_button")
                        ) {
                            if (uiState.isRestoring) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp)
                            } else {
                                Text(
                                    text = "Restore Purchases",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // TRUST / PURCHASE EXPLANATION COPY
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = ExpenseTrackerSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                ) {
                    Text(
                        text = "One-time purchase. No subscription.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Payment is processed securely by Google Play. ExpenseTracker does not receive your payment-card details.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ExpenseTrackerTheme.extendedColors.textMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // Beautiful feedback toast/snackbar overlays
        showSnackbarMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .padding(horizontal = ExpenseTrackerSpacing.lg)
                    .clip(ExpenseTrackerRadius.card)
                    .background(ExpenseTrackerTheme.extendedColors.surfaceHigh)
                    .border(1.dp, ExpenseTrackerTheme.extendedColors.cardBorder, ExpenseTrackerRadius.card)
                    .padding(horizontal = ExpenseTrackerSpacing.md, vertical = ExpenseTrackerSpacing.sm)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.xs))
                    TextButton(
                        onClick = { showSnackbarMessage = null },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private data class BenefitItem(
    val icon: ImageVector,
    val title: String,
    val description: String
)

package com.atomgo.android.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.atomgo.android.AppDesign
import com.atomgo.android.ClientPaymentType
import com.atomgo.android.R
import com.atomgo.android.presentation.model.*
import com.atomgo.android.presentation.viewmodel.*
import com.atomgo.shared.api.AdminBikeResponse
import com.atomgo.shared.api.AdminClientSummaryResponse
import com.atomgo.shared.api.AdminClientDetailsResponse
import com.atomgo.shared.api.AdminRentalHistoryItemResponse
import com.atomgo.shared.api.ClientDashboardResponse
import com.atomgo.shared.api.CreatePaymentResponse
import kotlinx.coroutines.delay
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
@Composable
fun AtomGoApp(
    appViewModel: AppViewModel,
    loginViewModel: LoginViewModel,
    clientHomeViewModel: ClientHomeViewModel,
    adminHomeViewModel: AdminHomeViewModel
) {
    val route by appViewModel.route.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var lastInsideRoute by remember { mutableStateOf<AppRoute?>(null) }
    var renderLoginLayer by remember { mutableStateOf(true) }
    val activeInsideRoute = route.takeIf { it.isInsideRoute() }
    val insideVisible = activeInsideRoute != null

    LaunchedEffect(activeInsideRoute) {
        if (activeInsideRoute != null) {
            focusManager.clearFocus(force = true)
            lastInsideRoute = activeInsideRoute
        }
    }
    LaunchedEffect(insideVisible) {
        if (insideVisible) {
            delay(AppStackTransitionMillis.toLong())
            renderLoginLayer = false
        } else {
            renderLoginLayer = true
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AppDesign.PageBackground) {
        when (val current = route) {
            AppRoute.Launching -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = AppDesign.Accent)
                    Spacer(Modifier.height(12.dp))
                    Text("Запуск приложения...")
                }
            }

            else -> {
                Box(Modifier.fillMaxSize()) {
                    if (renderLoginLayer) {
                        LoginScreen(
                            loginViewModel = loginViewModel,
                            onAuthenticated = appViewModel::onAuthenticated,
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(1f)
                                .appStackUnderlyingOffset(
                                    active = insideVisible,
                                    label = "rootLoginUnderlyingOffset"
                                )
                        )
                    }

                    AppStackVisibility(
                        visible = insideVisible,
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(2f)
                    ) {
                        when (val insideRoute = activeInsideRoute ?: lastInsideRoute) {
                            is AppRoute.ClientHome -> ClientHomeScreen(
                                session = insideRoute.session,
                                clientHomeViewModel = clientHomeViewModel,
                                onLogout = { appViewModel.logout(loginViewModel::resetForNextLogin) }
                            )
                            is AppRoute.AdminHome -> AdminHomeScreen(
                                session = insideRoute.session,
                                adminHomeViewModel = adminHomeViewModel,
                                onLogout = { appViewModel.logout(loginViewModel::resetForNextLogin) }
                            )
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

private fun AppRoute.isInsideRoute(): Boolean {
    return this is AppRoute.ClientHome || this is AppRoute.AdminHome
}

@Composable
private fun ClientTariffIllustration(
    paymentType: ClientPaymentType,
    scale: Float,
    modifier: Modifier = Modifier
) {
    fun d(value: Int) = (value * scale).dp
    val fill = AppDesign.IconFill
    val stroke = AppDesign.IconStroke
    val canvas = AppDesign.IconCanvas

    when (paymentType) {
        ClientPaymentType.Day -> {
            Box(
                modifier = modifier
                    .size(d(66))
                    .offset(x = d(8), y = d(8)),
                contentAlignment = Alignment.Center
            ) {
                repeat(8) { index ->
                    Box(
                        modifier = Modifier
                            .width(d(4))
                            .height(d(11))
                            .offset(y = -d(25))
                            .graphicsLayer { rotationZ = index * 45f }
                            .background(fill, RoundedCornerShape(d(2)))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(d(30))
                        .background(fill, RoundedCornerShape(999.dp))
                )
            }
        }

        ClientPaymentType.Week,
        ClientPaymentType.Month -> {
            Box(
                modifier = modifier
                    .size(if (paymentType == ClientPaymentType.Month) d(86) else d(84))
                    .offset(x = d(8), y = d(8)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(d(74))
                        .background(fill, RoundedCornerShape(d(10)))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = d(15))
                            .padding(top = d(6)),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .width(d(3))
                                    .height(d(9))
                                    .background(stroke, RoundedCornerShape(d(2)))
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = d(6))
                            .width(d(58))
                            .height(d(48))
                            .background(canvas, RoundedCornerShape(d(5))),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        repeat(3) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(d(5))) {
                                repeat(3) {
                                    Box(
                                        modifier = Modifier
                                            .size(d(6))
                                            .background(stroke, RoundedCornerShape(d(2)))
                                    )
                                }
                            }
                            if (row < 2) Spacer(Modifier.height(d(5)))
                        }
                    }
                }
                if (paymentType == ClientPaymentType.Month) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = d(5), y = d(5))
                            .size(d(34))
                            .background(fill, RoundedCornerShape(999.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = canvas,
                            modifier = Modifier.size(d(16))
                        )
                    }
                }
            }
        }

        ClientPaymentType.TwoWeeks -> {
            Row(
                modifier = modifier.offset(x = d(8), y = -d(6)),
                horizontalArrangement = Arrangement.spacedBy(d(4)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(d(18)).border(AppDesign.HairlineStroke, stroke, RoundedCornerShape(999.dp)))
                Box(Modifier.size(d(18)).background(stroke, RoundedCornerShape(999.dp)))
                Box(Modifier.size(d(18)).border(AppDesign.HairlineStroke, stroke, RoundedCornerShape(999.dp)))
            }
        }

        ClientPaymentType.DebtExact -> Unit
    }
}

@Composable
private fun ClientTariffCard(
    paymentType: ClientPaymentType,
    amountRub: Int,
    isSelected: Boolean,
    scale: Float,
    mainText: Color,
    subtleText: Color,
    cardColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    fun d(value: Int) = (value * scale).dp
    fun ss(value: Float) = (value * scale).sp
    val animatedHeight by animateDpAsState(
        targetValue = if (isSelected) d(124) else d(122),
        animationSpec = tween(180),
        label = "client_tariff_card_height"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) mainText else AppDesign.LightStroke,
        animationSpec = tween(180),
        label = "client_tariff_card_border_color"
    )
    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isSelected) AppDesign.SelectedStroke else AppDesign.HairlineStroke,
        animationSpec = tween(180),
        label = "client_tariff_card_border_width"
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.93f,
        animationSpec = tween(180),
        label = "client_tariff_card_alpha"
    )
    val cardShape = RoundedCornerShape(d(14))

    Surface(
        modifier = modifier
            .height(animatedHeight)
            .alpha(animatedAlpha)
            .adminClickable(shape = cardShape, onClick = onClick),
        shape = cardShape,
        color = cardColor,
        border = androidx.compose.foundation.BorderStroke(animatedBorderWidth, animatedBorderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (isSelected) d(16) else d(15),
                    top = if (isSelected) d(14) else d(13),
                    bottom = if (isSelected) d(14) else d(13)
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(paymentType.title, color = subtleText, fontSize = ss(11f), fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(money(amountRub), color = mainText, fontSize = ss(13f), fontWeight = FontWeight.Bold)
            }
            ClientTariffIllustration(
                paymentType = paymentType,
                scale = scale,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

internal fun isIndividualEntrepreneurTaxMode(taxMode: String?): Boolean {
    val normalized = taxMode
        ?.trim()
        ?.lowercase()
        ?.replace("-", "_")
        ?.replace(" ", "_")
        ?: return false
    return normalized in setOf(
        "individual_entrepreneur",
        "individualentrepreneur",
        "ip",
        "ип"
    )
}

internal fun shouldShowClientReceiptEmailUi(dashboard: ClientDashboardResponse?): Boolean {
    if (dashboard == null) return false
    return dashboard.requiresReceiptEmail || isIndividualEntrepreneurTaxMode(dashboard.taxMode)
}

private fun requiresClientReceiptEmailBeforePayment(dashboard: ClientDashboardResponse): Boolean {
    return dashboard.requiresReceiptEmail ||
        (shouldShowClientReceiptEmailUi(dashboard) && dashboard.receiptEmail.isNullOrBlank())
}

@Composable
private fun LoginScreen(
    loginViewModel: LoginViewModel,
    onAuthenticated: (AuthSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by loginViewModel.uiState.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.statusText) {
        val text = state.statusText.trim()
        if (text.isNotEmpty() && text != LoginUiState.WAITING_STATUS && text.startsWith("Статус:")) {
            toastMessage = text.removePrefix("Статус:").trim().ifEmpty { null }
            if (toastMessage != null) {
                delay(2200)
                toastMessage = null
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val poppins = FontFamily(Font(R.font.poppins_medium, FontWeight.Medium))
        val urbanist = FontFamily(Font(R.font.urbanist_variable, FontWeight.Bold))
        val density = LocalDensity.current
        val xScale = maxWidth.value / 414f
        val yScale = maxHeight.value / 896f
        val textScale = minOf(xScale, yScale)
        val imeBottomDp = with(density) { WindowInsets.ime.getBottom(this).toDp().value }
        val statusBarTopDp = with(density) { WindowInsets.statusBars.getTop(this).toDp().value }
        val loginButtonBottomDp = statusBarTopDp + (687f + 63f) * yScale
        val keyboardTopDp = (maxHeight.value - imeBottomDp).coerceAtMost(maxHeight.value)
        val keyboardLiftDp = (loginButtonBottomDp + 16f - keyboardTopDp).coerceAtLeast(0f)
        val animatedKeyboardLift by animateFloatAsState(
            targetValue = keyboardLiftDp,
            animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
            label = "login_keyboard_lift"
        )
        fun sx(v: Float) = (v * xScale).dp
        fun sy(v: Float) = (v * yScale).dp
        fun sw(v: Float) = (v * xScale).dp
        fun sh(v: Float) = (v * yScale).dp

        Box(
            Modifier
                .fillMaxSize()
                .background(AppDesign.PageBackground)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = (-animatedKeyboardLift).dp)
            ) {
            Image(
                painter = painterResource(R.drawable.ic_atomgo_icon),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(sw(154f))
                    .height(sh(184f))
                    .offset(x = sx(130f), y = sy(121f))
            )

            Column(
                modifier = Modifier
                    .width(sw(382f))
                    .offset(x = sx(16f), y = sy(328f)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(" Welcome to ", fontFamily = urbanist, fontWeight = FontWeight.Bold, fontSize = (40f * textScale).sp, color = AppDesign.TitleBlack)
                Text("AtomGo", fontFamily = urbanist, fontWeight = FontWeight.Bold, fontSize = (40f * textScale).sp, color = AppDesign.TitleBlack)
            }

            LoginField(
                value = state.login,
                onValueChange = loginViewModel::onLoginChanged,
                placeholder = "Enter Your Email",
                iconRes = R.drawable.ic_user,
                keyboardType = KeyboardType.Email,
                a11yId = "login_email_input",
                textScale = textScale,
                modifier = Modifier
                    .width(sw(343f))
                    .height(sh(64f))
                    .offset(x = sx(35f), y = sy(477f)),
                fontFamily = poppins
            )

            LoginField(
                value = state.password,
                onValueChange = loginViewModel::onPasswordChanged,
                placeholder = "Password",
                iconRes = R.drawable.ic_lock,
                keyboardType = KeyboardType.Password,
                a11yId = "login_password_input",
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                textScale = textScale,
                trailing = {
                    Image(
                        painter = painterResource(if (showPassword) R.drawable.ic_eye_on else R.drawable.ic_eye_off),
                        contentDescription = null,
                        modifier = Modifier
                            .size(sw(20f))
                            .adminClickable(shape = RoundedCornerShape(999.dp)) { showPassword = !showPassword }
                    )
                },
                modifier = Modifier
                    .width(sw(343f))
                    .height(sh(64f))
                    .offset(x = sx(35f), y = sy(562f)),
                fontFamily = poppins
            )

            Row(
                modifier = Modifier
                    .width(sw(343f))
                    .offset(x = sx(35f), y = sy(642f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(if (state.rememberMe) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background),
                    contentDescription = null,
                    modifier = Modifier
                        .size((17f * textScale).dp)
                        .adminClickable(shape = RoundedCornerShape(999.dp)) { loginViewModel.setRememberMe(!state.rememberMe) }
                )
                Spacer(Modifier.width((8f * xScale).dp))
                Text("Запомнить меня", fontFamily = poppins, fontSize = (13f * textScale).sp, color = AppDesign.SubtleText)
                Spacer(Modifier.weight(1f))
                Text("Forget Password ?", fontFamily = poppins, fontSize = (14f * textScale).sp, color = AppDesign.SubtleText)
            }

            Button(
                onClick = { loginViewModel.signIn(onAuthenticated) },
                enabled = !state.isLoading,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppDesign.Accent,
                    contentColor = AppDesign.SurfaceBackground,
                    disabledContainerColor = AppDesign.Accent.copy(alpha = 0.5f),
                    disabledContentColor = AppDesign.SurfaceBackground.copy(alpha = 0.9f)
                ),
                modifier = Modifier
                    .testTag("login_submit_button")
                    .semantics { contentDescription = "login_submit_button" }
                    .width(sw(343f))
                    .height(sh(63f))
                    .offset(x = sx(35f), y = sy(687f))
                ) {
                    Text(
                        if (state.isLoading) "Getting started..." else "Get Started",
                        fontFamily = poppins,
                        fontSize = (15f * textScale).sp,
                        letterSpacing = (0.45f * textScale).sp
                    )
                }

                Text(
                    text = state.statusText,
                    color = AppDesign.Transparent,
                    fontSize = 1.sp,
                    modifier = Modifier
                        .size(1.dp)
                        .testTag("login_status_text")
                        .semantics { contentDescription = "login_status_text" }
                )
            }

            AppToast(
                message = toastMessage,
                modifier = Modifier.align(Alignment.BottomCenter),
                bottomPadding = 86
            )
        }
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    iconRes: Int,
    a11yId: String,
    modifier: Modifier,
    fontFamily: FontFamily,
    textScale: Float,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null
) {
    val fieldShape = RoundedCornerShape(12.84.dp)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .background(AppDesign.CardBackground, fieldShape)
            .border(AppDesign.HairlineStroke, AppDesign.Black, fieldShape)
            .testTag(a11yId)
            .semantics { contentDescription = a11yId },
        singleLine = true,
        shape = fieldShape,
        textStyle = TextStyle(fontFamily = fontFamily, fontSize = ((24f * textScale) / 1.7f).sp, color = AppDesign.TitleText),
        placeholder = {
            Text(
                placeholder,
                color = AppDesign.IconSoft,
                fontFamily = fontFamily,
                fontSize = ((24f * textScale) / 1.7f).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        leadingIcon = {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = trailing,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppDesign.Transparent,
            unfocusedBorderColor = AppDesign.Transparent,
            focusedContainerColor = AppDesign.CardBackground,
            unfocusedContainerColor = AppDesign.CardBackground
        )
    )
}

@Composable
private fun ClientHomeScreen(
    session: AuthSession,
    clientHomeViewModel: ClientHomeViewModel,
    onLogout: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var dashboard by remember { mutableStateOf<ClientDashboardResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var paymentStatus by remember { mutableStateOf<String?>(null) }
    var activePaymentId by remember { mutableStateOf<String?>(null) }
    var isCreatingPayment by remember { mutableStateOf(false) }
    var isRefreshingPaymentStatus by remember { mutableStateOf(false) }
    var isSavingReceiptEmail by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(ClientPaymentType.Week) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var isTariffSheetPresented by remember { mutableStateOf(false) }
    var pendingPaymentType by remember { mutableStateOf<ClientPaymentType?>(null) }
    var isReceiptEmailDialogPresented by remember { mutableStateOf(false) }
    var receiptEmailInput by remember { mutableStateOf("") }

    fun refresh() {
        clientHomeViewModel.fetchClientDashboard(session.accessToken) { result ->
            result.onSuccess {
                dashboard = it
                error = null
            }.onFailure {
                error = it.message ?: "Ошибка загрузки"
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(AppStackDeferredWorkMillis.toLong())
        refresh()
    }
    LaunchedEffect(paymentStatus) {
        val text = paymentStatus?.trim().orEmpty()
        if (text.isNotEmpty()) {
            toastMessage = text
            delay(2200)
            toastMessage = null
        }
    }

    fun paymentMessage(payment: CreatePaymentResponse): String? {
        if (!isIndividualEntrepreneurTaxMode(payment.taxMode)) return null
        return when (payment.fiscalizationStatus) {
            "yookassa_receipt_pending" -> "Платеж создан. Чек будет отправлен на email после обработки ЮKassa."
            "fiscalization_not_configured" -> "Платеж создан. Чек 54-ФЗ не будет отправлен, пока в ЮKassa не настроена фискализация магазина."
            else -> null
        }
    }

    fun refreshPaymentStatus(paymentIdToRefresh: String) {
        if (paymentIdToRefresh.isBlank() || isRefreshingPaymentStatus) return
        isRefreshingPaymentStatus = true
        clientHomeViewModel.refreshPaymentStatus(session.accessToken, paymentIdToRefresh) { result ->
            result.onSuccess {
                paymentStatus = when (it.status) {
                    "succeeded" -> "Платеж успешно прошел. Данные аренды обновлены."
                    "canceled", "failed" -> "Платеж не прошел. Деньги не начислены."
                    else -> "Платеж пока ожидает подтверждения ЮKassa."
                }
                refresh()
            }.onFailure { paymentStatus = "Ошибка статуса: ${it.message}" }
            isRefreshingPaymentStatus = false
        }
    }

    fun runPayment(type: ClientPaymentType, receiptEmail: String?) {
        if (isCreatingPayment) return
        isCreatingPayment = true
        paymentStatus = null
        clientHomeViewModel.createClientPayment(
            accessToken = session.accessToken,
            paymentType = type.apiValue,
            receiptEmail = receiptEmail
        ) { result ->
            result.onSuccess {
                activePaymentId = it.paymentId
                paymentStatus = paymentMessage(it)
                if (!receiptEmail.isNullOrBlank()) {
                    dashboard = dashboard?.copy(
                        receiptEmail = receiptEmail,
                        requiresReceiptEmail = false
                    )
                }
                val confirmationUrl = it.confirmationUrl.trim()
                val shouldOpenPayment = it.status !in setOf("succeeded", "canceled", "failed")
                if (confirmationUrl.isNotEmpty() && shouldOpenPayment) {
                    runCatching { uriHandler.openUri(confirmationUrl) }
                        .onFailure { paymentStatus = "Не удалось открыть оплату: ${it.message}" }
                }
                refresh()
            }.onFailure { paymentStatus = "Ошибка платежа: ${it.message}" }
            isCreatingPayment = false
        }
    }

    fun startPayment(type: ClientPaymentType) {
        val data = dashboard ?: return
        if (type == ClientPaymentType.DebtExact && data.debtRub <= 0) return
        if (requiresClientReceiptEmailBeforePayment(data)) {
            pendingPaymentType = type
            receiptEmailInput = data.receiptEmail.orEmpty()
            isReceiptEmailDialogPresented = true
            return
        }
        runPayment(type = type, receiptEmail = null)
    }

    fun updateReceiptEmail(email: String) {
        if (isSavingReceiptEmail) return
        isSavingReceiptEmail = true
        clientHomeViewModel.updateReceiptEmail(session.accessToken, email) { result ->
            result.onSuccess { response ->
                val savedEmail = response.email.takeIf { it.isNotBlank() } ?: email
                dashboard = dashboard?.copy(
                    receiptEmail = savedEmail,
                    requiresReceiptEmail = false
                )
                paymentStatus = "Email для чека сохранен."
                refresh()
            }.onFailure { paymentStatus = "Ошибка сохранения email: ${it.message}" }
            isSavingReceiptEmail = false
        }
    }

    DisposableEffect(lifecycleOwner, activePaymentId) {
        val observer = LifecycleEventObserver { _, event ->
            val id = activePaymentId
            if (event == Lifecycle.Event.ON_RESUME && !id.isNullOrBlank()) {
                refresh()
                refreshPaymentStatus(id)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cardColor = AppDesign.BlackHaze
    val mainText = AppDesign.DarkControl
    val subtleText = AppDesign.PaleSky
    val darkButton = AppDesign.DarkControl

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesign.PageBackground)
    ) {
        val density = LocalDensity.current
        val scale = minOf(maxWidth.value / 414f, maxHeight.value / 896f).coerceIn(0.86f, 1.08f)
        fun s(value: Int) = (value * scale).dp
        fun ss(value: Float) = (value * scale).sp
        val navigationBottomDp = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
        val showReceiptEmailUi = shouldShowClientReceiptEmailUi(dashboard)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = s(23))
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = s(120) + navigationBottomDp)
        ) {
            Spacer(Modifier.height(s(10)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onLogout,
                    shape = RoundedCornerShape(s(14)),
                    border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, mainText),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(s(47))
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_admin_exit),
                        contentDescription = null,
                        modifier = Modifier.size(s(16))
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Моя аренда",
                    color = mainText,
                    fontSize = ss(18f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .testTag("client_home_title")
                        .semantics { contentDescription = "client_home_title" }
                )
                Spacer(Modifier.weight(1f))
                if (showReceiptEmailUi) {
                    OutlinedButton(
                        onClick = {
                            pendingPaymentType = null
                            receiptEmailInput = dashboard?.receiptEmail.orEmpty()
                            isReceiptEmailDialogPresented = true
                        },
                        shape = RoundedCornerShape(s(14)),
                        border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, mainText),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .size(s(47))
                            .testTag("client_receipt_email_edit_button")
                    ) {
                        Image(
                            painter = painterResource(R.drawable.refaktoring),
                            contentDescription = null,
                            modifier = Modifier.size(s(16))
                        )
                    }
                } else {
                    Spacer(Modifier.size(s(47)))
                }
            }

            Spacer(Modifier.height(s(16)))
            if (error != null) {
                Surface(
                    shape = RoundedCornerShape(s(16)),
                    color = cardColor,
                    border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, AppDesign.Danger)
                ) {
                    Column(Modifier.fillMaxWidth().padding(s(16))) {
                        Text("Не удалось загрузить данные", color = AppDesign.Danger, fontWeight = FontWeight.Bold, fontSize = ss(16f))
                        Spacer(Modifier.height(s(6)))
                        Text(error.orEmpty(), color = subtleText, fontSize = ss(13f))
                        Spacer(Modifier.height(s(10)))
                        OutlinedButton(onClick = ::refresh) { Text("Повторить") }
                    }
                }
                return@Column
            }
            if (dashboard == null) {
                Spacer(Modifier.height(s(40)))
                CircularProgressIndicator(color = AppDesign.Accent)
                return@Column
            }

            val data = dashboard!!
            val debtTitle = if (data.debtRub > 0) "ДОЛГ" else "ОСТАТОК"
            val debtAmount = if (data.debtRub > 0) data.debtRub else data.balanceRub
            val debtColor = if (data.debtRub > 0) AppDesign.DangerRed else AppDesign.PaidGreen
            val completedAtText = data.completedAt?.takeIf { it.isNotBlank() }?.let(::formatLongRuDate)
                ?: if (!data.rentalIsActive) "—" else null

            Surface(
                shape = RoundedCornerShape(s(15)),
                color = cardColor,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, mainText)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = s(23), vertical = s(21))) {
                    Row(horizontalArrangement = Arrangement.spacedBy(s(16)), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(s(84))
                                .background(AppDesign.Placeholder, RoundedCornerShape(s(18)))
                                .drawWithContent {
                                    drawContent()
                                    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                                    drawRoundRect(
                                        color = AppDesign.PlaceholderStroke,
                                        cornerRadius = CornerRadius(s(18).toPx(), s(18).toPx()),
                                        style = Stroke(width = AppDesign.HairlineStroke.toPx(), pathEffect = dash)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DirectionsBike,
                                contentDescription = null,
                                tint = AppDesign.BikeIconMuted,
                                modifier = Modifier.size(s(40))
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(s(4))) {
                            Text(
                                data.bikeModel,
                                color = mainText,
                                fontSize = ss(16f),
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("${money(data.presets.weekRub)}/нед", color = subtleText, fontSize = ss(12f))
                            if (shouldShowClientReceiptEmailUi(data)) {
                                val hasEmail = !data.receiptEmail.isNullOrBlank()
                                Text(
                                    text = data.receiptEmail?.takeIf { it.isNotBlank() } ?: "Email для чека не указан",
                                    color = if (hasEmail) mainText else subtleText,
                                    fontSize = ss(10f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("client_receipt_email_text")
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(s(16)))
                    HorizontalDivider(color = AppDesign.LightStroke, thickness = AppDesign.HairlineStroke)
                    Spacer(Modifier.height(s(10)))
                    Row(horizontalArrangement = Arrangement.spacedBy(s(12))) {
                        val stats = listOfNotNull(
                            Triple(debtTitle, money(debtAmount), debtColor),
                            Triple("КОРРЕКТ.", money(data.totalAdjustmentRub), mainText),
                            Triple("ОПЛАЧЕН ДО", formatLongRuDate(data.paidUntil), mainText),
                            completedAtText?.let { Triple("ЗАВЕРШЕНА", it, mainText) }
                        )
                        stats.forEach { (title, value, color) ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, color = subtleText, fontSize = ss(9f), fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(s(3)))
                                Text(value, color = color, fontSize = ss(13f), fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(s(22)))
            Text("БЫСТРАЯ ОПЛАТА", color = subtleText, fontSize = ss(11f), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(s(12)))
            Button(
                onClick = { startPayment(ClientPaymentType.DebtExact) },
                modifier = Modifier.fillMaxWidth().height(s(63)),
                shape = RoundedCornerShape(s(16)),
                enabled = data.debtRub > 0 && !isCreatingPayment,
                colors = ButtonDefaults.buttonColors(
                    containerColor = darkButton,
                    contentColor = AppDesign.SurfaceBackground
                )
            ) {
                if (isCreatingPayment) {
                    CircularProgressIndicator(color = AppDesign.SurfaceBackground, strokeWidth = 2.dp, modifier = Modifier.size(s(20)))
                } else {
                    Text("Оплатить весь долг · ${money(data.debtRub.coerceAtLeast(0))}", fontWeight = FontWeight.Bold, fontSize = ss(14f))
                }
            }

            Spacer(Modifier.height(s(10)))
            Button(
                onClick = {
                    selectedType = ClientPaymentType.Week
                    isReceiptEmailDialogPresented = false
                    isTariffSheetPresented = true
                },
                modifier = Modifier.fillMaxWidth().height(s(63)),
                shape = RoundedCornerShape(s(16)),
                enabled = !isCreatingPayment,
                colors = ButtonDefaults.buttonColors(
                    containerColor = darkButton,
                    contentColor = AppDesign.SurfaceBackground
                )
            ) { Text("Выбрать тариф ↑", fontWeight = FontWeight.Bold, fontSize = ss(14f)) }

        }

        AnimatedVisibility(
            visible = isTariffSheetPresented && dashboard != null,
            enter = fadeIn(animationSpec = tween(180)) + slideInVertically(initialOffsetY = { it }, animationSpec = tween(260, easing = LinearOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            val data = dashboard ?: return@AnimatedVisibility
            Surface(
                color = AppDesign.SurfaceBackground,
                shape = RoundedCornerShape(topStart = s(24), topEnd = s(24)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(s(429) + navigationBottomDp)
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = s(23))
                        .padding(top = s(14), bottom = s(14) + navigationBottomDp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(s(40))
                            .height(s(4))
                            .background(AppDesign.SheetHandle, RoundedCornerShape(s(2)))
                    )
                    Spacer(Modifier.height(s(12)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Оплата аренды", color = mainText, fontSize = ss(15.5f), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { isTariffSheetPresented = false }) { Text("Закрыть ✕", color = subtleText, fontSize = ss(12f)) }
                    }
                    Spacer(Modifier.height(s(14)))
                    Row(horizontalArrangement = Arrangement.spacedBy(s(10))) {
                        listOf(ClientPaymentType.Day, ClientPaymentType.Week).forEach { type ->
                            ClientTariffCard(
                                paymentType = type,
                                amountRub = amountForType(type, data),
                                isSelected = selectedType == type,
                                scale = scale,
                                mainText = mainText,
                                subtleText = subtleText,
                                cardColor = cardColor,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedType = type },
                            )
                        }
                    }
                    Spacer(Modifier.height(s(10)))
                    Row(horizontalArrangement = Arrangement.spacedBy(s(10))) {
                        listOf(ClientPaymentType.TwoWeeks, ClientPaymentType.Month).forEach { type ->
                            ClientTariffCard(
                                paymentType = type,
                                amountRub = amountForType(type, data),
                                isSelected = selectedType == type,
                                scale = scale,
                                mainText = mainText,
                                subtleText = subtleText,
                                cardColor = cardColor,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedType = type },
                            )
                        }
                    }
                    Spacer(Modifier.height(s(16)))
                    OutlinedButton(
                        onClick = {
                            isTariffSheetPresented = false
                            startPayment(selectedType)
                        },
                        modifier = Modifier.fillMaxWidth().height(s(63)),
                        shape = RoundedCornerShape(s(16)),
                        border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, mainText),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = AppDesign.SurfaceBackground, contentColor = mainText)
                    ) { Text("Оплатить выбранный · ${money(amountForType(selectedType, data))}", fontWeight = FontWeight.Bold, fontSize = ss(14f)) }
                }
            }
        }

        if (isReceiptEmailDialogPresented) {
            AlertDialog(
                onDismissRequest = {
                    isReceiptEmailDialogPresented = false
                    pendingPaymentType = null
                },
                title = { Text("Email для чека") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Укажите email, куда ЮKassa отправит чек.")
                        OutlinedTextField(
                            value = receiptEmailInput,
                            onValueChange = { receiptEmailInput = it },
                            placeholder = { Text("name@example.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.testTag("client_receipt_email_field")
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val email = receiptEmailInput.trim()
                            if (email.isEmpty()) {
                                paymentStatus = "Укажите email для чека."
                                return@TextButton
                            }
                            val type = pendingPaymentType
                            isReceiptEmailDialogPresented = false
                            pendingPaymentType = null
                            if (type != null) {
                                runPayment(type = type, receiptEmail = email)
                            } else {
                                updateReceiptEmail(email)
                            }
                        },
                        modifier = Modifier.testTag("client_receipt_email_submit_button")
                    ) { Text(if (pendingPaymentType == null) "Сохранить" else "Продолжить") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            isReceiptEmailDialogPresented = false
                            pendingPaymentType = null
                        }
                    ) { Text("Отмена") }
                }
            )
        }

        AppToast(
            message = toastMessage,
            modifier = Modifier.align(Alignment.BottomCenter),
            bottomPadding = 86
        )
    }
}

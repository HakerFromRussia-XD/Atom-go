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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

            AppRoute.Login -> LoginScreen(loginViewModel = loginViewModel, onAuthenticated = appViewModel::onAuthenticated)
            is AppRoute.ClientHome -> ClientHomeScreen(
                session = current.session,
                clientHomeViewModel = clientHomeViewModel,
                onLogout = { appViewModel.logout(loginViewModel::resetForNextLogin) }
            )
            is AppRoute.AdminHome -> AdminHomeScreen(
                session = current.session,
                adminHomeViewModel = adminHomeViewModel,
                onLogout = { appViewModel.logout(loginViewModel::resetForNextLogin) }
            )
        }
    }
}

@Composable
private fun LoginScreen(
    loginViewModel: LoginViewModel,
    onAuthenticated: (AuthSession) -> Unit
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
                Text(" Welcome to ", fontFamily = urbanist, fontWeight = FontWeight.Bold, fontSize = (40f * textScale).sp, color = Color(0xFF212121))
                Text("AtomGo", fontFamily = urbanist, fontWeight = FontWeight.Bold, fontSize = (40f * textScale).sp, color = Color(0xFF212121))
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
                        modifier = Modifier.size(sw(20f)).clickable { showPassword = !showPassword }
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
                        .clickable { loginViewModel.setRememberMe(!state.rememberMe) }
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
                    contentColor = Color.White,
                    disabledContainerColor = AppDesign.Accent.copy(alpha = 0.5f),
                    disabledContentColor = Color.White.copy(alpha = 0.9f)
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
                    color = Color.Transparent,
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .testTag(a11yId)
            .semantics { contentDescription = a11yId },
        singleLine = true,
        shape = RoundedCornerShape(12.84.dp),
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
            focusedBorderColor = Color.Black,
            unfocusedBorderColor = Color.Black,
            focusedContainerColor = AppDesign.CardBackground,
            unfocusedContainerColor = AppDesign.CardBackground
        )
    )
}

@Composable
internal fun AppToast(
    message: String?,
    modifier: Modifier = Modifier,
    bottomPadding: Int = 86
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
            slideInVertically(initialOffsetY = { it }, animationSpec = tween(durationMillis = 180)),
        exit = fadeOut(animationSpec = tween(durationMillis = 180)) +
            slideOutVertically(targetOffsetY = { it }, animationSpec = tween(durationMillis = 180)),
        modifier = modifier.padding(bottom = bottomPadding.dp)
    ) {
        Surface(
            color = Color.White.copy(alpha = 0.98f),
            shadowElevation = 10.dp,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = message.orEmpty(),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
        }
    }
}

@Composable
private fun ClientHomeScreen(
    session: AuthSession,
    clientHomeViewModel: ClientHomeViewModel,
    onLogout: () -> Unit
) {
    var dashboard by remember { mutableStateOf<ClientDashboardResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var paymentId by remember { mutableStateOf("") }
    var paymentStatus by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf(ClientPaymentType.Week) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

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
    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(paymentStatus) {
        val text = paymentStatus?.trim().orEmpty()
        if (text.isNotEmpty()) {
            toastMessage = text
            delay(2200)
            toastMessage = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Моя аренда",
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppDesign.TitleText,
                    modifier = Modifier
                        .testTag("client_home_title")
                        .semantics { contentDescription = "client_home_title" }
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onLogout) { Text("Выйти") }
            }
            Spacer(Modifier.height(12.dp))
            if (error != null) {
                Text(error.orEmpty(), color = AppDesign.Danger)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = ::refresh) { Text("Повторить") }
                return@Column
            }
            if (dashboard == null) {
                CircularProgressIndicator(color = AppDesign.Accent)
                return@Column
            }

        val data = dashboard!!
        val debtTitle = if (data.debtRub > 0) "ДОЛГ" else "ОСТАТОК"
        val debtAmount = if (data.debtRub > 0) data.debtRub else data.balanceRub
        val debtColor = if (data.debtRub > 0) AppDesign.Danger else AppDesign.Success

        Surface(shape = RoundedCornerShape(15.dp), color = Color.White, shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(data.bikeModel, style = MaterialTheme.typography.titleMedium, color = AppDesign.TitleText)
                Spacer(Modifier.height(4.dp))
                Text("${money(data.presets.weekRub)}/нед", color = AppDesign.SubtleText)
                Spacer(Modifier.height(12.dp))
                Row {
                    StatBlock(debtTitle, money(debtAmount), debtColor)
                    Spacer(Modifier.width(12.dp))
                    StatBlock("КОРРЕКТ.", money(data.totalAdjustmentRub), AppDesign.TitleText)
                    Spacer(Modifier.width(12.dp))
                    StatBlock("ОПЛАЧЕН ДО", data.paidUntil, AppDesign.TitleText)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("БЫСТРАЯ ОПЛАТА", color = AppDesign.SubtleText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                clientHomeViewModel.createClientPayment(
                    accessToken = session.accessToken,
                    paymentType = ClientPaymentType.DebtExact.apiValue,
                    receiptEmail = null
                ) { result ->
                    result.onSuccess {
                        paymentId = it.paymentId
                        paymentStatus = "Создан платеж: ${money(it.amountRub)}, статус: ${it.status}"
                        refresh()
                    }.onFailure { paymentStatus = "Ошибка платежа: ${it.message}" }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = data.debtRub > 0
        ) { Text("Оплатить весь долг · ${money(data.debtRub.coerceAtLeast(0))}") }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ClientPaymentType.Day, ClientPaymentType.Week, ClientPaymentType.TwoWeeks, ClientPaymentType.Month).forEach { type ->
                OutlinedButton(
                    onClick = { selectedType = type },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedType == type) AppDesign.Accent.copy(alpha = 0.08f) else Color.White,
                        contentColor = AppDesign.TitleText
                    )
                ) {
                    Text(type.title, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                clientHomeViewModel.createClientPayment(
                    accessToken = session.accessToken,
                    paymentType = selectedType.apiValue,
                    receiptEmail = null
                ) { result ->
                    result.onSuccess {
                        paymentId = it.paymentId
                        paymentStatus = "Создан платеж: ${money(it.amountRub)}, статус: ${it.status}"
                    }.onFailure { paymentStatus = "Ошибка платежа: ${it.message}" }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Оплатить выбранный · ${money(amountForType(selectedType, data))}") }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                if (paymentId.isNotBlank()) {
                    clientHomeViewModel.refreshPaymentStatus(session.accessToken, paymentId) { result ->
                        result.onSuccess {
                            paymentStatus = "Статус платежа: ${it.status}, сумма: ${money(it.amountRub)}"
                            refresh()
                        }.onFailure { paymentStatus = "Ошибка статуса: ${it.message}" }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Проверить статус платежа") }

        if (!paymentStatus.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(paymentStatus.orEmpty(), color = AppDesign.SubtleText)
        }

        }

        AppToast(
            message = toastMessage,
            modifier = Modifier.align(Alignment.BottomCenter),
            bottomPadding = 86
        )
    }
}

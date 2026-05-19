package com.atomgo.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.DirectionsBike
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atomgo.shared.api.AdminClientSummaryResponse
import com.atomgo.shared.api.AdminClientDetailsResponse
import com.atomgo.shared.api.ClientDashboardResponse
import kotlinx.coroutines.delay
import java.text.DecimalFormat

@Composable
fun AtomGoApp(
    appViewModel: AppViewModel,
    loginViewModel: LoginViewModel
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
                appViewModel = appViewModel,
                onLogout = { appViewModel.logout(loginViewModel::resetForNextLogin) }
            )
            is AppRoute.AdminHome -> AdminHomeScreen(
                session = current.session,
                appViewModel = appViewModel,
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
private fun AppToast(
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
                color = Color.Black
            )
        }
    }
}

@Composable
private fun ClientHomeScreen(
    session: AuthSession,
    appViewModel: AppViewModel,
    onLogout: () -> Unit
) {
    var dashboard by remember { mutableStateOf<ClientDashboardResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var paymentId by remember { mutableStateOf("") }
    var paymentStatus by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf(ClientPaymentType.Week) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        appViewModel.fetchClientDashboard(session.accessToken) { result ->
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
                appViewModel.createClientPayment(
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
                appViewModel.createClientPayment(
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
                    appViewModel.refreshPaymentStatus(session.accessToken, paymentId) { result ->
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

private enum class AdminRentFilter {
    All,
    SoonReturn,
    Debtors,
    Mine
}

private enum class AdminHomeTab {
    Rents,
    Clients,
    Bikes
}

private data class AdminFilterCounters(
    val all: Int,
    val soonReturn: Int,
    val debtors: Int,
    val mine: Int
)

private data class RentStatusPill(
    val title: String,
    val value: String,
    val color: Color,
    val widthDp: Int
)

@Composable
private fun AdminSquareTopButton(
    iconRes: Int,
    testTag: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppDesign.Accent),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = AppDesign.Accent
        ),
        modifier = Modifier
            .size(47.dp)
            .testTag(testTag)
            .semantics { contentDescription = testTag }
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun AdminSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .background(Color.White, RoundedCornerShape(12.84.dp))
            .border(1.dp, AppDesign.Accent, RoundedCornerShape(12.84.dp))
            .padding(horizontal = 15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = AppDesign.TitleText
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    color = AppDesign.TitleText
                ),
                cursorBrush = SolidColor(AppDesign.TitleText),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (value.isBlank()) {
                        Text(
                            text = "Поиск по клиенту, велосипеду...",
                            fontSize = 13.sp,
                            color = Color(0xFF73747F)
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}

@Composable
private fun AdminFilterRows(
    selectedFilter: AdminRentFilter,
    counts: AdminFilterCounters,
    onSelect: (AdminRentFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminFilterChip(
                title = "Все",
                count = counts.all,
                width = 84.dp,
                isSelected = selectedFilter == AdminRentFilter.All,
                testTag = "admin_filter_all",
                onClick = { onSelect(AdminRentFilter.All) }
            )
            AdminFilterChip(
                title = "Скоро вернут",
                count = counts.soonReturn,
                width = 146.dp,
                isSelected = selectedFilter == AdminRentFilter.SoonReturn,
                testTag = "admin_filter_soon_return",
                onClick = { onSelect(AdminRentFilter.SoonReturn) }
            )
            AdminFilterChip(
                title = "Должники",
                count = counts.debtors,
                width = 132.dp,
                isSelected = selectedFilter == AdminRentFilter.Debtors,
                testTag = "admin_filter_debtors",
                onClick = { onSelect(AdminRentFilter.Debtors) }
            )
        }
        AdminFilterChip(
            title = "У меня",
            count = counts.mine,
            width = 108.dp,
            isSelected = selectedFilter == AdminRentFilter.Mine,
            testTag = "admin_filter_mine",
            onClick = { onSelect(AdminRentFilter.Mine) }
        )
    }
}

@Composable
private fun AdminFilterChip(
    title: String,
    count: Int,
    width: androidx.compose.ui.unit.Dp,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) AppDesign.Accent else Color.White,
        animationSpec = tween(180),
        label = "admin_filter_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else AppDesign.Accent,
        animationSpec = tween(180),
        label = "admin_filter_text"
    )
    val counterColor by animateColorAsState(
        targetValue = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.08f),
        animationSpec = tween(180),
        label = "admin_filter_counter"
    )

    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 0.dp),
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppDesign.Accent),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = textColor
        ),
        modifier = Modifier
            .width(width)
            .height(36.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = count.toString(),
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(counterColor, RoundedCornerShape(999.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun AdminBottomTabBar(
    selectedTab: AdminHomeTab,
    onTabSelected: (AdminHomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = Color(red = 218f / 255f, green = 218f / 255f, blue = 218f / 255f)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("admin_bottom_tab_bar"),
        color = Color.White.copy(alpha = 0.93f),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(borderColor)
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                AdminBottomTabItem(
                    tab = AdminHomeTab.Rents,
                    selectedTab = selectedTab,
                    title = "Аренды",
                    selectedIcon = Icons.Filled.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    tag = "admin_tab_rents",
                    modifier = Modifier.weight(1f),
                    onClick = onTabSelected
                )
                AdminBottomTabItem(
                    tab = AdminHomeTab.Clients,
                    selectedTab = selectedTab,
                    title = "Клиенты",
                    selectedIcon = Icons.Filled.Group,
                    unselectedIcon = Icons.Outlined.Group,
                    tag = "admin_tab_clients",
                    modifier = Modifier.weight(1f),
                    onClick = onTabSelected
                )
                AdminBottomTabItem(
                    tab = AdminHomeTab.Bikes,
                    selectedTab = selectedTab,
                    title = "Велосипеды",
                    selectedIcon = Icons.Filled.DirectionsBike,
                    unselectedIcon = Icons.Outlined.DirectionsBike,
                    tag = "admin_tab_bikes",
                    modifier = Modifier.weight(1f),
                    onClick = onTabSelected
                )
            }
        }
    }
}

@Composable
private fun AdminBottomTabItem(
    tab: AdminHomeTab,
    selectedTab: AdminHomeTab,
    title: String,
    selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: (AdminHomeTab) -> Unit
) {
    val isSelected = selectedTab == tab
    val color = if (isSelected) Color(0xFF141718) else AppDesign.IconSoft

    Column(
        modifier = modifier
            .clickable { onClick(tab) }
            .testTag(tag)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else unselectedIcon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(if (tab == AdminHomeTab.Bikes) 24.dp else 22.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (isSelected) Color(0xFF141718) else Color.Transparent, RoundedCornerShape(999.dp))
        )
    }
}

@Composable
private fun AdminSecondaryTabStub(
    title: String,
    buttonText: String,
    onPrimaryAction: () -> Unit
) {
    val density = LocalDensity.current
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = statusBarTop + 18.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            color = Color(0xFF141718),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Экран переносится с iOS. Временная панель действий до полной parity-сборки.",
            color = AppDesign.SubtleText,
            fontSize = 13.sp
        )
        OutlinedButton(
            onClick = onPrimaryAction,
            shape = RoundedCornerShape(999.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppDesign.Accent),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = AppDesign.Accent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Text(buttonText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AdminHomeScreen(
    session: AuthSession,
    appViewModel: AppViewModel,
    onLogout: () -> Unit
) {
    var rents by remember { mutableStateOf<List<AdminClientSummaryResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(AdminRentFilter.All) }
    var search by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(AdminHomeTab.Rents) }
    var adminMessage by remember { mutableStateOf<String?>(null) }
    var showCreateClient by remember { mutableStateOf(false) }
    var showCreateBike by remember { mutableStateOf(false) }
    var showCreateRental by remember { mutableStateOf(false) }
    var showUpdateClient by remember { mutableStateOf(false) }
    var showUpdateBike by remember { mutableStateOf(false) }
    var showUpdateRental by remember { mutableStateOf(false) }
    var confirmDeleteRentalId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteClientId by remember { mutableStateOf<String?>(null) }
    var showFinishRentalFor by remember { mutableStateOf<AdminClientSummaryResponse?>(null) }
    var showStartRental by remember { mutableStateOf(false) }
    var detailClientId by remember { mutableStateOf<String?>(null) }
    var detailPayload by remember { mutableStateOf<AdminClientDetailsResponse?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        isLoading = true
        error = null
        appViewModel.fetchAdminRents(session.accessToken) { result ->
            result.onSuccess {
                rents = it
                isLoading = false
            }.onFailure {
                error = it.message ?: "Ошибка загрузки"
                isLoading = false
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(adminMessage) {
        val text = adminMessage?.trim().orEmpty()
        if (text.isNotEmpty()) {
            toastMessage = text
            delay(2200)
            toastMessage = null
        }
    }

    val normalizedQuery = search.trim()
    val searchedRents = rents.filter { item ->
        normalizedQuery.isEmpty() ||
            item.fullName.contains(normalizedQuery, ignoreCase = true) ||
            item.bikeModel.contains(normalizedQuery, ignoreCase = true) ||
            (item.clientLogin ?: "").contains(normalizedQuery, ignoreCase = true)
    }
    val filtered = searchedRents.filter { item ->
        when (filter) {
            AdminRentFilter.All -> true
            AdminRentFilter.SoonReturn -> item.rentalIsActive && item.rentalPipelineStatus.orEmpty() == "soon_return"
            AdminRentFilter.Debtors -> item.debtRub > 0
            AdminRentFilter.Mine -> !item.rentalIsActive
        }
    }

    val filterCounts = AdminFilterCounters(
        all = rents.size,
        soonReturn = rents.count { it.rentalIsActive && it.rentalPipelineStatus.orEmpty() == "soon_return" },
        debtors = rents.count { it.debtRub > 0 },
        mine = rents.count { !it.rentalIsActive }
    )
    val density = LocalDensity.current
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesign.PageBackground)
    ) {
        when (selectedTab) {
            AdminHomeTab.Rents -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    Spacer(Modifier.height(statusBarTop))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AdminSquareTopButton(
                            iconRes = R.drawable.ic_admin_exit,
                            testTag = "admin_logout_button",
                            onClick = onLogout
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Все аренды",
                            color = Color(0xFF141718),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .testTag("admin_home_title")
                                .semantics { contentDescription = "admin_home_title" }
                        )
                        Spacer(Modifier.weight(1f))
                        AdminSquareTopButton(
                            iconRes = R.drawable.ic_admin_plus,
                            testTag = "admin_create_button"
                        ) {
                            showCreateRental = true
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    AdminSearchField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_search_field")
                    )

                    Spacer(Modifier.height(10.dp))

                    AdminFilterRows(
                        selectedFilter = filter,
                        counts = filterCounts,
                        onSelect = { filter = it }
                    )

                    Spacer(Modifier.height(10.dp))

                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = AppDesign.Accent)
                            }
                        }

                        error != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("Не удалось загрузить аренды", color = AppDesign.Danger, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(error.orEmpty(), color = AppDesign.SubtleText)
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(onClick = ::refresh) { Text("Повторить") }
                            }
                        }

                        else -> {
                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(scrollState)
                                    .padding(bottom = 124.dp)
                            ) {
                                if (filtered.isEmpty()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(15.dp),
                                        color = Color(0xFFFAFBFB),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AppDesign.Accent)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 30.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("Аренд пока нет", color = AppDesign.TitleText, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.height(4.dp))
                                            Text("Клиентов в каталоге: ${rents.size}", color = AppDesign.SubtleText, fontSize = 13.sp)
                                        }
                                    }
                                } else {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateContentSize(animationSpec = tween(durationMillis = 180))
                                            .testTag("admin_rents_container"),
                                        shape = RoundedCornerShape(15.dp),
                                        color = Color(0xFFFAFBFB),
                                        shadowElevation = 8.dp,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AppDesign.Accent)
                                    ) {
                                        Column(modifier = Modifier.padding(vertical = 5.dp)) {
                                            filtered.forEachIndexed { index, item ->
                                                AdminRentCard(
                                                    item = item,
                                                    onDetails = {
                                                        isDetailLoading = true
                                                        detailPayload = null
                                                        detailClientId = item.clientId
                                                        appViewModel.fetchAdminClientDetails(session.accessToken, item.clientId) { result ->
                                                            result.onSuccess {
                                                                detailPayload = it
                                                                isDetailLoading = false
                                                            }.onFailure {
                                                                adminMessage = "Ошибка загрузки деталей: ${it.message}"
                                                                isDetailLoading = false
                                                            }
                                                        }
                                                    }
                                                )
                                                if (index < filtered.lastIndex) {
                                                    HorizontalDivider(color = Color(0xFFEAEAF0), thickness = 1.dp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AdminHomeTab.Clients -> {
                AdminSecondaryTabStub(
                    title = "Клиенты",
                    buttonText = "Новый клиент",
                    onPrimaryAction = { showCreateClient = true }
                )
            }

            AdminHomeTab.Bikes -> {
                AdminSecondaryTabStub(
                    title = "Велосипеды",
                    buttonText = "Новый велосипед",
                    onPrimaryAction = { showCreateBike = true }
                )
            }
        }

        AdminBottomTabBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        AppToast(
            message = toastMessage,
            modifier = Modifier.align(Alignment.BottomCenter),
            bottomPadding = 96
        )
    }

    if (showCreateClient) {
        AdminCreateClientDialog(
            onDismiss = { showCreateClient = false },
            onCreate = { fullName, address, passport, phoneLabel, phoneNumber ->
                appViewModel.createAdminClient(
                    accessToken = session.accessToken,
                    fullName = fullName,
                    address = address,
                    passportData = passport,
                    phoneLabel = phoneLabel,
                    phoneNumber = phoneNumber
                ) { result ->
                    result.onSuccess {
                        adminMessage = "Клиент создан"
                        showCreateClient = false
                        refresh()
                    }.onFailure { adminMessage = "Ошибка создания клиента: ${it.message}" }
                }
            }
        )
    }

    if (showCreateBike) {
        AdminCreateBikeDialog(
            onDismiss = { showCreateBike = false },
            onCreate = { model, rate, frame, motor, battery1, battery2 ->
                appViewModel.createAdminBike(
                    accessToken = session.accessToken,
                    bikeModel = model,
                    weeklyRateRub = rate.toIntOrNull() ?: 0,
                    frameSerialNumber = frame,
                    motorSerialNumber = motor,
                    batterySerialNumber1 = battery1,
                    batterySerialNumber2 = battery2.ifBlank { null }
                ) { result ->
                    result.onSuccess {
                        adminMessage = "Велосипед создан"
                        showCreateBike = false
                        refresh()
                    }.onFailure { adminMessage = "Ошибка создания велосипеда: ${it.message}" }
                }
            }
        )
    }

    if (showCreateRental) {
        AdminCreateRentalDialog(
            onDismiss = { showCreateRental = false },
            onCreate = { clientId, bikeId, login, password, periodStart ->
                appViewModel.createAdminRental(
                    accessToken = session.accessToken,
                    clientId = clientId.ifBlank { null },
                    bikeId = bikeId,
                    login = login,
                    password = password,
                    periodStart = periodStart
                ) { result ->
                    result.onSuccess {
                        adminMessage = "Аренда создана"
                        showCreateRental = false
                        refresh()
                    }.onFailure { adminMessage = "Ошибка создания аренды: ${it.message}" }
                }
            }
        )
    }

    if (showUpdateClient) {
        AdminUpdateClientDialog(
            onDismiss = { showUpdateClient = false },
            onUpdate = { clientId, fullName, address, passport, phoneLabel, phoneNumber, comment ->
                appViewModel.updateAdminClient(
                    accessToken = session.accessToken,
                    clientId = clientId,
                    fullName = fullName,
                    address = address,
                    passportData = passport,
                    phoneLabel = phoneLabel,
                    phoneNumber = phoneNumber,
                    comment = comment.ifBlank { null }
                ) { result ->
                    result.onSuccess {
                        adminMessage = "Клиент обновлен"
                        showUpdateClient = false
                        refresh()
                    }.onFailure { adminMessage = "Ошибка обновления клиента: ${it.message}" }
                }
            }
        )
    }

    if (showUpdateBike) {
        AdminUpdateBikeDialog(
            onDismiss = { showUpdateBike = false },
            onUpdate = { bikeId, model, rate, frame, motor, battery1, battery2 ->
                appViewModel.updateAdminBike(
                    accessToken = session.accessToken,
                    bikeId = bikeId,
                    bikeModel = model,
                    weeklyRateRub = rate.toIntOrNull() ?: 0,
                    frameSerialNumber = frame,
                    motorSerialNumber = motor,
                    batterySerialNumber1 = battery1,
                    batterySerialNumber2 = battery2.ifBlank { null }
                ) { result ->
                    result.onSuccess {
                        adminMessage = "Велосипед обновлен"
                        showUpdateBike = false
                        refresh()
                    }.onFailure { adminMessage = "Ошибка обновления велосипеда: ${it.message}" }
                }
            }
        )
    }

    if (showUpdateRental) {
        AdminUpdateRentalDialog(
            onDismiss = { showUpdateRental = false },
            onUpdate = { rentalId, bikeId, periodStart, periodEnd, login, password ->
                appViewModel.updateAdminRental(
                    accessToken = session.accessToken,
                    rentalId = rentalId,
                    bikeId = bikeId,
                    periodStart = periodStart,
                    periodEnd = periodEnd.ifBlank { null },
                    login = login.ifBlank { null },
                    password = password.ifBlank { null }
                ) { result ->
                    result.onSuccess {
                        adminMessage = "Аренда обновлена"
                        showUpdateRental = false
                        refresh()
                    }.onFailure { adminMessage = "Ошибка обновления аренды: ${it.message}" }
                }
            }
        )
    }

    if (showFinishRentalFor != null) {
        val item = showFinishRentalFor!!
        AdminFinishRentalDialog(
            rentalId = item.rentalId.orEmpty(),
            onDismiss = { showFinishRentalFor = null },
            onApply = { rentalId, bikeId, periodStart, finishDate ->
                appViewModel.finishAdminRental(
                    accessToken = session.accessToken,
                    rentalId = rentalId,
                    bikeId = bikeId,
                    periodStart = periodStart,
                    finishDate = finishDate
                ) { result ->
                    result.onSuccess {
                        adminMessage = "Аренда завершена"
                        showFinishRentalFor = null
                        refresh()
                    }.onFailure { adminMessage = "Ошибка завершения аренды: ${it.message}" }
                }
            }
        )
    }

    if (showStartRental) {
        AdminStartRentalDialog(
            onDismiss = { showStartRental = false },
            onStart = { clientId, bikeId, login, password, periodStart ->
                appViewModel.startAdminRental(
                    accessToken = session.accessToken,
                    clientId = clientId,
                    bikeId = bikeId,
                    login = login,
                    password = password,
                    periodStart = periodStart
                ) { result ->
                    result.onSuccess {
                        adminMessage = "Аренда запущена"
                        showStartRental = false
                        refresh()
                    }.onFailure { adminMessage = "Ошибка запуска аренды: ${it.message}" }
                }
            }
        )
    }

    if (confirmDeleteRentalId != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteRentalId = null },
            title = { Text("Удалить аренду?") },
            text = { Text("Действие необратимо.") },
            confirmButton = {
                OutlinedButton(onClick = {
                    val rentalId = confirmDeleteRentalId ?: return@OutlinedButton
                    appViewModel.deleteAdminRental(session.accessToken, rentalId) { result ->
                        result.onSuccess {
                            adminMessage = "Аренда удалена"
                            confirmDeleteRentalId = null
                            refresh()
                        }.onFailure { adminMessage = "Ошибка удаления аренды: ${it.message}" }
                    }
                }) { Text("Удалить") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDeleteRentalId = null }) { Text("Отмена") } }
        )
    }

    if (confirmDeleteClientId != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteClientId = null },
            title = { Text("Удалить клиента?") },
            text = { Text("Будут удалены связанные данные клиента.") },
            confirmButton = {
                OutlinedButton(onClick = {
                    val clientId = confirmDeleteClientId ?: return@OutlinedButton
                    appViewModel.deleteAdminClient(session.accessToken, clientId) { result ->
                        result.onSuccess {
                            adminMessage = "Клиент удален"
                            confirmDeleteClientId = null
                            refresh()
                        }.onFailure { adminMessage = "Ошибка удаления клиента: ${it.message}" }
                    }
                }) { Text("Удалить") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDeleteClientId = null }) { Text("Отмена") } }
        )
    }

    if (detailClientId != null) {
        AlertDialog(
            onDismissRequest = { detailClientId = null },
            title = { Text("Детали клиента") },
            text = {
                if (isDetailLoading) {
                    CircularProgressIndicator(color = AppDesign.Accent)
                } else if (detailPayload != null) {
                    val d = detailPayload!!
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ФИО: ${d.fullName}")
                        Text("Адрес: ${d.address}")
                        Text("Паспорт: ${d.passportData}")
                        Text("Велосипед: ${d.bikeModel}")
                        Text("Долг: ${money(d.debtRub)}")
                        Text("Оплачено до: ${d.paidUntil}")
                        Text("Телефонов: ${d.phones.size}")
                        Text("Аренд в истории: ${d.rentals.size}")
                    }
                } else {
                    Text("Нет данных")
                }
            },
            confirmButton = { OutlinedButton(onClick = { detailClientId = null }) { Text("Закрыть") } }
        )
    }
}

@Composable
private fun AdminRentCard(
    item: AdminClientSummaryResponse,
    onDetails: () -> Unit
) {
    val displayName = if (item.rentalIsActive) item.fullName else "Клиент не выбран"
    val status = rentStatus(item)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(77.dp)
            .clickable { onDetails() }
            .padding(horizontal = 9.dp)
            .testTag("admin_rent_card_${item.rentalId ?: item.clientId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(59.dp)
                .background(Color(0xFFE3E6EB), RoundedCornerShape(12.dp))
                .border(
                    width = 3.dp,
                    color = avatarBorderColor(item),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Outlined.DirectionsBike,
                contentDescription = null,
                tint = AppDesign.IconSoft,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(
            modifier = Modifier.width(136.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = displayName,
                color = Color(0xFF111827),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.bikeModel,
                color = Color(0x80111827),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Корректировка: ${money(item.totalAdjustmentRub)}",
                color = Color(0x80111827),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .width(status.widthDp.dp)
                .height(44.dp)
                .background(status.color, RoundedCornerShape(15.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = status.title,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = status.value,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

private fun rentStatus(item: AdminClientSummaryResponse): RentStatusPill {
    if (!item.rentalIsActive) {
        return RentStatusPill(
            title = "У меня",
            value = "—",
            color = Color(0xFF141718),
            widthDp = 108
        )
    }

    if (item.debtRub > 0) {
        return RentStatusPill(
            title = "Долг",
            value = money(item.debtRub),
            color = Color(red = 214f / 255f, green = 48f / 255f, blue = 52f / 255f),
            widthDp = 108
        )
    }

    return RentStatusPill(
        title = "Оплачено на",
        value = paidDaysText(item),
        color = Color(red = 35f / 255f, green = 143f / 255f, blue = 71f / 255f),
        widthDp = 108
    )
}

private fun avatarBorderColor(item: AdminClientSummaryResponse): Color {
    return when (item.rentalPipelineStatus.orEmpty().trim().lowercase()) {
        "in_stock", "mine" -> Color(red = 203f / 255f, green = 48f / 255f, blue = 224f / 255f)
        "soon_return" -> Color(red = 1f, green = 204f / 255f, blue = 0f)
        "long_term" -> Color(red = 52f / 255f, green = 199f / 255f, blue = 89f / 255f)
        else -> if (item.rentalIsActive) {
            Color(red = 52f / 255f, green = 199f / 255f, blue = 89f / 255f)
        } else {
            Color(red = 203f / 255f, green = 48f / 255f, blue = 224f / 255f)
        }
    }
}

private fun paidDaysText(item: AdminClientSummaryResponse): String {
    val daysFromStatus = Regex("\\d+").find(item.statusText.lowercase())?.value?.toIntOrNull()
    if (daysFromStatus != null) {
        return dayWord(daysFromStatus)
    }
    return "—"
}

private fun dayWord(days: Int): String {
    val mod10 = days % 10
    val mod100 = days % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "$days день"
        mod10 in 2..4 && mod100 !in 12..14 -> "$days дня"
        else -> "$days дней"
    }
}

@Composable
private fun AdminCreateClientDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var passport by remember { mutableStateOf("") }
    var phoneLabel by remember { mutableStateOf("main") }
    var phoneNumber by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать клиента") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fullName, { fullName = it }, label = { Text("ФИО") })
                OutlinedTextField(address, { address = it }, label = { Text("Адрес") })
                OutlinedTextField(passport, { passport = it }, label = { Text("Паспорт") })
                OutlinedTextField(phoneLabel, { phoneLabel = it }, label = { Text("Метка телефона") })
                OutlinedTextField(phoneNumber, { phoneNumber = it }, label = { Text("Телефон") })
            }
        },
        confirmButton = { OutlinedButton(onClick = { onCreate(fullName, address, passport, phoneLabel, phoneNumber) }) { Text("Создать") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun AdminCreateBikeDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String, String) -> Unit
) {
    var model by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var frame by remember { mutableStateOf("") }
    var motor by remember { mutableStateOf("") }
    var battery1 by remember { mutableStateOf("") }
    var battery2 by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать велосипед") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(model, { model = it }, label = { Text("Модель") })
                OutlinedTextField(rate, { rate = it }, label = { Text("Ставка/нед (₽)") })
                OutlinedTextField(frame, { frame = it }, label = { Text("Frame SN") })
                OutlinedTextField(motor, { motor = it }, label = { Text("Motor SN") })
                OutlinedTextField(battery1, { battery1 = it }, label = { Text("Battery 1 SN") })
                OutlinedTextField(battery2, { battery2 = it }, label = { Text("Battery 2 SN") })
            }
        },
        confirmButton = { OutlinedButton(onClick = { onCreate(model, rate, frame, motor, battery1, battery2) }) { Text("Создать") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun AdminCreateRentalDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String) -> Unit
) {
    var clientId by remember { mutableStateOf("") }
    var bikeId by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var periodStart by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать аренду") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(clientId, { clientId = it }, label = { Text("Client ID (optional)") })
                OutlinedTextField(bikeId, { bikeId = it }, label = { Text("Bike ID") })
                OutlinedTextField(login, { login = it }, label = { Text("Логин клиента") })
                OutlinedTextField(password, { password = it }, label = { Text("Пароль клиента") })
                OutlinedTextField(periodStart, { periodStart = it }, label = { Text("Дата начала YYYY-MM-DD") })
            }
        },
        confirmButton = { OutlinedButton(onClick = { onCreate(clientId, bikeId, login, password, periodStart) }) { Text("Создать") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun AdminUpdateClientDialog(
    onDismiss: () -> Unit,
    onUpdate: (String, String, String, String, String, String, String) -> Unit
) {
    var clientId by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var passport by remember { mutableStateOf("") }
    var phoneLabel by remember { mutableStateOf("main") }
    var phoneNumber by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Обновить клиента") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(clientId, { clientId = it }, label = { Text("Client ID") })
                OutlinedTextField(fullName, { fullName = it }, label = { Text("ФИО") })
                OutlinedTextField(address, { address = it }, label = { Text("Адрес") })
                OutlinedTextField(passport, { passport = it }, label = { Text("Паспорт") })
                OutlinedTextField(phoneLabel, { phoneLabel = it }, label = { Text("Метка телефона") })
                OutlinedTextField(phoneNumber, { phoneNumber = it }, label = { Text("Телефон") })
                OutlinedTextField(comment, { comment = it }, label = { Text("Комментарий") })
            }
        },
        confirmButton = { OutlinedButton(onClick = { onUpdate(clientId, fullName, address, passport, phoneLabel, phoneNumber, comment) }) { Text("Сохранить") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun AdminUpdateBikeDialog(
    onDismiss: () -> Unit,
    onUpdate: (String, String, String, String, String, String, String) -> Unit
) {
    var bikeId by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var frame by remember { mutableStateOf("") }
    var motor by remember { mutableStateOf("") }
    var battery1 by remember { mutableStateOf("") }
    var battery2 by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Обновить велосипед") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(bikeId, { bikeId = it }, label = { Text("Bike ID") })
                OutlinedTextField(model, { model = it }, label = { Text("Модель") })
                OutlinedTextField(rate, { rate = it }, label = { Text("Ставка/нед (₽)") })
                OutlinedTextField(frame, { frame = it }, label = { Text("Frame SN") })
                OutlinedTextField(motor, { motor = it }, label = { Text("Motor SN") })
                OutlinedTextField(battery1, { battery1 = it }, label = { Text("Battery 1 SN") })
                OutlinedTextField(battery2, { battery2 = it }, label = { Text("Battery 2 SN") })
            }
        },
        confirmButton = { OutlinedButton(onClick = { onUpdate(bikeId, model, rate, frame, motor, battery1, battery2) }) { Text("Сохранить") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun AdminUpdateRentalDialog(
    onDismiss: () -> Unit,
    onUpdate: (String, String, String, String, String, String) -> Unit
) {
    var rentalId by remember { mutableStateOf("") }
    var bikeId by remember { mutableStateOf("") }
    var periodStart by remember { mutableStateOf("") }
    var periodEnd by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Обновить аренду") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rentalId, { rentalId = it }, label = { Text("Rental ID") })
                OutlinedTextField(bikeId, { bikeId = it }, label = { Text("Bike ID") })
                OutlinedTextField(periodStart, { periodStart = it }, label = { Text("Дата начала YYYY-MM-DD") })
                OutlinedTextField(periodEnd, { periodEnd = it }, label = { Text("Дата конца YYYY-MM-DD (optional)") })
                OutlinedTextField(login, { login = it }, label = { Text("Логин (optional)") })
                OutlinedTextField(password, { password = it }, label = { Text("Пароль (optional)") })
            }
        },
        confirmButton = { OutlinedButton(onClick = { onUpdate(rentalId, bikeId, periodStart, periodEnd, login, password) }) { Text("Сохранить") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun AdminFinishRentalDialog(
    rentalId: String,
    onDismiss: () -> Unit,
    onApply: (String, String, String, String) -> Unit
) {
    var rentalIdInput by remember { mutableStateOf(rentalId) }
    var bikeId by remember { mutableStateOf("") }
    var periodStart by remember { mutableStateOf("") }
    var finishDate by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Завершить аренду") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rentalIdInput, { rentalIdInput = it }, label = { Text("Rental ID") })
                OutlinedTextField(bikeId, { bikeId = it }, label = { Text("Bike ID") })
                OutlinedTextField(periodStart, { periodStart = it }, label = { Text("Дата начала YYYY-MM-DD") })
                OutlinedTextField(finishDate, { finishDate = it }, label = { Text("YYYY-MM-DD") })
            }
        },
        confirmButton = { OutlinedButton(onClick = { onApply(rentalIdInput, bikeId, periodStart, finishDate) }) { Text("Завершить") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun AdminStartRentalDialog(
    onDismiss: () -> Unit,
    onStart: (String, String, String, String, String) -> Unit
) {
    var clientId by remember { mutableStateOf("") }
    var bikeId by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var periodStart by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Запустить аренду") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(clientId, { clientId = it }, label = { Text("Client ID") })
                OutlinedTextField(bikeId, { bikeId = it }, label = { Text("Bike ID") })
                OutlinedTextField(login, { login = it }, label = { Text("Логин") })
                OutlinedTextField(password, { password = it }, label = { Text("Пароль") })
                OutlinedTextField(periodStart, { periodStart = it }, label = { Text("Дата старта YYYY-MM-DD") })
            }
        },
        confirmButton = { OutlinedButton(onClick = { onStart(clientId, bikeId, login, password, periodStart) }) { Text("Запустить") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun StatBlock(title: String, value: String, valueColor: Color) {
    Column(modifier = Modifier.widthIn(min = 72.dp)) {
        Text(title, fontSize = 10.sp, color = AppDesign.SubtleText)
        Text(value, fontSize = 13.sp, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

private fun amountForType(type: ClientPaymentType, dashboard: ClientDashboardResponse): Int {
    return when (type) {
        ClientPaymentType.Day -> dashboard.presets.dayRub
        ClientPaymentType.Week -> dashboard.presets.weekRub
        ClientPaymentType.TwoWeeks -> dashboard.presets.twoWeeksRub
        ClientPaymentType.Month -> dashboard.presets.monthRub
        ClientPaymentType.DebtExact -> dashboard.presets.debtExactRub
    }
}

private fun money(value: Int): String = "${DecimalFormat("#,###").format(value).replace(',', ' ')} ₽"

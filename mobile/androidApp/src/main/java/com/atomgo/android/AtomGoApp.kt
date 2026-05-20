package com.atomgo.android

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
                fontWeight = FontWeight.Medium,
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

private enum class AdminClientFilter {
    All,
    Debtors,
    Active
}

private enum class AdminBikeFilter {
    All,
    Free,
    Rented
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

private data class AdminClientFilterCounters(
    val all: Int,
    val debtors: Int,
    val active: Int
)

private data class AdminBikeFilterCounters(
    val all: Int,
    val free: Int,
    val rented: Int
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
    placeholder: String,
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
                tint = AppDesign.TitleText,
                modifier = Modifier.size(14.dp)
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
                            text = placeholder,
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
private fun AdminFilterHitRows(
    enabled: Boolean,
    onSelect: (AdminRentFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminFilterHitTarget(width = 84.dp, enabled = enabled) { onSelect(AdminRentFilter.All) }
            AdminFilterHitTarget(width = 146.dp, enabled = enabled) { onSelect(AdminRentFilter.SoonReturn) }
            AdminFilterHitTarget(width = 132.dp, enabled = enabled) { onSelect(AdminRentFilter.Debtors) }
        }
        AdminFilterHitTarget(width = 108.dp, enabled = enabled) { onSelect(AdminRentFilter.Mine) }
    }
}

@Composable
private fun AdminFilterHitTarget(
    width: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(36.dp)
            .clickable(enabled = enabled, onClick = onClick)
    )
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
        targetValue = if (isSelected) Color.White.copy(alpha = 0.2f) else Color(0xFFE5E5E8),
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("admin_bottom_tab_bar"),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 2.dp, bottom = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 414.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
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
            .padding(vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else unselectedIcon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(if (tab == AdminHomeTab.Bikes) 21.dp else 19.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = title,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
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
    var clientsCatalog by remember { mutableStateOf<List<AdminClientSummaryResponse>>(emptyList()) }
    var bikesCatalog by remember { mutableStateOf<List<AdminBikeResponse>>(emptyList()) }
    var isRentsLoading by remember { mutableStateOf(true) }
    var rentsError by remember { mutableStateOf<String?>(null) }
    var isClientsLoading by remember { mutableStateOf(false) }
    var clientsError by remember { mutableStateOf<String?>(null) }
    var isBikesLoading by remember { mutableStateOf(false) }
    var bikesError by remember { mutableStateOf<String?>(null) }
    var rentsFilter by remember { mutableStateOf(AdminRentFilter.All) }
    var rentsSearch by remember { mutableStateOf("") }
    var clientsFilter by remember { mutableStateOf(AdminClientFilter.All) }
    var clientsSearch by remember { mutableStateOf("") }
    var bikesFilter by remember { mutableStateOf(AdminBikeFilter.All) }
    var bikesSearch by remember { mutableStateOf("") }
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
    var selectedRentalDetails by remember { mutableStateOf<AdminRentalPreview?>(null) }
    var isRentalDetailsLoading by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    fun refreshRents() {
        isRentsLoading = true
        rentsError = null
        appViewModel.fetchAdminRents(session.accessToken) { result ->
            result.onSuccess {
                rents = it
                isRentsLoading = false
            }.onFailure {
                rentsError = it.message ?: "Ошибка загрузки"
                isRentsLoading = false
            }
        }
    }
    fun refreshClients() {
        isClientsLoading = true
        clientsError = null
        appViewModel.fetchAdminClients(session.accessToken) { result ->
            result.onSuccess {
                clientsCatalog = it
                isClientsLoading = false
            }.onFailure {
                clientsError = it.message ?: "Ошибка загрузки"
                isClientsLoading = false
            }
        }
    }
    fun refreshBikes() {
        isBikesLoading = true
        bikesError = null
        appViewModel.fetchAdminBikes(session.accessToken) { result ->
            result.onSuccess {
                bikesCatalog = it
                isBikesLoading = false
            }.onFailure {
                bikesError = it.message ?: "Ошибка загрузки"
                isBikesLoading = false
            }
        }
    }
    fun refreshAllCatalogs() {
        refreshRents()
        refreshClients()
        refreshBikes()
    }
    fun openClientDetails(clientId: String) {
        isDetailLoading = true
        detailPayload = null
        detailClientId = clientId
        appViewModel.fetchAdminClientDetails(session.accessToken, clientId) { result ->
            result.onSuccess {
                detailPayload = it
                isDetailLoading = false
            }.onFailure {
                adminMessage = "Ошибка загрузки деталей клиента: ${it.message}"
                isDetailLoading = false
            }
        }
    }
    fun openRentalDetailsFromSummary(summary: AdminClientSummaryResponse) {
        selectedRentalDetails = summary.rentalId?.let { rentalId ->
            AdminRentalPreview.fromSummary(summary = summary, rentalId = rentalId)
        }
        isRentalDetailsLoading = true
        if (summary.clientId.isNotBlank()) {
            appViewModel.fetchAdminClientDetails(session.accessToken, summary.clientId) { result ->
                result.onSuccess { client ->
                    val matching = client.rentals.firstOrNull { it.rentalId == summary.rentalId }
                    selectedRentalDetails = if (matching != null) {
                        AdminRentalPreview.fromHistory(client = client, rental = matching)
                    } else {
                        selectedRentalDetails ?: AdminRentalPreview.fromSummary(summary = summary, rentalId = summary.rentalId.orEmpty())
                    }
                    isRentalDetailsLoading = false
                }.onFailure {
                    adminMessage = "Ошибка загрузки аренды: ${it.message}"
                    isRentalDetailsLoading = false
                }
            }
        } else {
            isRentalDetailsLoading = false
        }
    }
    LaunchedEffect(Unit) { refreshAllCatalogs() }
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            AdminHomeTab.Clients -> if (clientsCatalog.isEmpty() && !isClientsLoading) refreshClients()
            AdminHomeTab.Bikes -> if (bikesCatalog.isEmpty() && !isBikesLoading) refreshBikes()
            AdminHomeTab.Rents -> Unit
        }
    }
    LaunchedEffect(adminMessage) {
        val text = adminMessage?.trim().orEmpty()
        if (text.isNotEmpty()) {
            toastMessage = text
            delay(2200)
            toastMessage = null
        }
    }

    val normalizedQuery = rentsSearch.trim()
    val searchedRents = rents.filter { item ->
        normalizedQuery.isEmpty() ||
            item.fullName.contains(normalizedQuery, ignoreCase = true) ||
            item.bikeModel.contains(normalizedQuery, ignoreCase = true) ||
            (item.clientLogin ?: "").contains(normalizedQuery, ignoreCase = true)
    }
    val filteredRents = searchedRents.filter { item ->
        when (rentsFilter) {
            AdminRentFilter.All -> true
            AdminRentFilter.SoonReturn -> item.rentalIsActive && item.rentalPipelineStatus.orEmpty() == "soon_return"
            AdminRentFilter.Debtors -> item.debtRub > 0
            AdminRentFilter.Mine -> !item.rentalIsActive
        }
    }
    val normalizedClientQuery = clientsSearch.trim()
    val filteredClients = clientsCatalog.filter { item ->
        normalizedClientQuery.isEmpty() ||
            item.fullName.contains(normalizedClientQuery, ignoreCase = true) ||
            item.bikeModel.contains(normalizedClientQuery, ignoreCase = true) ||
            (item.clientLogin ?: "").contains(normalizedClientQuery, ignoreCase = true)
    }.filter { item ->
        when (clientsFilter) {
            AdminClientFilter.All -> true
            AdminClientFilter.Debtors -> item.debtRub > 0
            AdminClientFilter.Active -> item.rentalIsActive
        }
    }
    val normalizedBikeQuery = bikesSearch.trim()
    val filteredBikes = bikesCatalog.filter { bike ->
        normalizedBikeQuery.isEmpty() ||
            bike.bikeModel.contains(normalizedBikeQuery, ignoreCase = true) ||
            bike.frameSerialNumber.contains(normalizedBikeQuery, ignoreCase = true) ||
            bike.motorSerialNumber.contains(normalizedBikeQuery, ignoreCase = true) ||
            bike.batterySerialNumber1.contains(normalizedBikeQuery, ignoreCase = true) ||
            (bike.batterySerialNumber2?.contains(normalizedBikeQuery, ignoreCase = true) == true)
    }.filter { bike ->
        when (bikesFilter) {
            AdminBikeFilter.All -> true
            AdminBikeFilter.Free -> !bike.bikeIsInRental
            AdminBikeFilter.Rented -> bike.bikeIsInRental
        }
    }.sortedBy { it.bikeModel.lowercase() }

    val filterCounts = AdminFilterCounters(
        all = rents.size,
        soonReturn = rents.count { it.rentalIsActive && it.rentalPipelineStatus.orEmpty() == "soon_return" },
        debtors = rents.count { it.debtRub > 0 },
        mine = rents.count { !it.rentalIsActive }
    )
    val clientsFilterCounts = AdminClientFilterCounters(
        all = clientsCatalog.size,
        debtors = clientsCatalog.count { it.debtRub > 0 },
        active = clientsCatalog.count { it.rentalIsActive }
    )
    val bikesFilterCounts = AdminBikeFilterCounters(
        all = bikesCatalog.size,
        free = bikesCatalog.count { !it.bikeIsInRental },
        rented = bikesCatalog.count { it.bikeIsInRental }
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
                val horizontalInset = 8.dp
                val topBarHeight = 62.dp
                val searchTopPadding = 6.dp
                val searchHeight = 46.dp
                val chipsTopGap = 10.dp
                val chipsHeight = 80.dp
                val chipsTop = statusBarTop + topBarHeight + searchTopPadding + searchHeight + chipsTopGap
                val cardsInitialTop = chipsTop + chipsHeight + chipsTopGap
                val searchMaskHeight = statusBarTop + topBarHeight + searchTopPadding + (searchHeight / 2)
                val bottomCardsInset = 120.dp
                val searchMaskHeightPx = with(density) { searchMaskHeight.toPx() }
                val rentsListState = rememberLazyListState()
                val filtersInteractive by remember {
                    derivedStateOf {
                        when (rentsListState.firstVisibleItemIndex) {
                            0 -> rentsListState.firstVisibleItemScrollOffset < 10
                            else -> false
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalInset)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chipsHeight)
                            .offset(y = chipsTop)
                            .zIndex(1f)
                    ) {
                        AdminFilterRows(
                            selectedFilter = rentsFilter,
                            counts = filterCounts,
                            onSelect = { rentsFilter = it }
                        )
                    }

                    when {
                        isRentsLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(2f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = AppDesign.Accent)
                            }
                        }

                        rentsError != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = cardsInitialTop)
                                    .zIndex(2f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("Не удалось загрузить аренды", color = AppDesign.Danger, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(rentsError.orEmpty(), color = AppDesign.SubtleText)
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(onClick = ::refreshRents) { Text("Повторить") }
                            }
                        }

                        else -> {
                            Crossfade(
                                targetState = filteredRents,
                                animationSpec = tween(durationMillis = 180),
                                label = "admin_rents_crossfade",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawWithContent {
                                        clipRect(
                                            left = 0f,
                                            top = searchMaskHeightPx,
                                            right = size.width,
                                            bottom = size.height
                                        ) {
                                            this@drawWithContent.drawContent()
                                        }
                                    }
                                    .zIndex(2f)
                            ) { visibleRents ->
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("admin_rents_list"),
                                    state = rentsListState,
                                    contentPadding = PaddingValues(bottom = bottomCardsInset)
                                ) {
                                    item("admin_rents_top_spacer") {
                                        Spacer(Modifier.height(cardsInitialTop))
                                    }
                                    if (visibleRents.isEmpty()) {
                                        item("admin_rents_empty") {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 14.dp),
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
                                        }
                                    } else {
                                        item("admin_rents_container") {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("admin_rents_container"),
                                                shape = RoundedCornerShape(15.dp),
                                                color = Color(0xFFFAFBFB),
                                                shadowElevation = 8.dp,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, AppDesign.Accent)
                                            ) {
                                                Column(modifier = Modifier.padding(vertical = 5.dp)) {
                                                    visibleRents.forEachIndexed { index, item ->
                                                        AdminRentCard(
                                                            item = item,
                                                            isFirst = index == 0,
                                                            onDetails = { openRentalDetailsFromSummary(item) }
                                                        )
                                                        if (index < visibleRents.lastIndex) {
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

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chipsHeight)
                            .offset(y = chipsTop)
                            .zIndex(3.5f)
                    ) {
                        AdminFilterHitRows(
                            enabled = filtersInteractive,
                            onSelect = { rentsFilter = it }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(searchMaskHeight)
                            .background(AppDesign.PageBackground)
                            .zIndex(3f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(4f)
                    ) {
                        Spacer(Modifier.height(statusBarTop))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(topBarHeight),
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

                        Spacer(Modifier.height(searchTopPadding))

                        AdminSearchField(
                            value = rentsSearch,
                            onValueChange = { rentsSearch = it },
                            placeholder = "Поиск по клиенту, велосипеду...",
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_search_field")
                        )
                    }
                }
            }

            AdminHomeTab.Clients -> {
                AdminClientsCatalogScreen(
                    statusBarTop = statusBarTop,
                    clients = clientsCatalog,
                    isLoading = isClientsLoading,
                    error = clientsError,
                    search = clientsSearch,
                    onSearchChange = { clientsSearch = it },
                    selectedFilter = clientsFilter,
                    filterCounts = clientsFilterCounts,
                    onFilterSelect = { clientsFilter = it },
                    onRetry = ::refreshClients,
                    onLogout = onLogout,
                    onCreate = { showCreateClient = true },
                    onOpenClient = { client -> openClientDetails(client.clientId) },
                    visibleClients = filteredClients
                )
            }

            AdminHomeTab.Bikes -> {
                AdminBikesCatalogScreen(
                    statusBarTop = statusBarTop,
                    bikes = bikesCatalog,
                    rentals = clientsCatalog,
                    isLoading = isBikesLoading,
                    error = bikesError,
                    search = bikesSearch,
                    onSearchChange = { bikesSearch = it },
                    selectedFilter = bikesFilter,
                    filterCounts = bikesFilterCounts,
                    onFilterSelect = { bikesFilter = it },
                    onRetry = ::refreshBikes,
                    onLogout = onLogout,
                    onCreate = { showCreateBike = true },
                    visibleBikes = filteredBikes
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

    AnimatedVisibility(
        visible = showCreateClient,
        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(initialOffsetY = { it }, animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180)),
        modifier = Modifier.fillMaxSize().zIndex(12f)
    ) {
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
                        refreshAllCatalogs()
                    }.onFailure { adminMessage = "Ошибка создания клиента: ${it.message}" }
                }
            }
        )
    }

    AnimatedVisibility(
        visible = showCreateBike,
        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(initialOffsetY = { it }, animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180)),
        modifier = Modifier.fillMaxSize().zIndex(12f)
    ) {
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
                        refreshAllCatalogs()
                    }.onFailure { adminMessage = "Ошибка создания велосипеда: ${it.message}" }
                }
            }
        )
    }

    AnimatedVisibility(
        visible = showCreateRental,
        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(initialOffsetY = { it }, animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180)),
        modifier = Modifier.fillMaxSize().zIndex(12f)
    ) {
        AdminCreateRentalDialog(
            clients = clientsCatalog,
            bikes = bikesCatalog,
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
                        refreshAllCatalogs()
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
                        refreshAllCatalogs()
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
                        refreshAllCatalogs()
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
                        refreshAllCatalogs()
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
                        refreshAllCatalogs()
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
                        refreshAllCatalogs()
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
                            refreshAllCatalogs()
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
                            refreshAllCatalogs()
                        }.onFailure { adminMessage = "Ошибка удаления клиента: ${it.message}" }
                    }
                }) { Text("Удалить") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDeleteClientId = null }) { Text("Отмена") } }
        )
    }

    AnimatedVisibility(
        visible = detailClientId != null,
        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(initialOffsetY = { it }, animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180)),
        modifier = Modifier.fillMaxSize().zIndex(21f)
    ) {
        AdminClientDetailsScreen(
            details = detailPayload,
            isLoading = isDetailLoading,
            onClose = { detailClientId = null },
            onRetry = { detailClientId?.let(::openClientDetails) },
            onEditProfile = {
                showUpdateClient = true
            },
            onDeleteClient = { clientId ->
                confirmDeleteClientId = clientId
            },
            onOpenRental = { preview ->
                selectedRentalDetails = preview
                isRentalDetailsLoading = false
            }
        )
    }

    AnimatedVisibility(
        visible = selectedRentalDetails != null,
        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(initialOffsetY = { it }, animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180)),
        modifier = Modifier.fillMaxSize().zIndex(22f)
    ) {
        AdminRentalDetailsScreenAndroid(
            details = selectedRentalDetails,
            isLoading = isRentalDetailsLoading,
            onClose = { selectedRentalDetails = null },
            onOpenClient = {
                val clientId = selectedRentalDetails?.clientId?.trim().orEmpty()
                selectedRentalDetails = null
                if (clientId.isNotEmpty()) {
                    openClientDetails(clientId)
                } else {
                    adminMessage = "Клиент для этой аренды не найден"
                }
            },
            onDelete = {
                val rentalId = selectedRentalDetails?.rentalId ?: return@AdminRentalDetailsScreenAndroid
                appViewModel.deleteAdminRental(session.accessToken, rentalId) { result ->
                    result.onSuccess {
                        adminMessage = "Аренда удалена"
                        selectedRentalDetails = null
                        refreshAllCatalogs()
                    }.onFailure { adminMessage = "Ошибка удаления аренды: ${it.message}" }
                }
            }
        )
    }
}

@Composable
private fun AdminClientsCatalogScreen(
    statusBarTop: androidx.compose.ui.unit.Dp,
    clients: List<AdminClientSummaryResponse>,
    isLoading: Boolean,
    error: String?,
    search: String,
    onSearchChange: (String) -> Unit,
    selectedFilter: AdminClientFilter,
    filterCounts: AdminClientFilterCounters,
    onFilterSelect: (AdminClientFilter) -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    onCreate: () -> Unit,
    onOpenClient: (AdminClientSummaryResponse) -> Unit,
    visibleClients: List<AdminClientSummaryResponse>
) {
    val density = LocalDensity.current
    val horizontalInset = 8.dp
    val topBarHeight = 62.dp
    val searchTopPadding = 6.dp
    val searchHeight = 46.dp
    val chipsTopGap = 10.dp
    val chipsHeight = 36.dp
    val chipsTop = statusBarTop + topBarHeight + searchTopPadding + searchHeight + chipsTopGap
    val cardsInitialTop = chipsTop + chipsHeight + chipsTopGap
    val searchMaskHeight = statusBarTop + topBarHeight + searchTopPadding + (searchHeight / 2)
    val searchMaskHeightPx = with(density) { searchMaskHeight.toPx() }
    val listState = rememberLazyListState()
    val filtersInteractive by remember {
        derivedStateOf {
            when (listState.firstVisibleItemIndex) {
                0 -> listState.firstVisibleItemScrollOffset < 10
                else -> false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalInset)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chipsHeight)
                .offset(y = chipsTop)
                .zIndex(1f)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminFilterChip(
                    title = "Все",
                    count = filterCounts.all,
                    width = 84.dp,
                    isSelected = selectedFilter == AdminClientFilter.All,
                    testTag = "admin_client_filter_all",
                    onClick = { onFilterSelect(AdminClientFilter.All) }
                )
                AdminFilterChip(
                    title = "Должники",
                    count = filterCounts.debtors,
                    width = 106.dp,
                    isSelected = selectedFilter == AdminClientFilter.Debtors,
                    testTag = "admin_client_filter_debtors",
                    onClick = { onFilterSelect(AdminClientFilter.Debtors) }
                )
                AdminFilterChip(
                    title = "Активные",
                    count = filterCounts.active,
                    width = 106.dp,
                    isSelected = selectedFilter == AdminClientFilter.Active,
                    testTag = "admin_client_filter_active",
                    onClick = { onFilterSelect(AdminClientFilter.Active) }
                )
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppDesign.Accent)
                }
            }
            error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = cardsInitialTop)
                        .zIndex(2f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Не удалось загрузить клиентов", color = AppDesign.Danger, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(error, color = AppDesign.SubtleText)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onRetry) { Text("Повторить") }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(
                                left = 0f,
                                top = searchMaskHeightPx,
                                right = size.width,
                                bottom = size.height
                            ) {
                                this@drawWithContent.drawContent()
                            }
                        }
                        .zIndex(2f)
                        .testTag("admin_clients_list"),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    item("admin_clients_top_spacer") {
                        Spacer(Modifier.height(cardsInitialTop))
                    }
                    if (visibleClients.isEmpty()) {
                        item("admin_clients_empty") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp),
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
                                    Text("Список клиентов пуст", color = AppDesign.TitleText, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        item("admin_clients_container") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(15.dp),
                                color = Color(0xFFFAFBFB),
                                shadowElevation = 8.dp,
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, AppDesign.Accent)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 5.dp)) {
                                    visibleClients.forEachIndexed { index, item ->
                                        AdminClientCatalogRow(
                                            item = item,
                                            isFirst = index == 0,
                                            onClick = { onOpenClient(item) }
                                        )
                                        if (index < visibleClients.lastIndex) {
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chipsHeight)
                .offset(y = chipsTop)
                .zIndex(3.5f)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminFilterHitTarget(width = 84.dp, enabled = filtersInteractive) { onFilterSelect(AdminClientFilter.All) }
                AdminFilterHitTarget(width = 106.dp, enabled = filtersInteractive) { onFilterSelect(AdminClientFilter.Debtors) }
                AdminFilterHitTarget(width = 106.dp, enabled = filtersInteractive) { onFilterSelect(AdminClientFilter.Active) }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(searchMaskHeight)
                .background(AppDesign.PageBackground)
                .zIndex(3f)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(4f)
        ) {
            Spacer(Modifier.height(statusBarTop))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topBarHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AdminSquareTopButton(
                    iconRes = R.drawable.ic_admin_exit,
                    testTag = "admin_clients_logout_button",
                    onClick = onLogout
                )
                Spacer(Modifier.weight(1f))
                Text("Клиенты", color = Color(0xFF141718), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                AdminSquareTopButton(
                    iconRes = R.drawable.ic_admin_plus,
                    testTag = "admin_clients_create_button",
                    onClick = onCreate
                )
            }
            Spacer(Modifier.height(searchTopPadding))
            AdminSearchField(
                value = search,
                onValueChange = onSearchChange,
                placeholder = "Поиск: ФИО, телефон, паспорт",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AdminBikesCatalogScreen(
    statusBarTop: androidx.compose.ui.unit.Dp,
    bikes: List<AdminBikeResponse>,
    rentals: List<AdminClientSummaryResponse>,
    isLoading: Boolean,
    error: String?,
    search: String,
    onSearchChange: (String) -> Unit,
    selectedFilter: AdminBikeFilter,
    filterCounts: AdminBikeFilterCounters,
    onFilterSelect: (AdminBikeFilter) -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    onCreate: () -> Unit,
    visibleBikes: List<AdminBikeResponse>
) {
    val density = LocalDensity.current
    val horizontalInset = 8.dp
    val topBarHeight = 62.dp
    val searchTopPadding = 6.dp
    val searchHeight = 46.dp
    val chipsTopGap = 10.dp
    val chipsHeight = 36.dp
    val chipsTop = statusBarTop + topBarHeight + searchTopPadding + searchHeight + chipsTopGap
    val cardsInitialTop = chipsTop + chipsHeight + chipsTopGap
    val searchMaskHeight = statusBarTop + topBarHeight + searchTopPadding + (searchHeight / 2)
    val searchMaskHeightPx = with(density) { searchMaskHeight.toPx() }
    val listState = rememberLazyListState()
    val filtersInteractive by remember {
        derivedStateOf {
            when (listState.firstVisibleItemIndex) {
                0 -> listState.firstVisibleItemScrollOffset < 10
                else -> false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalInset)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chipsHeight)
                .offset(y = chipsTop)
                .zIndex(1f)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminFilterChip(
                    title = "Все",
                    count = filterCounts.all,
                    width = 84.dp,
                    isSelected = selectedFilter == AdminBikeFilter.All,
                    testTag = "admin_bike_filter_all",
                    onClick = { onFilterSelect(AdminBikeFilter.All) }
                )
                AdminFilterChip(
                    title = "Свободные",
                    count = filterCounts.free,
                    width = 128.dp,
                    isSelected = selectedFilter == AdminBikeFilter.Free,
                    testTag = "admin_bike_filter_free",
                    onClick = { onFilterSelect(AdminBikeFilter.Free) }
                )
                AdminFilterChip(
                    title = "В аренде",
                    count = filterCounts.rented,
                    width = 110.dp,
                    isSelected = selectedFilter == AdminBikeFilter.Rented,
                    testTag = "admin_bike_filter_rented",
                    onClick = { onFilterSelect(AdminBikeFilter.Rented) }
                )
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppDesign.Accent)
                }
            }
            error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = cardsInitialTop)
                        .zIndex(2f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Не удалось загрузить велосипеды", color = AppDesign.Danger, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(error, color = AppDesign.SubtleText)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onRetry) { Text("Повторить") }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(
                                left = 0f,
                                top = searchMaskHeightPx,
                                right = size.width,
                                bottom = size.height
                            ) {
                                this@drawWithContent.drawContent()
                            }
                        }
                        .zIndex(2f)
                        .testTag("admin_bikes_list"),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    item("admin_bikes_top_spacer") {
                        Spacer(Modifier.height(cardsInitialTop))
                    }
                    if (visibleBikes.isEmpty()) {
                        item("admin_bikes_empty") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp),
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
                                    Text("Список велосипедов пуст", color = AppDesign.TitleText, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        item("admin_bikes_container") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(15.dp),
                                color = Color(0xFFFAFBFB),
                                shadowElevation = 8.dp,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AppDesign.Accent)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 5.dp)) {
                                    visibleBikes.forEachIndexed { index, bike ->
                                        AdminBikeCatalogRow(
                                            bike = bike,
                                            runtime = bikeCatalogRuntimeSnapshot(bike = bike, rentals = rentals)
                                        )
                                        if (index < visibleBikes.lastIndex) {
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chipsHeight)
                .offset(y = chipsTop)
                .zIndex(3.5f)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminFilterHitTarget(width = 84.dp, enabled = filtersInteractive) { onFilterSelect(AdminBikeFilter.All) }
                AdminFilterHitTarget(width = 128.dp, enabled = filtersInteractive) { onFilterSelect(AdminBikeFilter.Free) }
                AdminFilterHitTarget(width = 110.dp, enabled = filtersInteractive) { onFilterSelect(AdminBikeFilter.Rented) }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(searchMaskHeight)
                .background(AppDesign.PageBackground)
                .zIndex(3f)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(4f)
        ) {
            Spacer(Modifier.height(statusBarTop))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topBarHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AdminSquareTopButton(
                    iconRes = R.drawable.ic_admin_exit,
                    testTag = "admin_bikes_logout_button",
                    onClick = onLogout
                )
                Spacer(Modifier.weight(1f))
                Text("Велосипеды", color = Color(0xFF141718), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                AdminSquareTopButton(
                    iconRes = R.drawable.ic_admin_plus,
                    testTag = "admin_bikes_create_button",
                    onClick = onCreate
                )
            }
            Spacer(Modifier.height(searchTopPadding))
            AdminSearchField(
                value = search,
                onValueChange = onSearchChange,
                placeholder = "Поиск: модель, серийный номер",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AdminClientCatalogRow(
    item: AdminClientSummaryResponse,
    isFirst: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(77.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp)
            .testTag(if (isFirst) "admin_client_row_first" else "admin_client_row_${item.clientId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .border(1.5.dp, Color(0xFF34C759), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Phone,
                contentDescription = null,
                tint = Color(0xFF34C759),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = item.fullName,
                color = Color(0xFF111827),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = clientCatalogSubtitle(item),
                color = Color(0xFF6B7280),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val totalDebt = clientTotalDebtRub(item)
        if (totalDebt > 0) {
            Text(
                text = money(totalDebt),
                color = Color(0xFFD63034),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(10.dp))
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFA7A7AB),
            modifier = Modifier.size(18.dp)
        )
    }
}

private data class AdminRentalPreview(
    val rentalId: String,
    val clientId: String,
    val clientName: String,
    val bikeModel: String,
    val periodStart: String,
    val periodEnd: String?,
    val debtRub: Int,
    val totalPaidRub: Int,
    val totalAdjustmentRub: Int,
    val weeklyRateRub: Int,
    val comment: String?,
    val sourceLabel: String
) {
    companion object {
        fun fromSummary(summary: AdminClientSummaryResponse, rentalId: String): AdminRentalPreview {
            return AdminRentalPreview(
                rentalId = rentalId,
                clientId = summary.clientId,
                clientName = summary.fullName,
                bikeModel = summary.bikeModel,
                periodStart = summary.paidUntil.orEmpty(),
                periodEnd = null,
                debtRub = summary.debtRub,
                totalPaidRub = summary.profitRub,
                totalAdjustmentRub = summary.totalAdjustmentRub,
                weeklyRateRub = 0,
                comment = null,
                sourceLabel = "lifecycle"
            )
        }

        fun fromHistory(client: AdminClientDetailsResponse, rental: AdminRentalHistoryItemResponse): AdminRentalPreview {
            return AdminRentalPreview(
                rentalId = rental.rentalId,
                clientId = client.clientId,
                clientName = client.fullName,
                bikeModel = rental.bikeModel,
                periodStart = rental.periodStart,
                periodEnd = rental.periodEnd,
                debtRub = rental.debtRub,
                totalPaidRub = rental.totalPaidRub,
                totalAdjustmentRub = rental.totalAdjustmentRub,
                weeklyRateRub = rental.weeklyRateRub,
                comment = rental.comment,
                sourceLabel = if (rental.periodEnd.isNullOrBlank()) "active_client_rental" else "closed_client_rental"
            )
        }
    }
}

@Composable
private fun AdminClientDetailsScreen(
    details: AdminClientDetailsResponse?,
    isLoading: Boolean,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onEditProfile: () -> Unit,
    onDeleteClient: (String) -> Unit,
    onOpenRental: (AdminRentalPreview) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesign.PageBackground)
            .testTag("admin_client_details_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AdminSquareTopButton(
                    iconRes = R.drawable.ic_back,
                    testTag = "admin_client_details_back",
                    onClick = onClose
                )
                Spacer(Modifier.weight(1f))
                Text("Клиент", color = AppDesign.TitleText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onEditProfile,
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppDesign.Accent),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = AppDesign.Accent
                        ),
                        modifier = Modifier
                            .size(47.dp)
                            .testTag("admin_client_details_edit")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                    val canDelete = details?.rentals?.isEmpty() == true
                    OutlinedButton(
                        onClick = { details?.clientId?.let(onDeleteClient) },
                        enabled = canDelete,
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppDesign.Danger),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = AppDesign.Danger
                        ),
                        modifier = Modifier
                            .size(47.dp)
                            .alpha(if (canDelete) 1f else 0.45f)
                            .testTag("admin_client_details_delete")
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                }
            }

            when {
                isLoading && details == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize().testTag("admin_client_details_loading"),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AppDesign.Accent)
                    }
                }
                details == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Не удалось загрузить клиента", color = AppDesign.TitleText, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = onRetry) { Text("Повторить") }
                    }
                }
                else -> {
                    val d = details
                    val hasOpenRental = d.rentals.any { it.periodEnd.isNullOrBlank() }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp, bottom = 120.dp)
                            .testTag("admin_client_details_content"),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_client_details_card"),
                            shape = RoundedCornerShape(15.dp),
                            color = Color(0xFFFAFBFB),
                            shadowElevation = 8.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppDesign.Accent)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (hasOpenRental) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .background(Color(0xFFE3E6EB), RoundedCornerShape(14.dp))
                                                .border(3.dp, Color(0xFF34C759), RoundedCornerShape(14.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DirectionsBike,
                                                contentDescription = null,
                                                tint = AppDesign.IconSoft,
                                                modifier = Modifier.size(42.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                normalizeCatalogBikeModel(d.bikeModel).ifBlank { "—" },
                                                color = AppDesign.TitleText,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "${formatRubAmount(d.weeklyRateRub)} ₽/нед",
                                                color = AppDesign.SubtleText,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFF34C759), RoundedCornerShape(999.dp))
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    "Активный",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(top = 4.dp),
                                        color = Color(0xFFEAEAF0)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF141718), RoundedCornerShape(999.dp))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            "Неактивный",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(d.fullName, color = AppDesign.TitleText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text(clientCatalogSubtitleFromDetails(d), color = AppDesign.SubtleText, fontSize = 14.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                                    MetricStack("Оплачено", money(d.totalPaidRub), AppDesign.Success)
                                    MetricStack("Долг", money(d.debtRub), if (d.debtRub > 0) AppDesign.Danger else AppDesign.TitleText)
                                    MetricStack("Коррект.", money(d.totalAdjustmentRub), AppDesign.TitleText)
                                }
                                if (d.carriedDebtRub > 0) {
                                    HorizontalDivider(color = Color(0xFFEAEAF0))
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            "ПЕРЕНЕСЁННЫЙ ДОЛГ",
                                            color = AppDesign.SubtleText,
                                            fontSize = 9.sp,
                                            letterSpacing = 0.36.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            money(d.carriedDebtRub),
                                            color = AppDesign.Danger,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            "ПРОФИЛЬ",
                            color = AppDesign.SubtleText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.88.sp
                        )
                        AdminDetailsReadonlyField("ФИО", d.fullName)
                        AdminDetailsReadonlyField("Адрес", d.address)
                        AdminDetailsReadonlyField("Паспорт", d.passportData)
                        d.phones.forEach { phone ->
                            AdminDetailsReadonlyField(phone.label, phone.number)
                        }

                        Text(
                            "ИСТОРИЯ АРЕНД",
                            color = AppDesign.SubtleText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.88.sp
                        )

                        if (d.rentals.isEmpty()) {
                            Text("История аренд пока пустая", color = AppDesign.SubtleText, fontSize = 13.sp)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                d.rentals.forEachIndexed { index, rental ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag(
                                                if (index == 0) "admin_client_history_row_first"
                                                else "admin_client_history_row_${rental.rentalId}"
                                            )
                                            .clickable {
                                                onOpenRental(AdminRentalPreview.fromHistory(client = d, rental = rental))
                                            },
                                        shape = RoundedCornerShape(15.dp),
                                        color = Color(0xFFFAFBFB),
                                        shadowElevation = 8.dp,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEAEAF0))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 15.dp, vertical = 13.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(Color(0xFFE3E6EB), RoundedCornerShape(10.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Outlined.DirectionsBike, contentDescription = null, tint = AppDesign.IconSoft, modifier = Modifier.size(20.dp))
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(
                                                    "${formatShortRuDate(rental.periodStart)} – ${rental.periodEnd?.let(::formatLongRuDate) ?: "н.в."}",
                                                    color = AppDesign.TitleText,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(rental.bikeModel, color = AppDesign.SubtleText, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            }
                                            Text(
                                                if (rental.debtRub > 0) "- ${money(rental.debtRub)}" else "+${money(rental.totalPaidRub)}",
                                                color = if (rental.debtRub > 0) AppDesign.Danger else AppDesign.Success,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = AppDesign.SubtleText, modifier = Modifier.size(16.dp))
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
}

@Composable
private fun AdminRentalDetailsScreenAndroid(
    details: AdminRentalPreview?,
    isLoading: Boolean,
    onClose: () -> Unit,
    onOpenClient: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesign.PageBackground)
            .testTag("admin_rental_details_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 23.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AdminSquareTopButton(
                    iconRes = R.drawable.ic_back,
                    testTag = "admin_rental_details_back",
                    onClick = onClose
                )
                Spacer(Modifier.weight(1f))
                Text("Аренда", color = AppDesign.TitleText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppDesign.Danger),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = AppDesign.Danger
                    ),
                    modifier = Modifier
                        .size(47.dp)
                        .testTag("admin_rental_details_delete")
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            if (isLoading || details == null) {
                Box(
                    modifier = Modifier.fillMaxSize().testTag("admin_rental_details_loading"),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppDesign.Accent)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("admin_rental_details_content")
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(15.dp),
                        color = Color(0xFFFAFBFB),
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppDesign.Accent)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 19.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .background(Color(0xFFE3E6EB), RoundedCornerShape(18.dp))
                                        .border(3.dp, rentalPreviewBorderColor(details), RoundedCornerShape(18.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DirectionsBike,
                                        contentDescription = null,
                                        tint = AppDesign.IconSoft,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(details.bikeModel, color = AppDesign.TitleText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("${formatRubAmount(details.weeklyRateRub)} ₽/нед", color = AppDesign.SubtleText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    rentalPreviewStatusPill(details)
                                }
                            }

                            HorizontalDivider(color = Color(0xFFEAEAF0))
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                                MetricStack("Оплачено", money(details.totalPaidRub), AppDesign.Success)
                                MetricStack("Долг", money(details.debtRub), if (details.debtRub > 0) AppDesign.Danger else AppDesign.TitleText)
                                MetricStack("Коррект.", money(details.totalAdjustmentRub), AppDesign.TitleText)
                            }
                            HorizontalDivider(color = Color(0xFFEAEAF0))
                            AdminDetailsReadonlyField("Период", "${formatLongRuDate(details.periodStart)} – ${details.periodEnd?.let(::formatLongRuDate) ?: "н.в."}")
                            AdminDetailsReadonlyField("Арендатор", details.clientName)
                            AdminDetailsReadonlyField("Логин", if (details.sourceLabel == "lifecycle") "Черновик lifecycle" else "—")
                            AdminDetailsReadonlyField("Пароль", "—")
                            if (!details.comment.isNullOrBlank()) {
                                AdminDetailsReadonlyField("Комментарий", details.comment)
                            }
                            AdminDetailsReadonlyField("Тип записи", rentalSourceTitle(details.sourceLabel))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onOpenClient,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppDesign.Accent),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = AppDesign.Accent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("admin_rental_details_secondary_action")
                        ) {
                            Text("К арендатору", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onDelete,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppDesign.Danger,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("admin_rental_details_primary_action")
                        ) {
                            Text("Удалить", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rentalPreviewStatusPill(details: AdminRentalPreview) {
    val isClosed = !details.periodEnd.isNullOrBlank()
    val title = when {
        isClosed -> "Завершена"
        details.sourceLabel == "lifecycle" -> "В стоке"
        else -> "Активная"
    }
    val color = when {
        isClosed -> Color(0xFF6B7280)
        details.sourceLabel == "lifecycle" -> Color(0xFFCB30E0)
        else -> Color(0xFF34C759)
    }
    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun rentalPreviewBorderColor(details: AdminRentalPreview): Color {
    if (!details.periodEnd.isNullOrBlank()) return Color(0xFF6B7280)
    return if (details.sourceLabel == "lifecycle") Color(0xFFCB30E0) else Color(0xFF34C759)
}

private fun rentalSourceTitle(raw: String): String {
    return when (raw) {
        "lifecycle" -> "Lifecycle"
        "active_client_rental" -> "Активная client_rental"
        "closed_client_rental" -> "Завершенная client_rental"
        else -> raw
    }
}

@Composable
private fun MetricStack(title: String, value: String, valueColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = AppDesign.SubtleText, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AdminDetailsReadonlyField(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(),
            color = AppDesign.SubtleText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.66.sp
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(Color.White, RoundedCornerShape(12.84.dp))
                .border(1.dp, AppDesign.Accent, RoundedCornerShape(12.84.dp))
                .padding(horizontal = 19.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = value?.ifBlank { "—" } ?: "—",
                color = AppDesign.TitleText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AdminBikeCatalogRow(
    bike: AdminBikeResponse,
    runtime: BikeCatalogRuntimeSnapshot
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(77.dp)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(59.dp)
                .background(Color(0xFFE3E6EB), RoundedCornerShape(12.dp))
                .border(
                    width = 3.dp,
                    color = runtime.borderColor,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Outlined.DirectionsBike,
                contentDescription = null,
                tint = AppDesign.IconSoft,
                modifier = Modifier.align(Alignment.Center).size(32.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = bike.bikeModel,
                color = Color(0xFF111827),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = runtime.subtitle,
                color = Color(0xFF6B7280),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "${formatRubAmount(bike.weeklyRateRub)} ₽/нед",
            color = Color(0xFF1F2937),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFA7A7AB),
            modifier = Modifier.size(18.dp)
        )
    }
}

private data class BikeCatalogRuntimeSnapshot(
    val borderColor: Color,
    val subtitle: String
)

private fun bikeCatalogRuntimeSnapshot(
    bike: AdminBikeResponse,
    rentals: List<AdminClientSummaryResponse>
): BikeCatalogRuntimeSnapshot {
    val normalizedBikeModel = normalizeCatalogSearchText(bike.bikeModel)
    val activeRentals = rentals.filter {
        it.rentalIsActive && normalizeCatalogSearchText(it.bikeModel) == normalizedBikeModel
    }
    if (activeRentals.isEmpty()) {
        return BikeCatalogRuntimeSnapshot(
            borderColor = Color(0xFFCB30E0),
            subtitle = "-"
        )
    }

    val hasSoonReturn = activeRentals.any { it.rentalPipelineStatus == "soon_return" }
    val borderColor = if (hasSoonReturn) Color(0xFFFFCC00) else Color(0xFF34C759)
    val activeRental = activeRentals.first()
    val clientName = activeRental.fullName.trim().ifEmpty { "Клиент" }
    val subtitle = if (activeRental.rentalPipelineStatus == "soon_return") {
        "$clientName · вернут в течении нед."
    } else {
        val paidUntil = shortPaidUntilText(activeRental.paidUntil)
        if (paidUntil != null) "$clientName · до $paidUntil" else "$clientName · долгосрочно"
    }

    return BikeCatalogRuntimeSnapshot(
        borderColor = borderColor,
        subtitle = subtitle
    )
}

private fun clientCatalogSubtitle(client: AdminClientSummaryResponse): String {
    if (client.rentalIsActive) {
        val model = normalizeCatalogBikeModel(client.bikeModel)
        val paidUntil = shortPaidUntilText(client.paidUntil)
        return when {
            paidUntil != null && model.isNotEmpty() -> "$model · до $paidUntil"
            paidUntil != null -> "до $paidUntil"
            model.isNotEmpty() -> model
            else -> "Активная аренда"
        }
    }
    val model = normalizeCatalogBikeModel(client.bikeModel)
    return if (model.isEmpty()) "-" else model
}

private fun clientCatalogSubtitleFromDetails(details: AdminClientDetailsResponse): String {
    val model = normalizeCatalogBikeModel(details.bikeModel)
    val paidUntil = shortPaidUntilText(details.paidUntil)
    return when {
        paidUntil != null && model.isNotEmpty() -> "$model · до $paidUntil"
        paidUntil != null -> "до $paidUntil"
        model.isNotEmpty() -> model
        else -> "—"
    }
}

private fun clientTotalDebtRub(client: AdminClientSummaryResponse): Int {
    return client.debtRub.coerceAtLeast(0) + client.carriedDebtRub.coerceAtLeast(0)
}

private fun normalizeCatalogBikeModel(rawValue: String): String {
    val value = rawValue.trim()
    return if (value.isEmpty() || value == "-") "" else value
}

private fun normalizeCatalogSearchText(value: String): String = value.trim().lowercase()

private fun shortPaidUntilText(paidUntilRaw: String?): String? {
    val value = paidUntilRaw?.trim().orEmpty()
    if (value.isEmpty()) return null
    val parts = value.split("-")
    if (parts.size != 3) return null
    val day = parts[2].toIntOrNull() ?: return null
    val monthIndex = (parts[1].toIntOrNull() ?: return null) - 1
    val month = ruShortMonths.getOrNull(monthIndex) ?: return null
    return "$day $month"
}

private fun formatRubAmount(value: Int): String {
    return DecimalFormat("#,###").format(value).replace(',', ' ')
}

private val ruShortMonths = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

private fun formatShortRuDate(value: String): String {
    return runCatching {
        val parts = value.trim().split("-")
        if (parts.size != 3) return value
        val day = parts[2].toInt()
        val month = ruShortMonths[parts[1].toInt() - 1]
        "${if (day < 10) "0$day" else "$day"} $month"
    }.getOrDefault(value)
}

private fun formatLongRuDate(value: String): String {
    return runCatching {
        val parts = value.trim().split("-")
        if (parts.size != 3) return value
        val day = parts[2].toInt()
        val month = ruShortMonths[parts[1].toInt() - 1]
        val year = parts[0]
        "${if (day < 10) "0$day" else "$day"} $month $year"
    }.getOrDefault(value)
}

@Composable
private fun AdminRentCard(
    item: AdminClientSummaryResponse,
    isFirst: Boolean = false,
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
            .testTag(if (isFirst) "admin_rent_card_first" else "admin_rent_card_${item.rentalId ?: item.clientId}"),
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
    var phoneLabel by remember { mutableStateOf("Рабочий (TG)") }
    var phoneNumber by remember { mutableStateOf("") }
    var extraPhoneLabel by remember { mutableStateOf("") }
    var extraPhoneNumber by remember { mutableStateOf("") }
    var showExtraPhone by remember { mutableStateOf(false) }
    var showComment by remember { mutableStateOf(false) }
    var comment by remember { mutableStateOf("") }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    fun fail(message: String) {
        toastMessage = message
    }

    LaunchedEffect(toastMessage) {
        if (!toastMessage.isNullOrEmpty()) {
            delay(2200)
            toastMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesign.PageBackground)
            .testTag("create_client_sheet")
    ) {
        AdminFormSheetScaffold(
            title = "Новый клиент",
            onBack = onDismiss,
            onSubmit = {
                if (fullName.trim().isEmpty()) {
                    fail("Укажите ФИО")
                    return@AdminFormSheetScaffold
                }
                if (address.trim().isEmpty()) {
                    fail("Укажите адрес")
                    return@AdminFormSheetScaffold
                }
                if (passport.trim().isEmpty()) {
                    fail("Укажите паспортные данные")
                    return@AdminFormSheetScaffold
                }
                if (phoneLabel.trim().isEmpty() || phoneNumber.trim().isEmpty()) {
                    fail("Заполните подпись и номер телефона")
                    return@AdminFormSheetScaffold
                }
                onCreate(
                    fullName.trim(),
                    address.trim(),
                    passport.trim(),
                    phoneLabel.trim(),
                    phoneNumber.trim()
                )
            },
            backTag = "create_client_cancel_button",
            submitTag = "create_client_submit_button",
            horizontalPadding = 8.dp,
            topBarHeight = 62.dp,
            topBarTopPadding = 0.dp,
            contentTopPadding = 16.dp,
            contentBottomPadding = 24.dp,
            contentSpacing = 18.dp
        ) {
            AdminFormSectionTitle("ПРОФИЛЬ")
            AdminSheetInputField(
                label = "ФИО",
                placeholder = "введите...",
                value = fullName,
                onValueChange = { fullName = it },
                testTag = "create_client_full_name_input",
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words
            )
            AdminSheetInputField(
                label = "Адрес",
                placeholder = "введите...",
                value = address,
                onValueChange = { address = it },
                testTag = "create_client_address_input",
                keyboardType = KeyboardType.Text
            )
            AdminSheetInputField(
                label = "Паспортные данные",
                placeholder = "введите...",
                value = passport,
                onValueChange = { passport = it },
                testTag = "create_client_passport_input",
                keyboardType = KeyboardType.Text
            )

            AdminFormSectionTitle("ТЕЛЕФОНЫ", topPadding = 6.dp)
            AdminSheetInputField(
                label = "Подпись",
                placeholder = "введите...",
                value = phoneLabel,
                onValueChange = { phoneLabel = it },
                testTag = "create_client_phone_label_input",
                keyboardType = KeyboardType.Text,
                valueWeight = FontWeight.Bold,
                capitalization = KeyboardCapitalization.Sentences
            )
            AdminSheetInputField(
                label = "Телефон",
                placeholder = "+7 …",
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                testTag = "create_client_phone_number_input",
                keyboardType = KeyboardType.Phone
            )

            if (showExtraPhone) {
                AdminSheetInputField(
                    label = "Подпись",
                    placeholder = "введите...",
                    value = extraPhoneLabel,
                    onValueChange = { extraPhoneLabel = it },
                    testTag = "create_client_phone_label2_input",
                    keyboardType = KeyboardType.Text
                )
                AdminSheetInputField(
                    label = "Телефон",
                    placeholder = "+7 …",
                    value = extraPhoneNumber,
                    onValueChange = { extraPhoneNumber = it },
                    testTag = "create_client_phone_number2_input",
                    keyboardType = KeyboardType.Phone
                )
            }

            AdminDashedActionButton(
                text = "+ Добавить телефон",
                enabled = !showExtraPhone,
                onClick = { showExtraPhone = true },
                testTag = "create_client_add_phone_button"
            )

            if (showComment) {
                AdminSheetInputField(
                    label = "Комментарий",
                    placeholder = "введите...",
                    value = comment,
                    onValueChange = { comment = it },
                    testTag = "create_client_comment_input",
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Sentences
                )
            }

            AdminDashedActionButton(
                text = "+ Добавить комментарий",
                enabled = !showComment,
                onClick = { showComment = true },
                testTag = "create_client_add_comment_button"
            )
        }

        AppToast(
            message = toastMessage,
            modifier = Modifier.align(Alignment.BottomCenter),
            bottomPadding = 96
        )
    }
}

@Composable
private fun AdminCreateBikeDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String, String) -> Unit
) {
    var model by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("3000") }
    var frame by remember { mutableStateOf("") }
    var motor by remember { mutableStateOf("") }
    var battery1 by remember { mutableStateOf("") }
    var battery2 by remember { mutableStateOf("") }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    fun fail(message: String) {
        toastMessage = message
    }

    LaunchedEffect(toastMessage) {
        if (!toastMessage.isNullOrEmpty()) {
            delay(2200)
            toastMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesign.PageBackground)
            .testTag("create_bike_sheet")
    ) {
        AdminFormSheetScaffold(
            title = "Новый велосипед",
            onBack = onDismiss,
            onSubmit = {
                if (model.trim().isEmpty()) {
                    fail("Укажите модель велосипеда")
                    return@AdminFormSheetScaffold
                }
                val numericRate = rate.trim().toIntOrNull()
                if (numericRate == null || numericRate <= 0) {
                    fail("Стоимость недели должна быть положительным числом")
                    return@AdminFormSheetScaffold
                }
                if (frame.trim().isEmpty()) {
                    fail("Укажите серийный номер рамы")
                    return@AdminFormSheetScaffold
                }
                if (motor.trim().isEmpty()) {
                    fail("Укажите серийный номер мотора")
                    return@AdminFormSheetScaffold
                }
                if (battery1.trim().isEmpty()) {
                    fail("Укажите серийный номер аккумулятора 1")
                    return@AdminFormSheetScaffold
                }

                onCreate(
                    model.trim(),
                    numericRate.toString(),
                    frame.trim(),
                    motor.trim(),
                    battery1.trim(),
                    battery2.trim()
                )
            },
            backTag = "create_bike_cancel_button",
            submitTag = "create_bike_submit_button",
            horizontalPadding = 8.dp,
            topBarHeight = 47.dp,
            topBarTopPadding = 8.dp,
            contentTopPadding = 14.dp,
            contentBottomPadding = 24.dp,
            contentSpacing = 14.dp
        ) {
            AdminBikePhotoCard(testTag = "create_bike_photo_picker")
            AdminFormSectionTitle("ОБЯЗАТЕЛЬНЫЕ")
            AdminSheetInputField(
                label = "Название/модель",
                placeholder = "введите...",
                value = model,
                onValueChange = { model = it },
                testTag = "create_bike_model_input",
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words
            )
            AdminSheetInputField(
                label = "Серийный номер / VIN",
                placeholder = "введите...",
                value = frame,
                onValueChange = { frame = it },
                testTag = "create_bike_frame_input",
                keyboardType = KeyboardType.Text
            )
            AdminSheetInputField(
                label = "Серийный номер мотора",
                placeholder = "введите...",
                value = motor,
                onValueChange = { motor = it },
                testTag = "create_bike_motor_input",
                keyboardType = KeyboardType.Text
            )
            AdminSheetInputField(
                label = "Недельная ставка W (₽)",
                placeholder = "введите...",
                value = rate,
                onValueChange = { rate = it },
                testTag = "create_bike_rate_input",
                keyboardType = KeyboardType.Number
            )

            AdminFormSectionTitle("ОПЦИОНАЛЬНО", topPadding = 6.dp)
            AdminSheetInputField(
                label = "Серийный номер АКБ 1",
                placeholder = "не обязательно",
                value = battery1,
                onValueChange = { battery1 = it },
                testTag = "create_bike_battery1_input",
                keyboardType = KeyboardType.Text,
                isDashed = true
            )
            AdminSheetInputField(
                label = "Серийный номер АКБ 2",
                placeholder = "не обязательно",
                value = battery2,
                onValueChange = { battery2 = it },
                testTag = "create_bike_battery2_input",
                keyboardType = KeyboardType.Text,
                isDashed = true
            )
        }

        AppToast(
            message = toastMessage,
            modifier = Modifier.align(Alignment.BottomCenter),
            bottomPadding = 96
        )
    }
}

@Composable
private fun AdminCreateRentalDialog(
    clients: List<AdminClientSummaryResponse>,
    bikes: List<AdminBikeResponse>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String) -> Unit
) {
    var clientId by remember { mutableStateOf("") }
    var bikeId by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var periodStart by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    var periodEnd by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }
    var contractUrl by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var isClientPickerPresented by remember { mutableStateOf(false) }
    var isBikePickerPresented by remember { mutableStateOf(false) }
    var draftClientId by remember { mutableStateOf("") }
    var draftBikeId by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    fun fail(message: String) {
        toastMessage = message
    }

    fun generateCredentials() {
        val symbols = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%"
        password = buildString {
            repeat(12) {
                append(symbols.random())
            }
        }
        if (login.trim().isEmpty()) {
            val fromClient = clients.firstOrNull { it.clientId == clientId }?.clientLogin.orEmpty()
            login = if (fromClient.isNotBlank()) {
                fromClient
            } else {
                "client${(1000..9999).random()}"
            }
        }
    }

    fun copyCredentials() {
        val normalizedLogin = login.trim()
        val normalizedPassword = password.trim()
        if (normalizedLogin.isEmpty() || normalizedPassword.isEmpty()) {
            fail("Заполните логин и пароль")
            return
        }
        clipboardManager.setText(AnnotatedString("Логин: $normalizedLogin\nПароль: $normalizedPassword"))
        toastMessage = "Скопировано"
    }

    LaunchedEffect(toastMessage) {
        if (!toastMessage.isNullOrEmpty()) {
            delay(2200)
            toastMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesign.PageBackground)
            .testTag("create_rental_sheet")
    ) {
        AdminFormSheetScaffold(
            title = "Новая аренда",
            onBack = onDismiss,
            onSubmit = {
                if (clientId.isBlank()) {
                    fail("Выберите клиента")
                    return@AdminFormSheetScaffold
                }
                if (bikeId.isBlank()) {
                    fail("Выберите велосипед")
                    return@AdminFormSheetScaffold
                }
                if (login.trim().isBlank() || password.trim().isBlank()) {
                    fail("Укажите логин и пароль клиента")
                    return@AdminFormSheetScaffold
                }
                val start = periodStart.trim()
                val validDate = runCatching { LocalDate.parse(start, DateTimeFormatter.ISO_LOCAL_DATE) }.isSuccess
                if (!validDate) {
                    fail("Дата начала должна быть в формате YYYY-MM-DD")
                    return@AdminFormSheetScaffold
                }
                val endTrimmed = periodEnd.trim()
                if (endTrimmed.isNotEmpty()) {
                    val validEndDate = runCatching { LocalDate.parse(endTrimmed, DateTimeFormatter.ISO_LOCAL_DATE) }.isSuccess
                    if (!validEndDate) {
                        fail("Дата окончания должна быть в формате YYYY-MM-DD")
                        return@AdminFormSheetScaffold
                    }
                    if (endTrimmed < start) {
                        fail("Дата окончания не может быть раньше даты начала")
                        return@AdminFormSheetScaffold
                    }
                }
                onCreate(clientId, bikeId, login.trim(), password.trim(), start)
            },
            backTag = "create_rental_cancel_button",
            submitTag = "create_rental_submit_button",
            horizontalPadding = 23.dp,
            topBarHeight = 47.dp,
            topBarTopPadding = 8.dp,
            contentTopPadding = 14.dp,
            contentBottomPadding = 26.dp,
            contentSpacing = 14.dp,
            titleColor = AppDesign.TitleText
        ) {
            AdminFormSectionTitle("КЛИЕНТ И ВЕЛОСИПЕД")
            AdminSelectorField(
                label = "КЛИЕНТ",
                value = clients.firstOrNull { it.clientId == clientId }?.fullName,
                placeholder = "выбрать клаента",
                testTag = "create_rental_client_selector",
                leadingMarkerColor = Color(0xFFD3D7DD),
                onClick = {
                    draftClientId = clientId
                    isClientPickerPresented = true
                }
            )

            AdminSelectorField(
                label = "ВЕЛОСИПЕД",
                value = bikes.firstOrNull { it.bikeId == bikeId }?.let { "${it.bikeModel} · ${it.weeklyRateRub} ₽/нед" },
                placeholder = "выбрать · покажет ставку",
                testTag = "create_rental_bike_selector",
                leadingMarkerColor = Color(0xFFCDD1D9),
                onClick = {
                    draftBikeId = bikeId
                    isBikePickerPresented = true
                }
            )

            AdminSheetInputField(
                label = "ДАТА НАЧАЛА",
                placeholder = "YYYY-MM-DD",
                value = periodStart,
                onValueChange = { periodStart = it },
                testTag = "create_rental_start_date_input",
                keyboardType = KeyboardType.Text
            )
            AdminSheetInputField(
                label = "ДАТА ОКОНЧАНИЯ",
                placeholder = "не обязательно",
                value = periodEnd,
                onValueChange = { periodEnd = it },
                testTag = "create_rental_end_date_input",
                keyboardType = KeyboardType.Text,
                isDashed = true
            )

            AdminFormSectionTitle("ДОСТУП КЛИЕНТА", topPadding = 6.dp)
            AdminSheetInputField(
                label = "ЛОГИН КЛИЕНТА",
                placeholder = "введите...",
                value = login,
                onValueChange = { login = it },
                testTag = "create_rental_login_input",
                keyboardType = KeyboardType.Text
            )
            AdminSheetInputField(
                label = "ПАРОЛЬ КЛИЕНТА",
                placeholder = "введите...",
                value = password,
                onValueChange = { password = it },
                testTag = "create_rental_password_input",
                keyboardType = KeyboardType.Password
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { generateCredentials() },
                    modifier = Modifier
                        .width(179.dp)
                        .height(44.dp)
                        .testTag("create_rental_generate_credentials_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppDesign.Accent,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Сгенерировать", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { copyCredentials() },
                    modifier = Modifier
                        .width(181.dp)
                        .height(46.dp)
                        .testTag("create_rental_copy_credentials_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AppDesign.Accent),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = AppDesign.Accent
                    )
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Скопировать", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            AdminFormSectionTitle("ДОКУМЕНТЫ И КОММЕНТАРИЙ", topPadding = 6.dp)
            AdminSheetInputField(
                label = "ССЫЛКА НА ВИДЕО",
                placeholder = "не обязательно",
                value = videoUrl,
                onValueChange = { videoUrl = it },
                testTag = "create_rental_video_url_input",
                keyboardType = KeyboardType.Uri,
                isDashed = true
            )
            AdminSheetInputField(
                label = "ССЫЛКА НА ДОГОВОР",
                placeholder = "не обязательно",
                value = contractUrl,
                onValueChange = { contractUrl = it },
                testTag = "create_rental_contract_url_input",
                keyboardType = KeyboardType.Uri,
                isDashed = true
            )
            AdminSheetInputField(
                label = "КОММЕНТАРИЙ",
                placeholder = "не обязательно",
                value = comment,
                onValueChange = { comment = it },
                testTag = "create_rental_comment_input",
                keyboardType = KeyboardType.Text,
                isDashed = true
            )
        }

        AppToast(
            message = toastMessage,
            modifier = Modifier.align(Alignment.BottomCenter),
            bottomPadding = 96
        )

        AnimatedVisibility(
            visible = isClientPickerPresented,
            enter = fadeIn(animationSpec = tween(220)) + slideInVertically(initialOffsetY = { it }, animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180)),
            modifier = Modifier.fillMaxSize().zIndex(15f)
        ) {
            RentalClientPickerSheet(
                clients = clients,
                selectedId = draftClientId,
                onSelect = { draftClientId = it },
                onClose = { isClientPickerPresented = false },
                onConfirm = {
                    clientId = draftClientId
                    val suggestedLogin = clients.firstOrNull { it.clientId == draftClientId }?.clientLogin.orEmpty()
                    if (login.trim().isEmpty() && suggestedLogin.isNotBlank()) {
                        login = suggestedLogin
                    }
                    isClientPickerPresented = false
                },
                listTag = "create_rental_client_picker_list"
            )
        }

        AnimatedVisibility(
            visible = isBikePickerPresented,
            enter = fadeIn(animationSpec = tween(220)) + slideInVertically(initialOffsetY = { it }, animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180)),
            modifier = Modifier.fillMaxSize().zIndex(15f)
        ) {
            RentalBikePickerSheet(
                bikes = bikes,
                selectedId = draftBikeId,
                onSelect = { draftBikeId = it },
                onClose = { isBikePickerPresented = false },
                onConfirm = {
                    bikeId = draftBikeId
                    isBikePickerPresented = false
                },
                listTag = "create_rental_bike_picker_list"
            )
        }
    }
}

@Composable
private fun AdminFormSheetScaffold(
    title: String,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    backTag: String,
    submitTag: String,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    topBarHeight: androidx.compose.ui.unit.Dp,
    topBarTopPadding: androidx.compose.ui.unit.Dp,
    contentTopPadding: androidx.compose.ui.unit.Dp,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    contentSpacing: androidx.compose.ui.unit.Dp,
    titleColor: Color = AppDesign.Accent,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (topBarTopPadding > 0.dp) {
            Spacer(Modifier.height(topBarTopPadding))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .height(topBarHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AdminSheetTopButton(
                onClick = onBack,
                dark = false,
                testTag = backTag,
                iconRes = R.drawable.ic_back,
                iconSize = 14.dp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = title,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            AdminSheetTopButton(
                onClick = onSubmit,
                dark = true,
                testTag = submitTag,
                iconRes = R.drawable.ic_ok,
                iconSize = 16.dp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding)
                .padding(top = contentTopPadding, bottom = contentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing)
        ) {
            content()
        }
    }
}

@Composable
private fun AdminSheetTopButton(
    onClick: () -> Unit,
    dark: Boolean,
    testTag: String,
    iconRes: Int,
    iconSize: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .size(47.dp)
            .background(if (dark) AppDesign.Accent else Color.White, RoundedCornerShape(14.dp))
            .border(
                width = 1.5.dp,
                color = AppDesign.Accent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun AdminFormSectionTitle(
    text: String,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    Text(
        text = text,
        color = Color(0xFF6B7280),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.88.sp,
        modifier = Modifier.padding(top = topPadding)
    )
}

@Composable
private fun AdminSheetInputField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    keyboardType: KeyboardType,
    valueWeight: FontWeight = FontWeight.Normal,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    isDashed: Boolean = false,
    accentBorder: Boolean = false
) {
    val dashedColor = Color(0xFF98A1AD)
    val borderColor = if (accentBorder) AppDesign.Accent else AppDesign.Accent

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(),
            color = Color(0xFF6B7280),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.66.sp
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(Color.White, RoundedCornerShape(12.84.dp))
                .drawWithContent {
                    drawContent()
                    val corner = CornerRadius(12.84.dp.toPx(), 12.84.dp.toPx())
                    if (isDashed) {
                        drawRoundRect(
                            color = dashedColor,
                            cornerRadius = corner,
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx()))
                            )
                        )
                    } else {
                        drawRoundRect(
                            color = borderColor,
                            cornerRadius = corner,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
                .padding(horizontal = 19.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag),
                singleLine = true,
                textStyle = TextStyle(
                    color = AppDesign.Accent,
                    fontSize = 13.sp,
                    fontWeight = valueWeight
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    capitalization = capitalization
                ),
                cursorBrush = SolidColor(AppDesign.TitleText),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            color = Color(0xFFC9CCD2),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    inner()
                }
            )
        }
    }
}

@Composable
private fun AdminSelectorField(
    label: String,
    value: String?,
    placeholder: String,
    testTag: String,
    leadingMarkerColor: Color = Color.Transparent,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Color.White, RoundedCornerShape(12.84.dp))
            .border(1.5.dp, AppDesign.Accent, RoundedCornerShape(12.84.dp))
            .testTag(testTag)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 19.dp, end = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    color = Color(0xFF6B7280),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = value ?: placeholder,
                    color = if (value == null) Color(0xFFC9CCD2) else AppDesign.TitleText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xFFEAEAF0), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF6B7280),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (leadingMarkerColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(vertical = 8.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(leadingMarkerColor, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun AdminDashedActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = Color(0xFF98A1AD),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                    )
                )
            }
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) AppDesign.Accent else AppDesign.Accent.copy(alpha = 0.5f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.28.sp
        )
    }
}

@Composable
private fun AdminBikePhotoCard(testTag: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(202.dp)
            .background(Color(0xFFEEF0F3), RoundedCornerShape(14.dp))
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = AppDesign.Accent,
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx()))
                    )
                )
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .border(1.5.dp, AppDesign.Accent, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DirectionsBike,
                    contentDescription = null,
                    tint = AppDesign.Accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Загрузить фото", color = AppDesign.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                "Нажмите, чтобы выбрать из галереи",
                color = Color(0xFF6B7280),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private enum class RentalClientPickerFilter(val title: String) {
    All("Все"),
    Debtors("Должники"),
    Active("Активные")
}

@Composable
private fun RentalClientPickerSheet(
    clients: List<AdminClientSummaryResponse>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    listTag: String
) {
    var searchText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(RentalClientPickerFilter.All) }
    val baseClients = remember(clients) { clients.filter { !it.rentalIsActive } }
    val visibleClients = remember(baseClients, searchText, selectedFilter) {
        val query = searchText.trim().lowercase()
        val searched = baseClients.filter { client ->
            query.isEmpty() ||
                client.fullName.lowercase().contains(query) ||
                client.bikeModel.lowercase().contains(query) ||
                client.clientLogin.orEmpty().lowercase().contains(query)
        }
        when (selectedFilter) {
            RentalClientPickerFilter.All -> searched
            RentalClientPickerFilter.Debtors -> searched.filter { it.debtRub > 0 }
            RentalClientPickerFilter.Active -> emptyList()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesign.PageBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            RentalPickerHeader(
                title = "Клиенты",
                onClose = onClose,
                onConfirm = onConfirm,
                confirmEnabled = selectedId.isNotBlank()
            )
            RentalPickerSearchField(
                value = searchText,
                placeholder = "Поиск: ФИО, телефон, паспорт",
                onValueChange = { searchText = it },
                modifier = Modifier.padding(horizontal = 8.dp).padding(top = 6.dp)
            )
            Row(
                modifier = Modifier.padding(horizontal = 8.dp).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RentalPickerFilterChip(
                    title = RentalClientPickerFilter.All.title,
                    count = baseClients.size,
                    selected = selectedFilter == RentalClientPickerFilter.All,
                    onClick = { selectedFilter = RentalClientPickerFilter.All }
                )
                RentalPickerFilterChip(
                    title = RentalClientPickerFilter.Debtors.title,
                    count = baseClients.count { it.debtRub > 0 },
                    selected = selectedFilter == RentalClientPickerFilter.Debtors,
                    onClick = { selectedFilter = RentalClientPickerFilter.Debtors }
                )
                RentalPickerFilterChip(
                    title = RentalClientPickerFilter.Active.title,
                    count = 0,
                    selected = selectedFilter == RentalClientPickerFilter.Active,
                    onClick = { selectedFilter = RentalClientPickerFilter.Active }
                )
            }

            if (visibleClients.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 14.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = AppDesign.IconSoft, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Нет свободных клиентов", color = AppDesign.TitleText, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "В списке выбора скрыты клиенты, которые уже участвуют в активных арендах.",
                            color = AppDesign.SubtleText,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 10.dp)
                        .background(Color.White)
                        .testTag(listTag),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(visibleClients.size) { index ->
                        val client = visibleClients[index]
                        val isSelected = selectedId == client.clientId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(client.clientId) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(AppDesign.PageBackground, RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFFE0E5EC), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.AccountCircle,
                                    contentDescription = null,
                                    tint = AppDesign.IconSoft,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(client.fullName, color = AppDesign.TitleText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                val login = client.clientLogin.orEmpty()
                                Text(
                                    if (login.isNotBlank()) "Логин: $login" else "Свободный клиент",
                                    color = AppDesign.SubtleText,
                                    fontSize = 14.sp
                                )
                                Text(client.bikeModel, color = AppDesign.SubtleText, fontSize = 12.sp)
                            }
                            Icon(
                                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) AppDesign.Success else AppDesign.IconSoft,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (index < visibleClients.lastIndex) {
                            HorizontalDivider(color = Color(0xFFEAEAF0), thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RentalBikePickerSheet(
    bikes: List<AdminBikeResponse>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    listTag: String
) {
    var searchText by remember { mutableStateOf("") }
    val availableBikes = remember(bikes) { bikes.filter { !it.bikeIsInRental } }
    val visibleBikes = remember(availableBikes, searchText) {
        val query = searchText.trim().lowercase()
        availableBikes.filter { bike ->
            query.isEmpty() ||
                bike.bikeModel.lowercase().contains(query) ||
                bike.frameSerialNumber.lowercase().contains(query) ||
                bike.motorSerialNumber.lowercase().contains(query) ||
                bike.batterySerialNumber1.lowercase().contains(query) ||
                bike.batterySerialNumber2.orEmpty().lowercase().contains(query)
        }.sortedBy { it.bikeModel.lowercase() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesign.PageBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            RentalPickerHeader(
                title = "Велосипеды",
                onClose = onClose,
                onConfirm = onConfirm,
                confirmEnabled = selectedId.isNotBlank()
            )
            RentalPickerSearchField(
                value = searchText,
                placeholder = "Поиск: модель, серийный номер",
                onValueChange = { searchText = it },
                modifier = Modifier.padding(horizontal = 8.dp).padding(top = 6.dp)
            )

            if (visibleBikes.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 14.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.DirectionsBike, contentDescription = null, tint = AppDesign.IconSoft, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Нет велосипедов", color = AppDesign.TitleText, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 10.dp)
                        .background(Color.White)
                        .testTag(listTag),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(visibleBikes.size) { index ->
                        val bike = visibleBikes[index]
                        val isSelected = selectedId == bike.bikeId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(bike.bikeId) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(AppDesign.PageBackground, RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFFE0E5EC), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.DirectionsBike,
                                    contentDescription = null,
                                    tint = AppDesign.IconSoft,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bike.bikeModel, color = AppDesign.TitleText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("${bike.weeklyRateRub} ₽ / неделя", color = AppDesign.SubtleText, fontSize = 14.sp)
                            }
                            Icon(
                                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) AppDesign.Success else AppDesign.IconSoft,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (index < visibleBikes.lastIndex) {
                            HorizontalDivider(color = Color(0xFFEAEAF0), thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RentalPickerHeader(
    title: String,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(62.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(47.dp)
                .background(Color.White, RoundedCornerShape(14.dp))
                .border(1.5.dp, AppDesign.Accent, RoundedCornerShape(14.dp))
                .clickable(onClick = onClose)
                .testTag("selection_picker_close_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = AppDesign.Accent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(title, color = Color(0xFF141718), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(47.dp)
                .background(AppDesign.Accent, RoundedCornerShape(14.dp))
                .border(1.5.dp, AppDesign.Accent, RoundedCornerShape(14.dp))
                .clickable(enabled = confirmEnabled, onClick = onConfirm)
                .testTag("selection_picker_confirm_button")
                .alpha(if (confirmEnabled) 1f else 0.45f),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_ok),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun RentalPickerSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(Color.White, RoundedCornerShape(12.84.dp))
            .border(1.5.dp, AppDesign.Accent, RoundedCornerShape(12.84.dp))
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = AppDesign.TitleText, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(color = AppDesign.TitleText, fontSize = 13.sp, fontWeight = FontWeight.Normal),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.None
            ),
            cursorBrush = SolidColor(AppDesign.TitleText),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = Color(0xFFC9CCD2), fontSize = 13.sp)
                }
                inner()
            }
        )
    }
}

@Composable
private fun RentalPickerFilterChip(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) AppDesign.Accent else Color.White
    val textColor = if (selected) Color.White else AppDesign.Accent
    val countBg = if (selected) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.08f)

    Row(
        modifier = Modifier
            .height(36.dp)
            .background(bg, RoundedCornerShape(999.dp))
            .border(1.5.dp, AppDesign.Accent, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .background(countBg, RoundedCornerShape(999.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("$count", color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
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

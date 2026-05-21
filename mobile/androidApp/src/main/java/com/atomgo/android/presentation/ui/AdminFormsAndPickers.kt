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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
internal fun AdminCreateClientDialog(
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
internal fun AdminCreateBikeDialog(
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
internal fun AdminCreateRentalDialog(
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
                leadingMarkerColor = AppDesign.SheetHandle,
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
                leadingMarkerColor = AppDesign.MarkerSoft,
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
                        contentColor = AppDesign.SurfaceBackground
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
                    border = androidx.compose.foundation.BorderStroke(AppDesign.ThinStroke, AppDesign.Accent),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = AppDesign.SurfaceBackground,
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
internal fun AdminFormSheetScaffold(
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
internal fun AdminSheetTopButton(
    onClick: () -> Unit,
    dark: Boolean,
    testTag: String,
    iconRes: Int,
    iconSize: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .size(47.dp)
            .background(if (dark) AppDesign.Accent else AppDesign.SurfaceBackground, RoundedCornerShape(14.dp))
            .border(
                width = AppDesign.ThinStroke,
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
internal fun AdminFormSectionTitle(
    text: String,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    Text(
        text = text,
        color = AppDesign.PaleSky,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.88.sp,
        modifier = Modifier.padding(top = topPadding)
    )
}

@Composable
internal fun AdminSheetInputField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    keyboardType: KeyboardType,
    valueWeight: FontWeight = FontWeight.Normal,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    isDashed: Boolean = false,
    accentBorder: Boolean = false,
    borderColor: Color = AppDesign.Accent,
    autoFocus: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dashedColor = AppDesign.PlaceholderStroke
    val resolvedBorderColor = if (accentBorder) AppDesign.Accent else borderColor
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val fieldInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(150)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(AppDesign.SurfaceBackground, RoundedCornerShape(12.84.dp))
            .drawWithContent {
                drawContent()
                val corner = CornerRadius(12.84.dp.toPx(), 12.84.dp.toPx())
                if (isDashed) {
                    drawRoundRect(
                        color = dashedColor,
                        cornerRadius = corner,
                        style = Stroke(
                            width = AppDesign.ThinStroke.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx()))
                        )
                    )
                } else {
                    drawRoundRect(
                        color = resolvedBorderColor,
                        cornerRadius = corner,
                        style = Stroke(width = AppDesign.ThinStroke.toPx())
                    )
                }
            }
            .clickable(
                interactionSource = fieldInteraction,
                indication = null,
                onClick = {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
            )
            .padding(horizontal = 19.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label.uppercase(),
                color = AppDesign.PaleSky,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.66.sp,
                maxLines = 1
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag(testTag),
                singleLine = true,
                textStyle = TextStyle(
                    color = AppDesign.TitleText,
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
                            color = AppDesign.Ghost,
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
internal fun AdminSelectorField(
    label: String,
    value: String?,
    placeholder: String,
    testTag: String,
    leadingMarkerColor: Color = AppDesign.Transparent,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(AppDesign.SurfaceBackground, RoundedCornerShape(12.84.dp))
            .border(AppDesign.ThinStroke, AppDesign.Accent, RoundedCornerShape(12.84.dp))
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
                    color = AppDesign.PaleSky,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = value ?: placeholder,
                    color = if (value == null) AppDesign.Ghost else AppDesign.TitleText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(AppDesign.LightStroke, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AppDesign.PaleSky,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (leadingMarkerColor != AppDesign.Transparent) {
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
internal fun AdminDashedActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(AppDesign.SurfaceBackground, RoundedCornerShape(16.dp))
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = AppDesign.PlaceholderStroke,
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(
                        width = AppDesign.ThinStroke.toPx(),
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
internal fun AdminBikePhotoCard(testTag: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(202.dp)
            .background(AppDesign.InputFill, RoundedCornerShape(14.dp))
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = AppDesign.Accent,
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                    style = Stroke(
                        width = AppDesign.ThinStroke.toPx(),
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
                    .background(AppDesign.SurfaceBackground, RoundedCornerShape(14.dp))
                    .border(AppDesign.ThinStroke, AppDesign.Accent, RoundedCornerShape(14.dp)),
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
                color = AppDesign.PaleSky,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

internal enum class RentalClientPickerFilter(val title: String) {
    All("Все"),
    Debtors("Должники"),
    Active("Активные")
}

@Composable
internal fun RentalClientPickerSheet(
    clients: List<AdminClientSummaryResponse>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    listTag: String
) {
    var searchText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(RentalClientPickerFilter.All) }
    val overlayInteraction = remember { MutableInteractionSource() }
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
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = overlayInteraction,
                    indication = null,
                    onClick = {}
                )
                .zIndex(0f)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .zIndex(1f)
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
                    color = AppDesign.SurfaceBackground
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
                        .background(AppDesign.SurfaceBackground)
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
                                    .border(AppDesign.HairlineStroke, AppDesign.ControlStroke, RoundedCornerShape(12.dp)),
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
                            HorizontalDivider(color = AppDesign.LightStroke, thickness = AppDesign.HairlineStroke)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RentalBikePickerSheet(
    bikes: List<AdminBikeResponse>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    listTag: String
) {
    var searchText by remember { mutableStateOf("") }
    val overlayInteraction = remember { MutableInteractionSource() }
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
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = overlayInteraction,
                    indication = null,
                    onClick = {}
                )
                .zIndex(0f)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .zIndex(1f)
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
                    color = AppDesign.SurfaceBackground
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
                        .background(AppDesign.SurfaceBackground)
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
                                    .border(AppDesign.HairlineStroke, AppDesign.ControlStroke, RoundedCornerShape(12.dp)),
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
                            HorizontalDivider(color = AppDesign.LightStroke, thickness = AppDesign.HairlineStroke)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RentalPickerHeader(
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
                .background(AppDesign.SurfaceBackground, RoundedCornerShape(14.dp))
                .border(AppDesign.ThinStroke, AppDesign.Accent, RoundedCornerShape(14.dp))
                .clickable(onClick = onClose)
                .testTag("selection_picker_close_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = AppDesign.Accent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(title, color = AppDesign.DarkText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(47.dp)
                .background(AppDesign.Accent, RoundedCornerShape(14.dp))
                .border(AppDesign.ThinStroke, AppDesign.Accent, RoundedCornerShape(14.dp))
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
internal fun RentalPickerSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(AppDesign.SurfaceBackground, RoundedCornerShape(12.84.dp))
            .border(AppDesign.ThinStroke, AppDesign.Accent, RoundedCornerShape(12.84.dp))
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
                    Text(placeholder, color = AppDesign.Ghost, fontSize = 13.sp)
                }
                inner()
            }
        )
    }
}

@Composable
internal fun RentalPickerFilterChip(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) AppDesign.Accent else AppDesign.SurfaceBackground
    val textColor = if (selected) AppDesign.SurfaceBackground else AppDesign.Accent
    val countBg = if (selected) AppDesign.SurfaceBackground.copy(alpha = 0.2f) else AppDesign.Black.copy(alpha = 0.08f)

    Row(
        modifier = Modifier
            .height(36.dp)
            .background(bg, RoundedCornerShape(999.dp))
            .border(AppDesign.ThinStroke, AppDesign.Accent, RoundedCornerShape(999.dp))
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
internal fun AdminUpdateClientDialog(
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
internal fun AdminUpdateBikeDialog(
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
internal fun AdminUpdateRentalDialog(
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
internal fun AdminFinishRentalDialog(
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
internal fun AdminStartRentalDialog(
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
internal fun StatBlock(title: String, value: String, valueColor: Color) {
    Column(modifier = Modifier.widthIn(min = 72.dp)) {
        Text(title, fontSize = 10.sp, color = AppDesign.SubtleText)
        Text(value, fontSize = 13.sp, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

internal fun amountForType(type: ClientPaymentType, dashboard: ClientDashboardResponse): Int {
    return when (type) {
        ClientPaymentType.Day -> dashboard.presets.dayRub
        ClientPaymentType.Week -> dashboard.presets.weekRub
        ClientPaymentType.TwoWeeks -> dashboard.presets.twoWeeksRub
        ClientPaymentType.Month -> dashboard.presets.monthRub
        ClientPaymentType.DebtExact -> dashboard.presets.debtExactRub
    }
}

internal fun money(value: Int): String = "${DecimalFormat("#,###").format(value).replace(',', ' ')} ₽"

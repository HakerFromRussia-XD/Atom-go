package com.atomgo.android.presentation.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atomgo.android.AppDesign
import com.atomgo.android.ClientPaymentType
import com.atomgo.android.R
import com.atomgo.android.presentation.logic.AdminCatalogFilterEngine
import com.atomgo.android.presentation.model.*
import com.atomgo.android.presentation.viewmodel.*
import com.atomgo.shared.api.AdminBikeResponse
import com.atomgo.shared.api.AdminClientSummaryResponse
import com.atomgo.shared.api.AdminClientDetailsResponse
import com.atomgo.shared.api.AdminRentalHistoryItemResponse
import com.atomgo.shared.api.ClientDashboardResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal data class RentStatusPill(
    val title: String,
    val value: String,
    val color: Color,
    val widthDp: Int
)

@Composable
private fun AdminHomeTabLayer(
    active: Boolean,
    precompose: Boolean = false,
    premeasure: Boolean = false,
    content: @Composable () -> Unit
) {
    if (!active && !precompose && !premeasure) return

    val layerModifier = Modifier
        .fillMaxSize()
        .zIndex(if (active) 1f else 0f)
        .alpha(if (active) 1f else 0f)
        .then(if (active) Modifier else Modifier.clearAndSetSemantics {})

    Layout(
        content = content,
        modifier = layerModifier
    ) { measurables, constraints ->
        if (active || precompose || premeasure) {
            val placeable = measurables.firstOrNull()?.measure(constraints)
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable?.placeRelative(if (active) 0 else -constraints.maxWidth * 2, 0)
            }
        } else {
            layout(constraints.maxWidth, constraints.maxHeight) {}
        }
    }
}

@Composable
internal fun AdminSquareTopButton(
    iconRes: Int,
    testTag: String,
    borderColor: Color = AppDesign.Accent,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = AppDesign.SurfaceBackground,
            contentColor = borderColor
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
internal fun AdminSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .background(AppDesign.SurfaceBackground, RoundedCornerShape(12.84.dp))
            .border(AppDesign.HairlineStroke, AppDesign.Accent, RoundedCornerShape(12.84.dp))
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
                            color = AppDesign.SearchPlaceholder
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}

@Composable
internal fun AdminFilterRows(
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
internal fun AdminFilterHitRows(
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
internal fun AdminFilterHitTarget(
    width: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .width(width)
            .height(36.dp)
            .adminClickable(shape = shape, enabled = enabled, onClick = onClick)
    )
}

@Composable
internal fun AdminFilterChip(
    title: String,
    count: Int,
    width: androidx.compose.ui.unit.Dp,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) AppDesign.Accent else AppDesign.SurfaceBackground,
        animationSpec = tween(180),
        label = "admin_filter_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) AppDesign.SurfaceBackground else AppDesign.Accent,
        animationSpec = tween(180),
        label = "admin_filter_text"
    )
    val counterColor by animateColorAsState(
        targetValue = if (isSelected) AppDesign.SurfaceBackground.copy(alpha = 0.2f) else AppDesign.SelectedMuted,
        animationSpec = tween(180),
        label = "admin_filter_counter"
    )

    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 0.dp),
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, AppDesign.Accent),
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
internal fun AdminBottomTabBar(
    selectedTab: AdminHomeTab,
    onTabSelected: (AdminHomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("admin_bottom_tab_bar"),
        color = AppDesign.SurfaceBackground,
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
                    selectedIcon = Icons.AutoMirrored.Filled.DirectionsBike,
                    unselectedIcon = Icons.AutoMirrored.Outlined.DirectionsBike,
                    tag = "admin_tab_bikes",
                    modifier = Modifier.weight(1f),
                    onClick = onTabSelected
                )
            }
        }
    }
}

@Composable
internal fun AdminBottomTabItem(
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
    val color = if (isSelected) AppDesign.DarkText else AppDesign.IconSoft
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .adminClickable(shape = shape) { onClick(tab) }
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
                .background(if (isSelected) AppDesign.DarkText else AppDesign.Transparent, RoundedCornerShape(999.dp))
        )
    }
}

@Composable
internal fun AdminSecondaryTabStub(
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
            color = AppDesign.DarkText,
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
            border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, AppDesign.Accent),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = AppDesign.SurfaceBackground,
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
internal fun AdminHomeScreen(
    session: AuthSession,
    adminHomeViewModel: AdminHomeViewModel,
    onLogout: () -> Unit
) {
    var rents by remember { mutableStateOf<List<AdminClientSummaryResponse>>(emptyList()) }
    var clientsCatalog by remember { mutableStateOf<List<AdminClientSummaryResponse>>(emptyList()) }
    var bikesCatalog by remember { mutableStateOf<List<AdminBikeResponse>>(emptyList()) }
    var isRentsLoading by remember { mutableStateOf(true) }
    var rentsError by remember { mutableStateOf<String?>(null) }
    var isClientsLoading by remember { mutableStateOf(true) }
    var clientsError by remember { mutableStateOf<String?>(null) }
    var isBikesLoading by remember { mutableStateOf(true) }
    var bikesError by remember { mutableStateOf<String?>(null) }
    var rentsFilter by remember { mutableStateOf(AdminRentFilter.All) }
    var rentsSearch by remember { mutableStateOf("") }
    var clientsFilter by remember { mutableStateOf(AdminClientFilter.All) }
    var clientsSearch by remember { mutableStateOf("") }
    var bikesFilter by remember { mutableStateOf(AdminBikeFilter.All) }
    var bikesSearch by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(AdminHomeTab.Rents) }
    val rentsListState = rememberLazyListState()
    val clientsListState = rememberLazyListState()
    val bikesListState = rememberLazyListState()
    var adminMessage by remember { mutableStateOf<String?>(null) }
    var showCreateClient by remember { mutableStateOf(false) }
    var showCreateBike by remember { mutableStateOf(false) }
    var showCreateRental by remember { mutableStateOf(false) }
    var showUpdateClient by remember { mutableStateOf(false) }
    var selectedBikeForEdit by remember { mutableStateOf<AdminBikeResponse?>(null) }
    var showUpdateRental by remember { mutableStateOf(false) }
    var confirmDeleteRentalId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteClientId by remember { mutableStateOf<String?>(null) }
    var deletingClientId by remember { mutableStateOf<String?>(null) }
    var showFinishRentalFor by remember { mutableStateOf<AdminClientSummaryResponse?>(null) }
    var showStartRental by remember { mutableStateOf(false) }
    var detailClientId by remember { mutableStateOf<String?>(null) }
    var detailPayload by remember { mutableStateOf<AdminClientDetailsResponse?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }
    var selectedRentalDetails by remember { mutableStateOf<AdminRentalPreview?>(null) }
    var isRentalDetailsLoading by remember { mutableStateOf(false) }
    var rentalAdjustmentTarget by remember { mutableStateOf<AdminRentalPreview?>(null) }
    var isRentalAdjustmentVisible by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var bikeForEditTransition by remember { mutableStateOf<AdminBikeResponse?>(null) }
    var rentalDetailsTransition by remember { mutableStateOf<AdminRentalPreview?>(null) }
    var stackDeferredWorkAllowedAfterMillis by remember { mutableStateOf(0L) }
    var composedTabs by remember { mutableStateOf(setOf(AdminHomeTab.Rents)) }
    var premeasureTab by remember { mutableStateOf<AdminHomeTab?>(null) }
    var prewarmUpdateRental by remember { mutableStateOf(false) }
    val transitionWorkScope = rememberCoroutineScope()

    fun markStackTransitionWindow() {
        stackDeferredWorkAllowedAfterMillis = SystemClock.uptimeMillis() + AppStackDeferredWorkMillis
    }

    fun runWhenStackTransitionSettled(block: () -> Unit) {
        transitionWorkScope.launch {
            val delayMillis = stackDeferredWorkAllowedAfterMillis - SystemClock.uptimeMillis()
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            block()
        }
    }

    LaunchedEffect(isRentalAdjustmentVisible) {
        if (!isRentalAdjustmentVisible && rentalAdjustmentTarget != null) {
            delay(220)
            rentalAdjustmentTarget = null
        }
    }

    fun refreshRents() {
        isRentsLoading = true
        rentsError = null
        adminHomeViewModel.fetchAdminRents(session.accessToken) { result ->
            runWhenStackTransitionSettled {
                result.onSuccess {
                    rents = it
                    isRentsLoading = false
                }.onFailure {
                    rentsError = it.message ?: "Ошибка загрузки"
                    isRentsLoading = false
                }
            }
        }
    }
    fun refreshClients() {
        isClientsLoading = true
        clientsError = null
        adminHomeViewModel.fetchAdminClients(session.accessToken) { result ->
            runWhenStackTransitionSettled {
                result.onSuccess {
                    clientsCatalog = it
                    isClientsLoading = false
                }.onFailure {
                    clientsError = it.message ?: "Ошибка загрузки"
                    isClientsLoading = false
                }
            }
        }
    }
    fun refreshBikes() {
        isBikesLoading = true
        bikesError = null
        adminHomeViewModel.fetchAdminBikes(session.accessToken) { result ->
            runWhenStackTransitionSettled {
                result.onSuccess {
                    bikesCatalog = it
                    isBikesLoading = false
                }.onFailure {
                    bikesError = it.message ?: "Ошибка загрузки"
                    isBikesLoading = false
                }
            }
        }
    }
    fun refreshAllCatalogs() {
        refreshRents()
        refreshClients()
        refreshBikes()
    }
    fun refreshAllCatalogsAfterStackTransition() {
        markStackTransitionWindow()
        runWhenStackTransitionSettled { refreshAllCatalogs() }
    }
    fun openClientDetails(clientId: String) {
        val normalizedClientId = clientId.trim()
        if (normalizedClientId.isEmpty()) {
            adminMessage = "Клиент не найден"
            return
        }
        if (detailClientId != normalizedClientId) {
            isDetailLoading = true
            detailPayload = null
            detailClientId = normalizedClientId
            markStackTransitionWindow()
        }
        runWhenStackTransitionSettled {
            if (detailClientId != normalizedClientId) return@runWhenStackTransitionSettled
            isDetailLoading = true
            adminHomeViewModel.fetchAdminClientDetails(session.accessToken, normalizedClientId) { result ->
                runWhenStackTransitionSettled {
                    if (detailClientId != normalizedClientId) return@runWhenStackTransitionSettled
                    result.onSuccess {
                        detailPayload = it
                        isDetailLoading = false
                    }.onFailure {
                        adminMessage = "Ошибка загрузки деталей клиента: ${it.message}"
                        isDetailLoading = false
                    }
                }
            }
        }
    }
    fun openRentalDetails(rentalId: String, fallback: AdminRentalPreview? = null) {
        val normalizedRentalId = rentalId.trim()
        if (normalizedRentalId.isEmpty()) {
            adminMessage = "Аренда не найдена"
            return
        }
        if (selectedRentalDetails?.rentalId != normalizedRentalId) {
            selectedRentalDetails = fallback
            isRentalDetailsLoading = true
            markStackTransitionWindow()
        }
        runWhenStackTransitionSettled {
            if (selectedRentalDetails?.rentalId != normalizedRentalId) return@runWhenStackTransitionSettled
            isRentalDetailsLoading = true
            adminHomeViewModel.fetchAdminRentalDetails(session.accessToken, normalizedRentalId) { result ->
                runWhenStackTransitionSettled {
                    if (selectedRentalDetails?.rentalId != normalizedRentalId) return@runWhenStackTransitionSettled
                    result.onSuccess {
                        selectedRentalDetails = AdminRentalPreview.fromDetails(it)
                        isRentalDetailsLoading = false
                    }.onFailure {
                        if (fallback == null) {
                            selectedRentalDetails = null
                        }
                        adminMessage = "Ошибка загрузки аренды: ${it.message}"
                        isRentalDetailsLoading = false
                    }
                }
            }
        }
    }
    fun openRentalDetailsFromSummary(summary: AdminClientSummaryResponse) {
        val rentalId = summary.rentalId.orEmpty()
        openRentalDetails(
            rentalId = rentalId,
            fallback = rentalId.takeIf { it.isNotBlank() }?.let {
                AdminRentalPreview.fromSummary(summary = summary, rentalId = it)
            }
        )
    }
    fun refreshSelectedRentalDetails() {
        val rentalId = selectedRentalDetails?.rentalId.orEmpty()
        if (rentalId.isNotBlank()) {
            openRentalDetails(rentalId = rentalId, fallback = selectedRentalDetails)
        }
    }
    fun updateRentalPipelineStatus(item: AdminClientSummaryResponse, pipelineStatus: String) {
        val rentalId = item.rentalId ?: return
        adminHomeViewModel.updateAdminRentalPipelineStatus(
            accessToken = session.accessToken,
            rentalId = rentalId,
            pipelineStatus = pipelineStatus
        ) { result ->
            result.onSuccess {
                adminMessage = "Статус аренды обновлен"
                refreshRents()
            }.onFailure {
                adminMessage = "Ошибка обновления статуса: ${it.message}"
            }
        }
    }
    fun finishRentalToMine(item: AdminClientSummaryResponse) {
        val rentalId = item.rentalId ?: return
        adminHomeViewModel.finishAdminRentalByLifecycle(
            accessToken = session.accessToken,
            rentalId = rentalId
        ) { result ->
            result.onSuccess {
                adminMessage = "Аренда завершена"
                refreshAllCatalogsAfterStackTransition()
            }.onFailure {
                adminMessage = "Ошибка завершения аренды: ${it.message}"
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(AppStackDeferredWorkMillis.toLong() + 260L)
        refreshRents()
        delay(120L)
        refreshClients()
        delay(120L)
        refreshBikes()
    }
    LaunchedEffect(selectedTab) {
        composedTabs = composedTabs + selectedTab
        premeasureTab = null
        when (selectedTab) {
            AdminHomeTab.Clients -> if (clientsCatalog.isEmpty() && !isClientsLoading) refreshClients()
            AdminHomeTab.Bikes -> if (bikesCatalog.isEmpty() && !isBikesLoading) refreshBikes()
            AdminHomeTab.Rents -> Unit
        }
    }
    LaunchedEffect(Unit) {
        delay(AppStackDeferredWorkMillis.toLong() + 160L)
        listOf(AdminHomeTab.Clients, AdminHomeTab.Bikes).forEach { tab ->
            composedTabs = composedTabs + tab
            premeasureTab = tab
            delay(96L)
            if (premeasureTab == tab) {
                premeasureTab = null
            }
        }
    }
    LaunchedEffect(clientsCatalog) {
        if (clientsCatalog.isNotEmpty() && selectedTab != AdminHomeTab.Clients) {
            composedTabs = composedTabs + AdminHomeTab.Clients
            premeasureTab = AdminHomeTab.Clients
            delay(96L)
            if (premeasureTab == AdminHomeTab.Clients) {
                premeasureTab = null
            }
        }
    }
    LaunchedEffect(bikesCatalog) {
        if (bikesCatalog.isNotEmpty() && selectedTab != AdminHomeTab.Bikes) {
            composedTabs = composedTabs + AdminHomeTab.Bikes
            premeasureTab = AdminHomeTab.Bikes
            delay(96L)
            if (premeasureTab == AdminHomeTab.Bikes) {
                premeasureTab = null
            }
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
    LaunchedEffect(selectedBikeForEdit) {
        selectedBikeForEdit?.let { bikeForEditTransition = it }
        if (selectedBikeForEdit == null && bikeForEditTransition != null) {
            delay(AppStackTransitionMillis.toLong())
            bikeForEditTransition = null
        }
    }
    LaunchedEffect(selectedRentalDetails) {
        selectedRentalDetails?.let { rentalDetailsTransition = it }
        if (selectedRentalDetails == null && rentalDetailsTransition != null) {
            delay(AppStackTransitionMillis.toLong())
            rentalDetailsTransition = null
        }
    }
    LaunchedEffect(selectedRentalDetails?.rentalId) {
        val rentalId = selectedRentalDetails?.rentalId
        prewarmUpdateRental = false
        if (rentalId != null) {
            delay(AppStackDeferredWorkMillis.toLong())
            if (selectedRentalDetails?.rentalId == rentalId) {
                prewarmUpdateRental = true
            }
        }
    }

    val derivedData = remember(
        rents,
        rentsSearch,
        rentsFilter,
        clientsCatalog,
        clientsSearch,
        clientsFilter,
        bikesCatalog,
        bikesSearch,
        bikesFilter
    ) {
        AdminCatalogFilterEngine.derive(
            rents = rents,
            rentsSearch = rentsSearch,
            rentsFilter = rentsFilter,
            clientsCatalog = clientsCatalog,
            clientsSearch = clientsSearch,
            clientsFilter = clientsFilter,
            bikesCatalog = bikesCatalog,
            bikesSearch = bikesSearch,
            bikesFilter = bikesFilter
        )
    }
    val filteredRents = derivedData.filteredRents
    val filteredClients = derivedData.filteredClients
    val filteredBikes = derivedData.filteredBikes
    val filterCounts = derivedData.rentCounters
    val clientsFilterCounts = derivedData.clientCounters
    val bikesFilterCounts = derivedData.bikeCounters
    val density = LocalDensity.current
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val stackScreenVisible =
        showCreateClient ||
            showCreateBike ||
            showCreateRental ||
            selectedBikeForEdit != null ||
            detailClientId != null ||
            selectedRentalDetails != null

    LaunchedEffect(stackScreenVisible, showUpdateClient, showUpdateRental) {
        if (stackScreenVisible || showUpdateClient || showUpdateRental) {
            markStackTransitionWindow()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesign.PageBackground)
            .appStackUnderlyingOffset(
                active = stackScreenVisible,
                label = "adminHomeUnderlyingOffset"
            )
    ) {
        AdminHomeTabLayer(
            active = selectedTab == AdminHomeTab.Rents,
            precompose = AdminHomeTab.Rents in composedTabs,
            premeasure = premeasureTab == AdminHomeTab.Rents
        ) {
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
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(2f)
                                    .testTag("admin_rents_list"),
                                state = rentsListState,
                                contentPadding = PaddingValues(bottom = bottomCardsInset)
                            ) {
                                item("admin_rents_top_spacer") {
                                    Spacer(Modifier.height(cardsInitialTop))
                                }
                                if (filteredRents.isEmpty()) {
                                    item("admin_rents_empty") {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 14.dp),
                                            shape = RoundedCornerShape(15.dp),
                                            color = AppDesign.BlackHaze,
                                            border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, AppDesign.Accent)
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
                                            color = AppDesign.BlackHaze,
                                            border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, AppDesign.Accent)
                                        ) {
                                            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                                                filteredRents.forEachIndexed { index, item ->
                                                    AdminRentCard(
                                                        item = item,
                                                        isFirst = index == 0,
                                                        onDetails = { openRentalDetailsFromSummary(item) },
                                                        onSetLongTerm = { updateRentalPipelineStatus(item, "long_term") },
                                                        onSetSoonReturn = { updateRentalPipelineStatus(item, "soon_return") },
                                                        onSetMine = { finishRentalToMine(item) }
                                                    )
                                                    if (index < filteredRents.lastIndex) {
                                                        HorizontalDivider(color = AppDesign.LightStroke, thickness = AppDesign.HairlineStroke)
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
                                color = AppDesign.DarkText,
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

        AdminHomeTabLayer(
            active = selectedTab == AdminHomeTab.Clients,
            precompose = AdminHomeTab.Clients in composedTabs,
            premeasure = premeasureTab == AdminHomeTab.Clients
        ) {
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
                    visibleClients = filteredClients,
                    listState = clientsListState
                )
        }

        AdminHomeTabLayer(
            active = selectedTab == AdminHomeTab.Bikes,
            precompose = AdminHomeTab.Bikes in composedTabs,
            premeasure = premeasureTab == AdminHomeTab.Bikes
        ) {
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
                    onOpenBike = { bike -> selectedBikeForEdit = bike },
                    visibleBikes = filteredBikes,
                    listState = bikesListState
                )
        }

        AnimatedVisibility(
            visible = !stackScreenVisible,
            enter = appBottomNavEnter(),
            exit = appBottomNavExit(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(8f)
        ) {
            AdminBottomTabBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    if (selectedTab != tab) selectedTab = tab
                }
            )
        }

        AppToast(
            message = toastMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(9f),
            bottomPadding = 96
        )
    }

    AppStackVisibility(
        visible = showCreateClient,
        modifier = Modifier.fillMaxSize().zIndex(12f)
    ) {
        AdminCreateClientDialog(
            onDismiss = { showCreateClient = false },
            onCreate = { fullName, address, passport, phoneLabel, phoneNumber ->
                adminHomeViewModel.createAdminClient(
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
                        refreshAllCatalogsAfterStackTransition()
                    }.onFailure { adminMessage = "Ошибка создания клиента: ${it.message}" }
                }
            }
        )
    }

    AppStackVisibility(
        visible = showCreateBike,
        modifier = Modifier.fillMaxSize().zIndex(12f)
    ) {
        AdminCreateBikeDialog(
            onDismiss = { showCreateBike = false },
            onCreate = { model, rate, frame, motor, battery1, battery2 ->
                adminHomeViewModel.createAdminBike(
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
                        refreshAllCatalogsAfterStackTransition()
                    }.onFailure { adminMessage = "Ошибка создания велосипеда: ${it.message}" }
                }
            }
        )
    }

    AppStackVisibility(
        visible = showCreateRental,
        modifier = Modifier.fillMaxSize().zIndex(12f)
    ) {
        AdminCreateRentalDialog(
            clients = clientsCatalog,
            bikes = bikesCatalog,
            onDismiss = { showCreateRental = false },
            onCreate = { clientId, bikeId, login, password, periodStart, periodEnd, paymentDay, videoUrl, contractUrl, comment ->
                adminHomeViewModel.createAdminRental(
                    accessToken = session.accessToken,
                    clientId = clientId.ifBlank { null },
                    bikeId = bikeId,
                    login = login,
                    password = password,
                    periodStart = periodStart,
                    periodEnd = periodEnd.ifBlank { null },
                    paymentDay = paymentDay,
                    videoUrl = videoUrl.ifBlank { null },
                    contractUrl = contractUrl.ifBlank { null },
                    comment = comment.ifBlank { null }
                ) { result ->
                    result.onSuccess {
                        adminMessage = "Аренда создана"
                        showCreateRental = false
                        refreshAllCatalogsAfterStackTransition()
                    }.onFailure { adminMessage = "Ошибка создания аренды: ${it.message}" }
                }
            }
        )
    }

    AppStackVisibility(
        visible = selectedBikeForEdit != null,
        modifier = Modifier.fillMaxSize().zIndex(12f)
    ) {
        val editingBike = selectedBikeForEdit ?: bikeForEditTransition ?: return@AppStackVisibility
        AdminUpdateBikeDialog(
            bike = editingBike,
            bikes = bikesCatalog,
            onDismiss = { selectedBikeForEdit = null },
            onUpdate = { bikeId, photoUrl, model, rate, frame, motor, battery1, battery2 ->
                adminHomeViewModel.updateAdminBike(
                    accessToken = session.accessToken,
                    bikeId = bikeId,
                    photoUrl = photoUrl,
                    bikeModel = model,
                    weeklyRateRub = rate.toIntOrNull() ?: 0,
                    frameSerialNumber = frame,
                    motorSerialNumber = motor,
                    batterySerialNumber1 = battery1,
                    batterySerialNumber2 = battery2.ifBlank { null }
                ) { result ->
                    result.onSuccess {
                        adminMessage = "Велосипед обновлен"
                        selectedBikeForEdit = null
                        refreshAllCatalogsAfterStackTransition()
                    }.onFailure { adminMessage = "Ошибка обновления велосипеда: ${it.message}" }
                }
            },
            onDelete = { bikeId ->
                adminHomeViewModel.deleteAdminBike(session.accessToken, bikeId) { result ->
                    result.onSuccess {
                        adminMessage = "Велосипед удален"
                        selectedBikeForEdit = null
                        refreshAllCatalogsAfterStackTransition()
                    }.onFailure { adminMessage = "Ошибка удаления велосипеда: ${it.message}" }
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
                adminHomeViewModel.finishAdminRental(
                    accessToken = session.accessToken,
                    rentalId = rentalId,
                    bikeId = bikeId,
                    periodStart = periodStart,
                    finishDate = finishDate
                ) { result ->
                    result.onSuccess {
                        adminMessage = "Аренда завершена"
                        showFinishRentalFor = null
                        refreshAllCatalogsAfterStackTransition()
                    }.onFailure { adminMessage = "Ошибка завершения аренды: ${it.message}" }
                }
            }
        )
    }

    if (showStartRental) {
        AdminStartRentalDialog(
            onDismiss = { showStartRental = false },
            onStart = { clientId, bikeId, login, password, periodStart ->
                adminHomeViewModel.startAdminRental(
                    accessToken = session.accessToken,
                    clientId = clientId,
                    bikeId = bikeId,
                    login = login,
                    password = password,
                    periodStart = periodStart,
                    paymentDay = todayRentalPaymentDay()
                ) { result ->
                    result.onSuccess {
                        adminMessage = "Аренда запущена"
                        showStartRental = false
                        refreshAllCatalogsAfterStackTransition()
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
                    adminHomeViewModel.deleteAdminRental(session.accessToken, rentalId) { result ->
                        result.onSuccess {
                            adminMessage = "Аренда удалена"
                            confirmDeleteRentalId = null
                            refreshAllCatalogsAfterStackTransition()
                        }.onFailure { adminMessage = "Ошибка удаления аренды: ${it.message}" }
                    }
                }) { Text("Удалить") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDeleteRentalId = null }) { Text("Отмена") } }
        )
    }

    AppStackVisibility(
        visible = detailClientId != null,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(21f)
            .appStackUnderlyingOffset(
                active = showUpdateClient,
                label = "adminClientDetailUnderlyingOffset"
            )
    ) {
        AdminClientDetailsScreen(
            details = detailPayload,
            isLoading = isDetailLoading,
            isOperationInProgress = deletingClientId != null,
            onClose = { detailClientId = null },
            onRetry = { detailClientId?.let(::openClientDetails) },
            onEditProfile = {
                showUpdateClient = true
            },
            onDeleteClient = { clientId ->
                confirmDeleteClientId = clientId
            },
            onOpenRental = { preview ->
                openRentalDetails(rentalId = preview.rentalId, fallback = preview)
            }
        )
    }

    if (confirmDeleteClientId != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteClientId = null },
            title = { Text("Удалить клиента?") },
            text = { Text("Клиент будет скрыт в приложении, данные останутся на сервере.") },
            confirmButton = {
                OutlinedButton(
                    enabled = deletingClientId == null,
                    onClick = {
                        val clientId = confirmDeleteClientId ?: return@OutlinedButton
                        confirmDeleteClientId = null
                        deletingClientId = clientId
                        adminHomeViewModel.deleteAdminClient(session.accessToken, clientId) { result ->
                            deletingClientId = null
                            result.onSuccess {
                                if (detailClientId == clientId) {
                                    detailClientId = null
                                    detailPayload = null
                                    isDetailLoading = false
                                }
                                adminMessage = "Клиент удален"
                                refreshAllCatalogsAfterStackTransition()
                            }.onFailure {
                                adminMessage = "Ошибка удаления клиента: ${it.message}"
                            }
                        }
                    }
                ) { Text("Удалить") }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = deletingClientId == null,
                    onClick = { confirmDeleteClientId = null }
                ) { Text("Отмена") }
            }
        )
    }

    AppStackVisibility(
        visible = selectedRentalDetails != null,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(22f)
            .appStackUnderlyingOffset(
                active = showUpdateRental,
                label = "adminRentalDetailUnderlyingOffset"
            )
    ) {
        val rentalDetails = selectedRentalDetails ?: rentalDetailsTransition
        AdminRentalDetailsScreenAndroid(
            details = rentalDetails,
            clients = clientsCatalog,
            isLoading = isRentalDetailsLoading,
            onClose = { selectedRentalDetails = null },
            onEdit = {
                if (!isRentalDetailsLoading) {
                    showUpdateRental = true
                }
            },
            onOpenClient = {
                val clientId = rentalDetails?.clientId?.trim().orEmpty()
                selectedRentalDetails = null
                if (clientId.isNotEmpty()) {
                    openClientDetails(clientId)
                } else {
                    adminMessage = "Клиент для этой аренды не найден"
                }
            },
            onAdjust = { preview ->
                rentalAdjustmentTarget = preview
                isRentalAdjustmentVisible = true
            },
            onFinish = { preview ->
                val rentalId = preview.rentalId
                adminHomeViewModel.finishAdminRentalByLifecycle(
                    accessToken = session.accessToken,
                    rentalId = rentalId
                ) { result ->
                    result.onSuccess {
                        adminMessage = "Аренда завершена"
                        refreshAllCatalogsAfterStackTransition()
                        refreshSelectedRentalDetails()
                    }.onFailure {
                        adminMessage = "Ошибка завершения аренды: ${it.message}"
                    }
                }
            },
            onStartRental = { rentalId, clientId, login, password, periodStart ->
                adminHomeViewModel.startClientRentalInExisting(
                    accessToken = session.accessToken,
                    rentalId = rentalId,
                    clientId = clientId,
                    login = login,
                    password = password,
                    periodStart = periodStart,
                    paymentDay = todayRentalPaymentDay()
                ) { result ->
                    result.onSuccess {
                        adminMessage = "Новая клиентская аренда запущена"
                        refreshAllCatalogsAfterStackTransition()
                        refreshSelectedRentalDetails()
                    }.onFailure {
                        adminMessage = "Ошибка запуска аренды: ${it.message}"
                    }
                }
            },
            onDelete = {
                val rentalId = rentalDetails?.rentalId ?: return@AdminRentalDetailsScreenAndroid
                adminHomeViewModel.deleteAdminRental(session.accessToken, rentalId) { result ->
                    result.onSuccess {
                        adminMessage = "Аренда удалена"
                        selectedRentalDetails = null
                        refreshAllCatalogsAfterStackTransition()
                    }.onFailure { adminMessage = "Ошибка удаления аренды: ${it.message}" }
                }
            }
        )
    }

    AppStackVisibility(
        visible = showUpdateClient && detailPayload != null,
        modifier = Modifier.fillMaxSize().zIndex(31f)
    ) {
        val details = detailPayload
        if (details != null) {
            AdminUpdateClientDialog(
                details = details,
                onDismiss = { showUpdateClient = false },
                onUpdate = { clientId, fullName, address, passport, phones, comment ->
                    adminHomeViewModel.updateAdminClient(
                        accessToken = session.accessToken,
                        clientId = clientId,
                        fullName = fullName,
                        address = address,
                        passportData = passport,
                        phones = phones,
                        comment = comment.ifBlank { null }
                    ) { result ->
                        result.onSuccess {
                            adminMessage = "Клиент обновлен"
                            showUpdateClient = false
                            refreshAllCatalogsAfterStackTransition()
                            openClientDetails(clientId)
                        }.onFailure { adminMessage = "Ошибка обновления клиента: ${it.message}" }
                    }
                }
            )
        }
    }

    AppStackVisibility(
        visible = showUpdateRental && selectedRentalDetails != null,
        precompose = prewarmUpdateRental && selectedRentalDetails != null,
        modifier = Modifier.fillMaxSize().zIndex(32f)
    ) {
        val details = selectedRentalDetails
        if (details != null) {
            AdminUpdateRentalDialog(
                details = details,
                clients = clientsCatalog,
                bikes = bikesCatalog,
                onDismiss = { showUpdateRental = false },
                onUpdate = { rentalId, _, bikeId, periodStart, periodEnd, paymentDay, login, password, videoUrl, contractUrl, comment ->
                    adminHomeViewModel.updateAdminRental(
                        accessToken = session.accessToken,
                        rentalId = rentalId,
                        bikeId = bikeId,
                        periodStart = periodStart,
                        periodEnd = periodEnd.ifBlank { null },
                        paymentDay = paymentDay,
                        login = login.ifBlank { null },
                        password = password.ifBlank { null },
                        videoUrl = videoUrl.ifBlank { null },
                        contractUrl = contractUrl.ifBlank { null },
                        comment = comment.ifBlank { null }
                    ) { result ->
                        result.onSuccess {
                            adminMessage = "Аренда обновлена"
                            showUpdateRental = false
                            refreshAllCatalogsAfterStackTransition()
                            refreshSelectedRentalDetails()
                        }.onFailure { adminMessage = "Ошибка обновления аренды: ${it.message}" }
                    }
                }
            )
        }
    }

    if (rentalAdjustmentTarget != null) {
        AdminRentalDebtAdjustmentDialog(
            title = "Корректировка долга",
            visible = isRentalAdjustmentVisible,
            onDismiss = { isRentalAdjustmentVisible = false },
            onApply = { amountRub, sign, comment ->
                val target = rentalAdjustmentTarget ?: return@AdminRentalDebtAdjustmentDialog
                val clientRentalId = target.clientRentalId?.trim().orEmpty()
                if (clientRentalId.isEmpty()) {
                    adminMessage = "Клиентская аренда для корректировки не найдена"
                    isRentalAdjustmentVisible = false
                    return@AdminRentalDebtAdjustmentDialog
                }
                if (sign == "cash") {
                    adminHomeViewModel.recordAdminClientRentalCashPayment(
                        accessToken = session.accessToken,
                        clientRentalId = clientRentalId,
                        amountRub = amountRub,
                        comment = comment.ifBlank { null }
                    ) { result ->
                        result.onSuccess {
                            adminMessage = "Оплата наличными сохранена"
                            isRentalAdjustmentVisible = false
                            refreshAllCatalogsAfterStackTransition()
                            refreshSelectedRentalDetails()
                        }.onFailure {
                            adminMessage = "Ошибка оплаты наличными: ${it.message}"
                        }
                    }
                    return@AdminRentalDebtAdjustmentDialog
                }
                adminHomeViewModel.adjustAdminClientRentalDebt(
                    accessToken = session.accessToken,
                    clientRentalId = clientRentalId,
                    amountRub = amountRub,
                    sign = sign,
                    comment = comment.ifBlank { null }
                ) { result ->
                    result.onSuccess {
                        adminMessage = "Корректировка сохранена"
                        isRentalAdjustmentVisible = false
                        refreshAllCatalogsAfterStackTransition()
                        refreshSelectedRentalDetails()
                    }.onFailure {
                        adminMessage = "Ошибка корректировки: ${it.message}"
                    }
                }
            }
        )
    }
}

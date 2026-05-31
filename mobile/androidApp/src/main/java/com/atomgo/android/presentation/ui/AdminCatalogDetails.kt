package com.atomgo.android.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.atomgo.android.AppDesign
import com.atomgo.android.ClientPaymentType
import com.atomgo.android.R
import com.atomgo.android.presentation.model.*
import com.atomgo.android.presentation.model.normalizedPipelineStatus
import com.atomgo.android.presentation.viewmodel.*
import com.atomgo.shared.api.AdminBikeResponse
import com.atomgo.shared.api.AdminClientSummaryResponse
import com.atomgo.shared.api.AdminClientDetailsResponse
import com.atomgo.shared.api.AdminRentalDetailsResponse
import com.atomgo.shared.api.AdminRentalHistoryItemResponse
import com.atomgo.shared.api.AdminRentalJournalEntryResponse
import com.atomgo.shared.api.ClientDashboardResponse
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val AdminCatalogRowShape = RoundedCornerShape(15.dp)
private val AdminCatalogAvatarShape = RoundedCornerShape(12.dp)
private val AdminCatalogCallShape = RoundedCornerShape(12.dp)

@Composable
internal fun AdminClientsCatalogScreen(
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
    visibleClients: List<AdminClientSummaryResponse>,
    listState: LazyListState
) {
    val horizontalInset = 8.dp
    val topBarHeight = 62.dp
    val searchTopPadding = 6.dp
    val searchHeight = 46.dp
    val chipsTopGap = 10.dp
    val chipsHeight = 36.dp
    val chipsTop = statusBarTop + topBarHeight + searchTopPadding + searchHeight + chipsTopGap
    val cardsInitialTop = chipsTop + chipsHeight + chipsTopGap
    val searchMaskHeight = statusBarTop + topBarHeight + searchTopPadding + (searchHeight / 2)
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
                                color = AppDesign.BlackHaze,
                                border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, AppDesign.Accent)
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
                                color = AppDesign.BlackHaze,
                                border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, AppDesign.Accent)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 5.dp)) {
                                    visibleClients.forEachIndexed { index, item ->
                                        AdminClientCatalogRow(
                                            item = item,
                                            isFirst = index == 0,
                                            onClick = { onOpenClient(item) }
                                        )
                                        if (index < visibleClients.lastIndex) {
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
                Text("Клиенты", color = AppDesign.DarkText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
internal fun AdminBikesCatalogScreen(
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
    onOpenBike: (AdminBikeResponse) -> Unit,
    visibleBikes: List<AdminBikeResponse>,
    listState: LazyListState
) {
    val horizontalInset = 8.dp
    val topBarHeight = 62.dp
    val searchTopPadding = 6.dp
    val searchHeight = 46.dp
    val chipsTopGap = 10.dp
    val chipsHeight = 36.dp
    val chipsTop = statusBarTop + topBarHeight + searchTopPadding + searchHeight + chipsTopGap
    val cardsInitialTop = chipsTop + chipsHeight + chipsTopGap
    val searchMaskHeight = statusBarTop + topBarHeight + searchTopPadding + (searchHeight / 2)
    val bikeRuntimeByModel = remember(rentals) { bikeCatalogRuntimeSnapshots(rentals) }
    val freeBikeRuntime = remember { BikeCatalogRuntimeSnapshot(borderColor = AppDesign.IdlePurple, subtitle = "-") }
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
                                color = AppDesign.BlackHaze,
                                border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, AppDesign.Accent)
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
                                color = AppDesign.BlackHaze,
                                border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, AppDesign.Accent)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 5.dp)) {
                                    visibleBikes.forEachIndexed { index, bike ->
                                        AdminBikeCatalogRow(
                                            bike = bike,
                                            runtime = bikeRuntimeByModel[normalizeCatalogSearchText(bike.bikeModel)] ?: freeBikeRuntime,
                                            onClick = { onOpenBike(bike) }
                                        )
                                        if (index < visibleBikes.lastIndex) {
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
                Text("Велосипеды", color = AppDesign.DarkText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
internal fun AdminClientCatalogRow(
    item: AdminClientSummaryResponse,
    isFirst: Boolean = false,
    onClick: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val telUri = remember(item.primaryPhone) { telUriString(item.primaryPhone) }
    val isCallEnabled = telUri != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .adminClickable(shape = AdminCatalogRowShape, onClick = onClick)
            .padding(horizontal = 9.dp)
            .testTag(if (isFirst) "admin_client_row_first" else "admin_client_row_${item.clientId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .adminClickable(shape = AdminCatalogCallShape, enabled = isCallEnabled) {
                    telUri?.let(uriHandler::openUri)
                }
                .alpha(if (isCallEnabled) 1f else 0.45f)
                .background(AppDesign.SurfaceBackground, AdminCatalogCallShape)
                .border(AppDesign.ThinStroke, AppDesign.PaidGreen, AdminCatalogCallShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Phone,
                contentDescription = null,
                tint = AppDesign.PaidGreen,
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
                color = AppDesign.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = clientCatalogSubtitle(item),
                color = AppDesign.PaleSky,
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
                color = AppDesign.DangerRed,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(10.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppDesign.Chevron,
            modifier = Modifier.size(18.dp)
        )
    }
}

internal fun telUriString(rawPhone: String?): String? {
    val trimmed = rawPhone?.trim().orEmpty()
    if (trimmed.isEmpty()) return null

    val normalized = buildString {
        trimmed.forEachIndexed { index, ch ->
            when {
                ch.isDigit() -> append(ch)
                ch == '+' && index == 0 -> append(ch)
            }
        }
    }
    if (normalized.isEmpty()) return null
    return "tel://$normalized"
}

internal data class AdminRentalPreview(
    val rentalId: String,
    val clientId: String,
    val clientName: String,
    val bikeId: String,
    val bikeModel: String,
    val bikeAvatarUrl: String,
    val periodStart: String,
    val periodEnd: String?,
    val debtRub: Int,
    val totalPaidRub: Int,
    val totalAdjustmentRub: Int,
    val weeklyRateRub: Int,
    val clientLogin: String?,
    val clientPassword: String?,
    val videoUrl: String?,
    val contractUrl: String?,
    val paidUntil: String?,
    val rentalPipelineStatus: String?,
    val rentalIsActive: Boolean,
    val comment: String?,
    val clientRentalId: String?,
    val journalEntries: List<AdminRentalJournalEntryResponse>,
    val sourceLabel: String
) {
    companion object {
        fun fromSummary(summary: AdminClientSummaryResponse, rentalId: String): AdminRentalPreview {
            return AdminRentalPreview(
                rentalId = rentalId,
                clientId = summary.clientId,
                clientName = summary.fullName,
                bikeId = "",
                bikeModel = summary.bikeModel,
                bikeAvatarUrl = summary.bikeAvatarUrl,
                periodStart = summary.paidUntil.orEmpty(),
                periodEnd = null,
                debtRub = summary.debtRub,
                totalPaidRub = summary.profitRub,
                totalAdjustmentRub = summary.totalAdjustmentRub,
                weeklyRateRub = 0,
                clientLogin = summary.clientLogin,
                clientPassword = null,
                videoUrl = null,
                contractUrl = null,
                paidUntil = summary.paidUntil,
                rentalPipelineStatus = summary.rentalPipelineStatus,
                rentalIsActive = summary.rentalIsActive,
                comment = null,
                clientRentalId = null,
                journalEntries = emptyList(),
                sourceLabel = "lifecycle"
            )
        }

        fun fromHistory(client: AdminClientDetailsResponse, rental: AdminRentalHistoryItemResponse): AdminRentalPreview {
            return AdminRentalPreview(
                rentalId = rental.rentalId,
                clientId = client.clientId,
                clientName = client.fullName,
                bikeId = rental.bikeId,
                bikeModel = rental.bikeModel,
                bikeAvatarUrl = rental.bikeAvatarUrl,
                periodStart = rental.periodStart,
                periodEnd = rental.periodEnd,
                debtRub = rental.debtRub,
                totalPaidRub = rental.totalPaidRub,
                totalAdjustmentRub = rental.totalAdjustmentRub,
                weeklyRateRub = rental.weeklyRateRub,
                clientLogin = null,
                clientPassword = null,
                videoUrl = rental.videoUrl,
                contractUrl = rental.contractUrl,
                paidUntil = null,
                rentalPipelineStatus = null,
                rentalIsActive = rental.periodEnd.isNullOrBlank(),
                comment = rental.comment,
                clientRentalId = rental.rentalId,
                journalEntries = emptyList(),
                sourceLabel = if (rental.periodEnd.isNullOrBlank()) "active_client_rental" else "closed_client_rental"
            )
        }

        fun fromDetails(details: AdminRentalDetailsResponse): AdminRentalPreview {
            val isClosedClientRental = !details.completedAt.isNullOrBlank()
            val isLifecycleStock = details.clientRentalId.isNullOrBlank()
            return AdminRentalPreview(
                rentalId = details.rentalId,
                clientId = details.clientId,
                clientName = details.clientFullName.ifBlank { "Клиент не выбран" },
                bikeId = details.bikeId,
                bikeModel = details.bikeModel,
                bikeAvatarUrl = details.bikeAvatarUrl,
                periodStart = details.rentalStart,
                periodEnd = details.completedAt,
                debtRub = details.debtRub,
                totalPaidRub = details.totalPaidRub,
                totalAdjustmentRub = details.totalAdjustmentRub,
                weeklyRateRub = details.weeklyRateRub,
                clientLogin = details.clientLogin,
                clientPassword = details.clientPassword,
                videoUrl = details.videoUrl,
                contractUrl = details.contractUrl,
                paidUntil = details.paidUntil,
                rentalPipelineStatus = details.rentalPipelineStatus,
                rentalIsActive = details.rentalIsActive,
                comment = details.comment,
                clientRentalId = details.clientRentalId,
                journalEntries = details.journalEntries,
                sourceLabel = when {
                    isLifecycleStock -> "lifecycle"
                    isClosedClientRental -> "closed_client_rental"
                    else -> "active_client_rental"
                }
            )
        }
    }
}

internal fun rentalPreviewIsInStock(details: AdminRentalPreview): Boolean {
    if (!details.periodEnd.isNullOrBlank()) return false
    val status = normalizedPipelineStatus(details.rentalPipelineStatus)
    if (status == "in_stock" || status == "mine") return true
    return !details.rentalIsActive && details.clientRentalId.isNullOrBlank()
}

internal fun rentalPreviewIsRunning(details: AdminRentalPreview): Boolean {
    return details.rentalIsActive && !rentalPreviewIsInStock(details)
}

@Composable
internal fun AdminClientDetailsScreen(
    details: AdminClientDetailsResponse?,
    isLoading: Boolean,
    isOperationInProgress: Boolean,
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
                .padding(horizontal = 8.dp)
        ) {
            Spacer(Modifier.height(8.dp))
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
                        border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, AppDesign.Accent),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = AppDesign.SurfaceBackground,
                            contentColor = AppDesign.Accent
                        ),
                        modifier = Modifier
                            .size(47.dp)
                            .testTag("admin_client_details_edit")
                    ) {
                        Image(
                            painter = painterResource(R.drawable.refaktoring),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    val hasNoRentals = details?.rentals?.isEmpty() == true
                    val canDeleteClient = hasNoRentals && !isOperationInProgress
                    OutlinedButton(
                        onClick = { details?.clientId?.let(onDeleteClient) },
                        enabled = canDeleteClient,
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, AppDesign.Danger),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = AppDesign.SurfaceBackground,
                            contentColor = AppDesign.Danger
                        ),
                        modifier = Modifier
                            .size(47.dp)
                            .alpha(if (hasNoRentals) 1f else 0.45f)
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
                            .padding(top = 8.dp, bottom = 126.dp)
                            .navigationBarsPadding()
                            .testTag("admin_client_details_content"),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_client_details_card"),
                            shape = RoundedCornerShape(15.dp),
                            color = AppDesign.BlackHaze,
                            border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, AppDesign.Accent)
                        ) {
                            val cardPadding = if (hasOpenRental) {
                                PaddingValues(start = 23.dp, top = 21.dp, end = 23.dp, bottom = 21.dp)
                            } else {
                                PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp)
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(cardPadding),
                                verticalArrangement = Arrangement.spacedBy(if (hasOpenRental) 16.dp else 18.dp)
                            ) {
                                if (hasOpenRental) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ClientBikeAvatar(
                                            avatarUrl = d.bikeAvatarUrl,
                                            modifier = Modifier.size(80.dp),
                                            cornerRadius = 14.dp
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
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
                                                    .background(AppDesign.PaidGreen, RoundedCornerShape(999.dp))
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    "Активный",
                                                    color = AppDesign.SurfaceBackground,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = AppDesign.LightStroke, thickness = AppDesign.HairlineStroke)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .background(AppDesign.DarkControl, RoundedCornerShape(999.dp))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            "Неактивный",
                                            color = AppDesign.SurfaceBackground,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    MetricStack("ОПЛАЧЕНО", "+${money(d.totalPaidRub)}", AppDesign.Success)
                                    Spacer(Modifier.weight(1f))
                                    MetricStack("ДОЛГ", money(d.debtRub), if (d.debtRub > 0) AppDesign.Danger else AppDesign.TitleText)
                                    Spacer(Modifier.weight(1f))
                                    MetricStack("КОРРЕКТ.", money(d.totalAdjustmentRub), AppDesign.TitleText)
                                }

                                if (d.carriedDebtRub > 0) {
                                    HorizontalDivider(color = AppDesign.LightStroke, thickness = AppDesign.HairlineStroke)
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

                                val clientComment = d.comment?.trim().orEmpty()
                                if (clientComment.isNotEmpty()) {
                                    HorizontalDivider(color = AppDesign.LightStroke, thickness = AppDesign.HairlineStroke)
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "КОММЕНТАРИЙ",
                                            color = AppDesign.SubtleText,
                                            fontSize = 9.sp,
                                            letterSpacing = 0.54.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            clientComment,
                                            color = AppDesign.TitleText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                            }
                        }

                        ClientProfileBlock(details = d)
                        ClientRentalHistoryBlock(details = d, onOpenRental = onOpenRental)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientProfileBlock(details: AdminClientDetailsResponse) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ClientDetailsSectionTitle(
            title = "Профиль",
            modifier = Modifier.testTag("admin_client_profile_section")
        )
        AdminDetailsReadonlyField("ФИО", details.fullName)
        AdminDetailsReadonlyField("Адрес", details.address)
        AdminDetailsReadonlyField("Паспорт", details.passportData)
        details.phones.forEach { phone ->
            AdminDetailsReadonlyField(phone.label, phone.number)
        }
    }
}

@Composable
private fun ClientRentalHistoryBlock(
    details: AdminClientDetailsResponse,
    onOpenRental: (AdminRentalPreview) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ClientDetailsSectionTitle(title = "История аренд")

        if (details.rentals.isEmpty()) {
            Text(
                text = "История аренд пока пустая",
                color = AppDesign.SubtleText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                details.rentals.forEachIndexed { index, rental ->
                    ClientRentalHistoryRow(
                        details = details,
                        rental = rental,
                        isFirst = index == 0,
                        onOpenRental = onOpenRental
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientRentalHistoryRow(
    details: AdminClientDetailsResponse,
    rental: AdminRentalHistoryItemResponse,
    isFirst: Boolean,
    onOpenRental: (AdminRentalPreview) -> Unit
) {
    val shape = RoundedCornerShape(15.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(
                if (isFirst) "admin_client_history_row_first"
                else "admin_client_history_row_${rental.rentalId}"
            )
            .adminClickable(shape = shape) {
                onOpenRental(AdminRentalPreview.fromHistory(client = details, rental = rental))
            },
        shape = shape,
        color = AppDesign.BlackHaze,
        border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, AppDesign.LightStroke)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClientBikeAvatar(
                avatarUrl = rental.bikeAvatarUrl,
                modifier = Modifier.size(36.dp),
                cornerRadius = 10.dp,
                borderColor = null
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = clientHistoryPeriodText(rental),
                    color = AppDesign.TitleText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = rental.bikeModel,
                    color = AppDesign.SubtleText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = clientHistoryAmountText(rental),
                color = if (rental.debtRub > 0) AppDesign.Danger else AppDesign.Success,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppDesign.SubtleText,
                modifier = Modifier.size(10.dp)
            )
        }
    }
}

@Composable
private fun ClientDetailsSectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title.uppercase(),
        color = AppDesign.SubtleText,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.88.sp,
        modifier = modifier
    )
}

private fun clientHistoryPeriodText(rental: AdminRentalHistoryItemResponse): String {
    val start = formatShortRuDate(rental.periodStart)
    val end = rental.periodEnd
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(::formatLongRuDate)
        ?: "н.в."
    return "$start – $end"
}

private fun clientHistoryAmountText(rental: AdminRentalHistoryItemResponse): String {
    return if (rental.debtRub > 0) "- ${money(rental.debtRub)}" else "+${money(rental.totalPaidRub)}"
}

@Composable
internal fun AdminRentalDetailsScreenAndroid(
    details: AdminRentalPreview?,
    clients: List<AdminClientSummaryResponse>,
    isLoading: Boolean,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onOpenClient: () -> Unit,
    onAdjust: (AdminRentalPreview) -> Unit,
    onFinish: (AdminRentalPreview) -> Unit,
    onStartRental: (rentalId: String, clientId: String, login: String, password: String, periodStart: String) -> Unit,
    onDelete: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val overlayInteraction = remember { MutableInteractionSource() }
    val mainTextColor = AppDesign.DarkControl
    val subtleTextColor = AppDesign.PaleSky
    val dividerColor = AppDesign.LightStroke
    val credentialButtonColor = AppDesign.DarkControl
    var editableLogin by remember(details?.rentalId) { mutableStateOf(details?.clientLogin.orEmpty()) }
    var editablePassword by remember(details?.rentalId) { mutableStateOf(details?.clientPassword.orEmpty()) }
    var selectedStartClientId by remember(details?.rentalId) { mutableStateOf("") }
    var isClientPickerPresented by remember(details?.rentalId) { mutableStateOf(false) }
    var localMessage by remember(details?.rentalId) { mutableStateOf<String?>(null) }
    val availableStartClients = remember(clients) {
        clients
            .filter { !it.rentalIsActive }
            .sortedBy { it.fullName.lowercase() }
    }
    val selectedStartClient = remember(availableStartClients, selectedStartClientId) {
        availableStartClients.firstOrNull { it.clientId == selectedStartClientId }
    }

    LaunchedEffect(details?.rentalId, details?.clientLogin, details?.clientPassword, details?.clientRentalId) {
        editableLogin = details?.clientLogin.orEmpty()
        editablePassword = details?.clientPassword.orEmpty()
        if (details?.clientRentalId != null) {
            selectedStartClientId = ""
        }
    }
    LaunchedEffect(localMessage) {
        if (!localMessage.isNullOrBlank()) {
            delay(2200)
            localMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesign.PageBackground)
            .zIndex(30f)
            .testTag("admin_rental_details_screen")
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
                .padding(horizontal = 8.dp)
                .zIndex(1f)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(47.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AdminSquareTopButton(
                    iconRes = R.drawable.ic_back,
                    testTag = "admin_rental_details_back",
                    onClick = onClose,
                    borderColor = mainTextColor
                )
                Spacer(Modifier.weight(1f))
                Text("Аренда", color = AppDesign.TitleText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isLoading && details != null && details.rentalId.isNotBlank()) {
                        OutlinedButton(
                            onClick = onEdit,
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, mainTextColor),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = AppDesign.SurfaceBackground,
                                contentColor = mainTextColor
                            ),
                            modifier = Modifier
                                .size(47.dp)
                                .testTag("admin_rental_details_edit")
                        ) {
                            Image(
                                painter = painterResource(R.drawable.refaktoring),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, AppDesign.Danger),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = AppDesign.SurfaceBackground,
                            contentColor = AppDesign.Danger
                        ),
                        modifier = Modifier
                            .size(47.dp)
                            .testTag("admin_rental_details_delete")
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
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
                val isInStockState = rentalPreviewIsInStock(details)
                val runningRentalIsActive = rentalPreviewIsRunning(details)
                val canAdjust = !isInStockState && !details.clientRentalId.isNullOrBlank()
                val canFinish = runningRentalIsActive
                val fourthMetricTitle = if (details.periodEnd.isNullOrBlank()) "ОПЛАЧ. ДО" else "ЗАВЕРШЕНА"
                val fourthMetricValue = when {
                    details.periodEnd.isNullOrBlank() && !details.paidUntil.isNullOrBlank() -> formatLongRuDate(details.paidUntil)
                    !details.periodEnd.isNullOrBlank() -> formatLongRuDate(details.periodEnd)
                    else -> "—"
                }
                val journalRows = details.journalEntries
                val paidMetricText = if (isInStockState) "—" else "+${money(details.totalPaidRub)}"
                val debtMetricText = if (isInStockState) "—" else money(details.debtRub)
                val adjustmentMetricText = if (isInStockState) "—" else money(details.totalAdjustmentRub)
                val fourthMetricText = if (isInStockState) "—" else fourthMetricValue
                val paidMetricColor = if (isInStockState) mainTextColor else AppDesign.Success
                val debtMetricColor = if (isInStockState) mainTextColor else AppDesign.Danger
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
                        color = AppDesign.SurfaceBackground,
                        border = androidx.compose.foundation.BorderStroke(AppDesign.HairlineStroke, mainTextColor)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 19.dp, top = 21.dp, end = 19.dp, bottom = 14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .background(AppDesign.Placeholder, RoundedCornerShape(18.dp))
                                        .border(AppDesign.EmphasisStroke, rentalPreviewBorderColor(details), RoundedCornerShape(18.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.DirectionsBike,
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
                                    Text(details.bikeModel, color = mainTextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("${formatRubAmount(details.weeklyRateRub)} ₽/нед", color = subtleTextColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    rentalPreviewStatusPill(details)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    RentalLinkIconButton(
                                        iconRes = R.drawable.youtube_link,
                                        contentDescription = "youtube",
                                        iconWidth = 21.dp,
                                        iconHeight = 16.dp,
                                        enabled = !details.videoUrl.isNullOrBlank(),
                                        onClick = {
                                            val url = details.videoUrl?.trim().orEmpty()
                                            if (url.isNotEmpty()) uriHandler.openUri(url)
                                        }
                                    )
                                    RentalLinkIconButton(
                                        iconRes = R.drawable.dogovor_link,
                                        contentDescription = "contract",
                                        iconWidth = 14.dp,
                                        iconHeight = 18.dp,
                                        enabled = !details.contractUrl.isNullOrBlank(),
                                        onClick = {
                                            val url = details.contractUrl?.trim().orEmpty()
                                            if (url.isNotEmpty()) uriHandler.openUri(url)
                                        }
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = dividerColor, thickness = AppDesign.HairlineStroke)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                MetricStack("ОПЛАЧЕНО", paidMetricText, paidMetricColor, titleColor = subtleTextColor, modifier = Modifier.weight(1f))
                                MetricStack("ДОЛГ", debtMetricText, debtMetricColor, titleColor = subtleTextColor, modifier = Modifier.weight(1f))
                                MetricStack("КОРРЕКТ.", adjustmentMetricText, mainTextColor, titleColor = subtleTextColor, modifier = Modifier.weight(1f))
                                MetricStack(fourthMetricTitle, fourthMetricText, mainTextColor, titleColor = subtleTextColor, modifier = Modifier.weight(1f))
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = dividerColor, thickness = AppDesign.HairlineStroke)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 19.dp)
                                    .padding(top = 9.dp, bottom = 8.dp)
                                    .height(67.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CompactCredentialField(
                                        title = "ЛОГИН",
                                        value = editableLogin,
                                        editable = isInStockState,
                                        onValueChange = { editableLogin = it },
                                        placeholder = "—"
                                    )
                                    CompactCredentialField(
                                        title = "ПАРОЛЬ",
                                        value = editablePassword,
                                        editable = isInStockState,
                                        onValueChange = { editablePassword = it },
                                        placeholder = "—"
                                    )
                                }
                                Row(
                                    modifier = Modifier.width(165.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isInStockState) {
                                        RentalCredentialActionButton(
                                            title = "Сгенерировать",
                                            backgroundColor = credentialButtonColor,
                                            onClick = {
                                                editableLogin = "user${(100000..999999).random()}"
                                                editablePassword = buildString {
                                                    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
                                                    repeat(12) { append(alphabet.random()) }
                                                }
                                            }
                                        )
                                    } else {
                                        Spacer(Modifier.size(width = 110.dp, height = 47.dp))
                                    }

                                    RentalCredentialCopyButton(
                                        backgroundColor = credentialButtonColor,
                                        onClick = {
                                            val login = editableLogin.ifBlank { details.clientLogin.orEmpty() }.ifBlank { "—" }
                                            val password = editablePassword.ifBlank { details.clientPassword.orEmpty() }.ifBlank { "—" }
                                            clipboardManager.setText(AnnotatedString("Логин: $login\nПароль: $password"))
                                        }
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = dividerColor, thickness = AppDesign.HairlineStroke)
                            if (isInStockState) {
                                RentalStartClientSelectorRow(
                                    selectedClientName = selectedStartClient?.fullName,
                                    borderColor = mainTextColor,
                                    onClick = { isClientPickerPresented = true },
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(68.dp)
                                        .adminClickable(shape = RoundedCornerShape(12.dp), enabled = details.clientId.isNotBlank()) { onOpenClient() }
                                        .padding(horizontal = 19.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.testTag("admin_rental_details_renter_row"),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            "АРЕНДАТОР",
                                            color = subtleTextColor,
                                            fontSize = 10.sp,
                                            letterSpacing = 0.6.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            details.clientName.ifBlank { "Клиент" },
                                            color = mainTextColor,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = subtleTextColor, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    if (!isInStockState) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "ЖУРНАЛ",
                            color = AppDesign.SubtleText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.88.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(journalRows.size) { index ->
                                val row = journalRows[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(35.dp)
                                        .background(AppDesign.SurfaceBackground, RoundedCornerShape(10.dp))
                                        .border(AppDesign.HairlineStroke, AppDesign.LightStroke, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 15.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                    Text(
                                        text = ledgerOperationLabel(row.type, row.paymentMethod),
                                        color = AppDesign.SubtleText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(128.dp)
                                    )
                                    Text(
                                        text = signedRub(row.amountRub),
                                        color = when {
                                            row.amountRub > 0 -> AppDesign.Success
                                            row.amountRub < 0 -> AppDesign.Danger
                                            else -> AppDesign.TitleText
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = formatJournalDateLabel(row.createdAt),
                                        color = AppDesign.SubtleText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onAdjust(details) },
                            enabled = canAdjust,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (canAdjust) mainTextColor else mainTextColor.copy(alpha = 0.35f)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = AppDesign.SurfaceBackground,
                                contentColor = mainTextColor,
                                disabledContainerColor = AppDesign.SurfaceBackground,
                                disabledContentColor = mainTextColor.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("admin_rental_details_secondary_action")
                        ) {
                            Text("+ Корректировка", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        if (runningRentalIsActive) {
                            Button(
                                onClick = { onFinish(details) },
                                enabled = canFinish,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppDesign.Danger,
                                    contentColor = AppDesign.SurfaceBackground
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("admin_rental_details_primary_action")
                            ) {
                                Text("Завершить", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (!isInStockState) return@Button
                                    val client = selectedStartClient
                                    val login = editableLogin.trim()
                                    val password = editablePassword.trim()
                                    when {
                                        client == null -> localMessage = "Выберите клиента"
                                        login.isEmpty() && password.isEmpty() -> localMessage = "Заполните логин и пароль"
                                        login.isEmpty() -> localMessage = "Заполните логин"
                                        password.isEmpty() -> localMessage = "Сгенерируйте новый пароль"
                                        else -> {
                                            localMessage = null
                                            onStartRental(
                                                details.rentalId,
                                                client.clientId,
                                                login,
                                                password,
                                                LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                                            )
                                        }
                                    }
                                },
                                enabled = isInStockState,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppDesign.Success,
                                    contentColor = AppDesign.SurfaceBackground,
                                    disabledContainerColor = AppDesign.Success.copy(alpha = 0.65f),
                                    disabledContentColor = AppDesign.SurfaceBackground
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("admin_rental_details_primary_action")
                            ) {
                                Text("Начать!", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        AppStackVisibility(
            visible = isClientPickerPresented,
            modifier = Modifier.fillMaxSize().zIndex(35f)
        ) {
            RentalClientPickerSheet(
                clients = availableStartClients,
                selectedId = selectedStartClientId,
                onSelect = {
                    selectedStartClientId = it
                    localMessage = null
                },
                onClose = { isClientPickerPresented = false },
                onConfirm = {
                    isClientPickerPresented = false
                    val suggestedLogin = availableStartClients.firstOrNull { it.clientId == selectedStartClientId }?.clientLogin.orEmpty()
                    if (editableLogin.trim().isEmpty() && suggestedLogin.isNotBlank()) {
                        editableLogin = suggestedLogin
                    }
                },
                listTag = "admin_rental_details_client_picker_list"
            )
        }

        AppToast(
            message = localMessage,
            modifier = Modifier.align(Alignment.BottomCenter),
            bottomPadding = 96
        )
    }
}

internal data class RentalJournalPreviewRow(
    val type: String,
    val amountRub: Int,
    val date: String
)

@Composable
private fun RentalCredentialActionButton(
    title: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = Modifier
            .size(width = 110.dp, height = 47.dp)
            .adminClickable(shape = shape, onClick = onClick)
            .background(backgroundColor, shape)
            .testTag("admin_rental_details_generate_credentials"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = AppDesign.SurfaceBackground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun RentalCredentialCopyButton(
    backgroundColor: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = Modifier
            .size(47.dp)
            .adminClickable(shape = shape, onClick = onClick)
            .background(backgroundColor, shape)
            .testTag("admin_rental_details_copy_credentials"),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.copy_icon),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
internal fun CompactCredentialField(
    title: String,
    value: String,
    editable: Boolean,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    val mainTextColor = AppDesign.DarkControl
    val subtleTextColor = AppDesign.PaleSky
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            color = subtleTextColor,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.Bold
        )
        if (editable) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = mainTextColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(AppDesign.Accent),
                modifier = Modifier.width(150.dp),
                decorationBox = { inner ->
                    if (value.isBlank()) {
                        Text(placeholder, color = subtleTextColor, fontSize = 10.sp)
                    }
                    inner()
                }
            )
        } else {
            Text(
                text = value.ifBlank { placeholder },
                color = mainTextColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(150.dp)
            )
        }
    }
}

@Composable
internal fun RentalStartClientSelectorRow(
    selectedClientName: String?,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasSelectedClient = !selectedClientName.isNullOrBlank()
    val shape = RoundedCornerShape(12.84.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .adminClickable(shape = shape, onClick = onClick)
            .background(AppDesign.SurfaceBackground, shape)
            .border(AppDesign.HairlineStroke, borderColor, shape)
            .testTag("admin_rental_details_client_selector"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    if (hasSelectedClient) borderColor else AppDesign.SheetHandle,
                    RoundedCornerShape(4.dp)
                )
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 15.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "КЛИЕНТ",
                color = AppDesign.SubtleText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = selectedClientName?.takeIf { it.isNotBlank() } ?: "выбрать клиента",
                color = if (hasSelectedClient) AppDesign.TitleText else AppDesign.Ghost,
                fontSize = 13.sp,
                fontWeight = if (hasSelectedClient) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .padding(end = 15.dp)
                .size(28.dp)
                .background(AppDesign.LightStroke, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = AppDesign.SubtleText,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
internal fun RentalLinkIconButton(
    iconRes: Int,
    contentDescription: String,
    iconWidth: androidx.compose.ui.unit.Dp = 18.dp,
    iconHeight: androidx.compose.ui.unit.Dp = 18.dp,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppDesign.DarkControl,
            contentColor = AppDesign.SurfaceBackground,
            disabledContainerColor = AppDesign.DarkControl.copy(alpha = 0.25f),
            disabledContentColor = AppDesign.SurfaceBackground.copy(alpha = 0.35f)
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(47.dp)
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(width = iconWidth, height = iconHeight),
            contentScale = ContentScale.Fit,
            alpha = if (enabled) 1f else 0.35f
        )
    }
}

@Composable
internal fun rentalPreviewStatusPill(details: AdminRentalPreview) {
    if (rentalPreviewIsInStock(details)) return
    val isClosed = !rentalPreviewIsRunning(details)
    val title = when {
        isClosed -> "Завершённая"
        else -> "Активная"
    }
    val color = when {
        isClosed -> AppDesign.DarkControl
        else -> AppDesign.PaidGreen
    }
    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = title,
            color = AppDesign.SurfaceBackground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

internal fun rentalPreviewBorderColor(details: AdminRentalPreview): Color {
    if (!details.periodEnd.isNullOrBlank()) return AppDesign.PaleSky
    return when (normalizedPipelineStatus(details.rentalPipelineStatus)) {
        "in_stock", "mine" -> AppDesign.IdlePurple
        "soon_return" -> AppDesign.WarningYellow
        "long_term" -> AppDesign.PaidGreen
        else -> if (details.rentalIsActive) AppDesign.PaidGreen else AppDesign.IdlePurple
    }
}

internal fun rentalSourceTitle(raw: String): String {
    return when (raw) {
        "lifecycle" -> "Lifecycle"
        "active_client_rental" -> "Активная client_rental"
        "closed_client_rental" -> "Завершенная client_rental"
        else -> raw
    }
}

@Composable
internal fun MetricStack(
    title: String,
    value: String,
    valueColor: Color,
    titleColor: Color = AppDesign.SubtleText,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title.uppercase(),
            color = titleColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.36.sp,
            maxLines = 1
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ClientBikeAvatar(
    avatarUrl: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    borderColor: Color? = AppDesign.Success,
    borderWidth: Dp = AppDesign.EmphasisStroke
) {
    val shape = RoundedCornerShape(cornerRadius)
    val normalizedAvatarUrl = avatarUrl
        .trim()
        .takeIf { it.isNotEmpty() && !it.startsWith("data:image", ignoreCase = true) }

    Box(
        modifier = modifier
            .background(AppDesign.Placeholder, shape)
            .drawWithContent {
                val strokeWidth = AppDesign.ThinStroke.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                val corner = cornerRadius.toPx()
                drawLine(
                    color = AppDesign.PlaceholderStroke.copy(alpha = 0.45f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = AppDesign.PlaceholderStroke.copy(alpha = 0.45f),
                    start = Offset(size.width, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = strokeWidth
                )
                drawRoundRect(
                    color = AppDesign.PlaceholderStroke,
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = strokeWidth, pathEffect = dash)
                )
                drawContent()
            }
            .then(
                if (borderColor != null) {
                    Modifier.border(borderWidth, borderColor, shape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (normalizedAvatarUrl != null) {
            AsyncImage(
                model = normalizedAvatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
            )
        }
    }
}

@Composable
internal fun AdminDetailsReadonlyField(label: String, value: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(AppDesign.SurfaceBackground, RoundedCornerShape(12.84.dp))
            .border(AppDesign.HairlineStroke, AppDesign.Accent, RoundedCornerShape(12.84.dp))
            .padding(horizontal = 19.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label.uppercase(),
                color = AppDesign.SubtleText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.66.sp,
                maxLines = 1
            )
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
internal fun AdminBikeCatalogRow(
    bike: AdminBikeResponse,
    runtime: BikeCatalogRuntimeSnapshot,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(77.dp)
            .adminClickable(shape = AdminCatalogRowShape, onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(59.dp)
                .background(AppDesign.Placeholder, AdminCatalogAvatarShape)
                .border(
                    width = AppDesign.EmphasisStroke,
                    color = runtime.borderColor,
                    shape = AdminCatalogAvatarShape
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.DirectionsBike,
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
                color = AppDesign.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = runtime.subtitle,
                color = AppDesign.PaleSky,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "${formatRubAmount(bike.weeklyRateRub)} ₽/нед",
            color = AppDesign.DarkControl,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppDesign.Chevron,
            modifier = Modifier.size(18.dp)
        )
    }
}


@Composable
internal fun AdminRentCard(
    item: AdminClientSummaryResponse,
    isFirst: Boolean = false,
    onDetails: () -> Unit,
    onSetLongTerm: () -> Unit,
    onSetSoonReturn: () -> Unit,
    onSetMine: () -> Unit
) {
    val displayName = if (item.rentalIsActive) item.fullName else "Клиент не выбран"
    val status = rentStatus(item)
    val avatarTag = if (isFirst) "admin_rent_card_avatar_first" else "admin_rent_card_avatar_${item.rentalId ?: item.clientId}"
    var isPipelineMenuOpen by remember(item.rentalId, item.clientId) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(77.dp)
            .adminClickable(shape = AdminCatalogRowShape) { onDetails() }
            .padding(horizontal = 9.dp)
            .testTag(if (isFirst) "admin_rent_card_first" else "admin_rent_card_${item.rentalId ?: item.clientId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(59.dp)
                    .adminClickable(shape = AdminCatalogAvatarShape) { isPipelineMenuOpen = true }
                    .testTag(avatarTag)
                    .background(AppDesign.Placeholder, AdminCatalogAvatarShape)
                    .border(
                        width = AppDesign.EmphasisStroke,
                        color = avatarBorderColor(item),
                        shape = AdminCatalogAvatarShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.DirectionsBike,
                    contentDescription = null,
                    tint = AppDesign.IconSoft,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp)
                )
            }
            if (isPipelineMenuOpen) {
                val normalizedPipelineStatus = item.rentalPipelineStatus.orEmpty().trim().lowercase()
                val isLongTermSelected = item.rentalIsActive && normalizedPipelineStatus != "soon_return"
                val isSoonReturnSelected = item.rentalIsActive && normalizedPipelineStatus == "soon_return"
                val isMineSelected = !item.rentalIsActive

                androidx.compose.material3.DropdownMenu(
                    expanded = true,
                    onDismissRequest = { isPipelineMenuOpen = false }
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Долгосрочная аренда") },
                        modifier = Modifier.testTag("admin_pipeline_mode_long_term"),
                        onClick = {
                            isPipelineMenuOpen = false
                            onSetLongTerm()
                        },
                        enabled = item.rentalIsActive && !isLongTermSelected
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Вернут в течении недели") },
                        modifier = Modifier.testTag("admin_pipeline_mode_soon_return"),
                        onClick = {
                            isPipelineMenuOpen = false
                            onSetSoonReturn()
                        },
                        enabled = item.rentalIsActive && !isSoonReturnSelected
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Велосипед у меня") },
                        modifier = Modifier.testTag("admin_pipeline_mode_mine"),
                        onClick = {
                            isPipelineMenuOpen = false
                            onSetMine()
                        },
                        enabled = item.rentalIsActive && !isMineSelected
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(
            modifier = Modifier.width(136.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = displayName,
                color = AppDesign.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.bikeModel,
                color = AppDesign.TextPrimaryMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Корректировка: ${money(item.totalAdjustmentRub)}",
                color = AppDesign.TextPrimaryMuted,
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
                color = AppDesign.SurfaceBackground.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = status.value,
                color = AppDesign.SurfaceBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun AdminRentalDebtAdjustmentDialog(
    title: String,
    visible: Boolean = true,
    onDismiss: () -> Unit,
    onApply: (amountRub: Int, sign: String, comment: String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var selectedSign by remember { mutableStateOf("minus") }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    val overlayInteraction = remember { MutableInteractionSource() }
    val mainTextColor = AppDesign.DarkControl
    val density = LocalDensity.current
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 0.08f else 0f,
        animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
        label = "adjustmentScrimAlpha"
    )

    LaunchedEffect(toastMessage) {
        if (!toastMessage.isNullOrBlank()) {
            delay(2200)
            toastMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(40f)
            .background(AppDesign.Black.copy(alpha = scrimAlpha))
            .testTag("admin_rental_adjustment_sheet")
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
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                slideInVertically(initialOffsetY = { it }, animationSpec = tween(220, easing = LinearOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(180)) +
                slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val keyboardBottomDp = with(density) { WindowInsets.ime.getBottom(this).toDp() }
                val navigationBottomDp = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
                val keyboardVisible = keyboardBottomDp > 0.dp
                val targetSheetBottomGap = if (keyboardVisible) keyboardBottomDp else 0.dp
                val sheetBottomGap by animateDpAsState(
                    targetValue = targetSheetBottomGap,
                    animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing),
                    label = "adjustmentKeyboardLift"
                )
                val sheetBottomPadding = if (keyboardVisible) 24.dp else 24.dp + navigationBottomDp
                val sheetHeight = 312.dp + if (keyboardVisible) 0.dp else navigationBottomDp

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = sheetBottomGap),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sheetHeight)
                            .drawWithContent {
                                drawContent()
                                drawLine(
                                    color = mainTextColor.copy(alpha = 0.2f),
                                    start = Offset.Zero,
                                    end = Offset(size.width, 0f),
                                    strokeWidth = AppDesign.HairlineStroke.toPx()
                                )
                            },
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = AppDesign.SurfaceBackground,
                        shadowElevation = 12.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = 14.dp)
                                    .width(40.dp)
                                    .height(4.dp)
                                    .background(AppDesign.SheetHandleAlt, RoundedCornerShape(999.dp))
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 23.dp)
                                    .padding(top = 12.dp, bottom = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title,
                                    color = mainTextColor,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Spacer(Modifier.width(12.dp).weight(1f))
                                Text(
                                    text = "Закрыть ✕",
                                    color = AppDesign.SubtleText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .adminClickable(shape = RoundedCornerShape(999.dp), onClick = onDismiss)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 23.dp)
                                    .padding(bottom = 14.dp)
                                    .height(54.dp)
                                    .background(AppDesign.SegmentBackground, RoundedCornerShape(16.dp))
                            ) {
                                AdjustmentSegmentButton(
                                    title = "– Уменьшить",
                                    selected = selectedSign == "minus",
                                    onClick = { selectedSign = "minus" },
                                    modifier = Modifier.weight(1f)
                                )
                                AdjustmentSegmentButton(
                                    title = "+ Увеличить",
                                    selected = selectedSign == "plus",
                                    onClick = { selectedSign = "plus" },
                                    modifier = Modifier.weight(1f)
                                )
                                AdjustmentSegmentButton(
                                    title = "+ Наличные",
                                    selected = selectedSign == "cash",
                                    onClick = { selectedSign = "cash" },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("debt_adjustment_cash_segment")
                                )
                            }
                            AdminSheetInputField(
                                label = "СУММА, ₽",
                                placeholder = "введите...",
                                value = amountText,
                                onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                                testTag = "debt_adjustment_amount_input",
                                keyboardType = KeyboardType.Number,
                                borderColor = mainTextColor,
                                autoFocus = true,
                                modifier = Modifier
                                    .padding(horizontal = 23.dp)
                                    .padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = {
                                    val amount = amountText.toIntOrNull() ?: 0
                                    if (amount <= 0) {
                                        toastMessage = "Введите положительную сумму"
                                        return@Button
                                    }
                                    onApply(amount, selectedSign, "")
                                },
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = mainTextColor,
                                    contentColor = AppDesign.SurfaceBackground
                                ),
                                modifier = Modifier
                                    .padding(horizontal = 23.dp)
                                    .padding(bottom = sheetBottomPadding)
                                    .fillMaxWidth()
                                    .height(63.dp)
                                    .testTag("debt_adjustment_apply_button")
                            ) {
                                Text(
                                    if (selectedSign == "cash") "Добавить наличные" else "Применить",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.28.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        AppToast(
            message = toastMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(2f),
            bottomPadding = 96
        )
    }
}

@Composable
private fun AdjustmentSegmentButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mainTextColor = AppDesign.DarkControl
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = modifier
            .padding(4.dp)
            .fillMaxHeight()
            .adminClickable(shape = shape, onClick = onClick)
            .background(if (selected) AppDesign.SurfaceBackground else AppDesign.Transparent, shape)
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) mainTextColor else AppDesign.Transparent,
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (selected) mainTextColor else AppDesign.SubtleText,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

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
                                border = androidx.compose.foundation.BorderStroke(1.dp, AppDesign.Accent)
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
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp)
            .testTag(if (isFirst) "admin_client_row_first" else "admin_client_row_${item.clientId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable(enabled = isCallEnabled) {
                    telUri?.let(uriHandler::openUri)
                }
                .alpha(if (isCallEnabled) 1f else 0.45f)
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
    val bikeModel: String,
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
                clientLogin = summary.clientLogin,
                clientPassword = null,
                videoUrl = null,
                contractUrl = null,
                paidUntil = summary.paidUntil,
                rentalPipelineStatus = summary.rentalPipelineStatus,
                rentalIsActive = summary.rentalIsActive,
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
                clientLogin = null,
                clientPassword = null,
                videoUrl = rental.videoUrl,
                contractUrl = rental.contractUrl,
                paidUntil = null,
                rentalPipelineStatus = null,
                rentalIsActive = rental.periodEnd.isNullOrBlank(),
                comment = rental.comment,
                sourceLabel = if (rental.periodEnd.isNullOrBlank()) "active_client_rental" else "closed_client_rental"
            )
        }
    }
}

@Composable
internal fun AdminClientDetailsScreen(
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
                        Image(
                            painter = painterResource(R.drawable.refaktoring),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            contentScale = ContentScale.Fit
                        )
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
                                    HorizontalDivider(color = Color(0xFFEAEAF0))
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

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    MetricStack("ОПЛАЧЕНО", "+${money(d.totalPaidRub)}", AppDesign.Success)
                                    Spacer(Modifier.weight(1f))
                                    MetricStack("ДОЛГ", money(d.debtRub), if (d.debtRub > 0) AppDesign.Danger else AppDesign.TitleText)
                                    Spacer(Modifier.weight(1f))
                                    MetricStack("КОРРЕКТ.", money(d.totalAdjustmentRub), AppDesign.TitleText)
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
                            letterSpacing = 0.88.sp,
                            modifier = Modifier.testTag("admin_client_profile_section")
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
internal fun AdminRentalDetailsScreenAndroid(
    details: AdminRentalPreview?,
    isLoading: Boolean,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onOpenClient: () -> Unit,
    onAdjust: (AdminRentalPreview) -> Unit,
    onFinish: (AdminRentalPreview) -> Unit,
    onDelete: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var editableLogin by remember(details?.rentalId) { mutableStateOf(details?.clientLogin.orEmpty()) }
    var editablePassword by remember(details?.rentalId) { mutableStateOf(details?.clientPassword.orEmpty()) }

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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (details?.rentalIsActive == true && details.sourceLabel != "lifecycle") {
                        OutlinedButton(
                            onClick = onEdit,
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppDesign.Accent),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = AppDesign.Accent
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
            }

            if (isLoading || details == null) {
                Box(
                    modifier = Modifier.fillMaxSize().testTag("admin_rental_details_loading"),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppDesign.Accent)
                }
            } else {
                val isInStockState = details.sourceLabel == "lifecycle" &&
                    (details.rentalPipelineStatus == "in_stock" || details.rentalPipelineStatus == "mine" || !details.rentalIsActive)
                val canAdjust = !isInStockState
                val canFinish = details.rentalIsActive && !isInStockState
                val fourthMetricTitle = if (details.periodEnd.isNullOrBlank()) "ОПЛАЧ. ДО" else "ЗАВЕРШЕНА"
                val fourthMetricValue = when {
                    details.periodEnd.isNullOrBlank() && !details.paidUntil.isNullOrBlank() -> formatLongRuDate(details.paidUntil)
                    !details.periodEnd.isNullOrBlank() -> formatLongRuDate(details.periodEnd)
                    else -> "—"
                }
                val journalRows = remember(details.rentalId, details.periodStart, details.periodEnd, details.totalPaidRub, details.debtRub, details.totalAdjustmentRub) {
                    listOf(
                        RentalJournalPreviewRow("payment", details.totalPaidRub, formatShortRuDate(details.periodStart)),
                        RentalJournalPreviewRow("debt", -details.debtRub, formatShortRuDate(details.periodEnd ?: details.periodStart)),
                        RentalJournalPreviewRow("adjust", details.totalAdjustmentRub, formatShortRuDate(details.periodEnd ?: details.periodStart))
                    )
                }
                val paidMetricText = if (isInStockState) "—" else "+${money(details.totalPaidRub)}"
                val debtMetricText = if (isInStockState) "—" else money(details.debtRub)
                val adjustmentMetricText = if (isInStockState) "—" else money(details.totalAdjustmentRub)
                val fourthMetricText = if (isInStockState) "—" else fourthMetricValue
                val paidMetricColor = if (isInStockState) AppDesign.TitleText else AppDesign.Success
                val debtMetricColor = if (isInStockState) AppDesign.TitleText else if (details.debtRub > 0) AppDesign.Danger else AppDesign.TitleText

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
                        color = Color.White,
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppDesign.Accent)
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

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = Color(0xFFEAEAF0))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                MetricStack("ОПЛАЧЕНО", paidMetricText, paidMetricColor)
                                Spacer(Modifier.weight(1f))
                                MetricStack("ДОЛГ", debtMetricText, debtMetricColor)
                                Spacer(Modifier.weight(1f))
                                MetricStack("КОРРЕКТ.", adjustmentMetricText, AppDesign.TitleText)
                                Spacer(Modifier.weight(1f))
                                MetricStack(fourthMetricTitle, fourthMetricText, AppDesign.TitleText)
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = Color(0xFFEAEAF0))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(67.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.padding(start = 19.dp),
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
                                Spacer(Modifier.weight(1f))
                                Row(
                                    modifier = Modifier.padding(end = 19.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isInStockState) {
                                        Button(
                                            onClick = {
                                                editableLogin = "user${(100000..999999).random()}"
                                                editablePassword = buildString {
                                                    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
                                                    repeat(12) { append(alphabet.random()) }
                                                }
                                            },
                                            shape = RoundedCornerShape(15.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF141718),
                                                contentColor = Color.White
                                            ),
                                            modifier = Modifier.size(width = 110.dp, height = 47.dp)
                                        ) {
                                            Text("Сгенерировать", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Spacer(Modifier.size(width = 110.dp, height = 47.dp))
                                    }

                                    Button(
                                        onClick = {
                                            val login = editableLogin.ifBlank { details.clientLogin.orEmpty() }.ifBlank { "—" }
                                            val password = editablePassword.ifBlank { details.clientPassword.orEmpty() }.ifBlank { "—" }
                                            clipboardManager.setText(AnnotatedString("Логин: $login\nПароль: $password"))
                                        },
                                        shape = RoundedCornerShape(15.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF141718),
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.size(47.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(R.drawable.copy_icon),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = Color(0xFFEAEAF0))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp)
                                    .clickable { onOpenClient() }
                                    .padding(horizontal = 19.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.testTag("admin_rental_details_renter_row"),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "АРЕНДАТОР",
                                        color = AppDesign.SubtleText,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.6.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        details.clientName,
                                        color = AppDesign.TitleText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = AppDesign.SubtleText, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    if (!isInStockState && journalRows.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "ЖУРНАЛ",
                            color = AppDesign.SubtleText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.88.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            journalRows.forEachIndexed { _, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(35.dp)
                                        .background(Color.White, RoundedCornerShape(10.dp))
                                        .border(1.dp, Color(0xFFEAEAF0), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 15.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                    Text(
                                        text = row.type.uppercase(),
                                        color = AppDesign.SubtleText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(90.dp)
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
                                        text = row.date,
                                        color = AppDesign.SubtleText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onAdjust(details) },
                            enabled = canAdjust,
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
                            Text("+ Корректировка", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onFinish(details) },
                            enabled = canFinish,
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
                            Text("Завершить", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

internal data class RentalJournalPreviewRow(
    val type: String,
    val amountRub: Int,
    val date: String
)

@Composable
internal fun CompactCredentialField(
    title: String,
    value: String,
    editable: Boolean,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            color = AppDesign.SubtleText,
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
                    color = AppDesign.TitleText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(AppDesign.Accent),
                modifier = Modifier.width(150.dp),
                decorationBox = { inner ->
                    if (value.isBlank()) {
                        Text(placeholder, color = AppDesign.SubtleText, fontSize = 10.sp)
                    }
                    inner()
                }
            )
        } else {
            Text(
                text = value.ifBlank { placeholder },
                color = AppDesign.TitleText,
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
            containerColor = Color(0xFF141718),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF141718).copy(alpha = 0.25f),
            disabledContentColor = Color.White.copy(alpha = 0.35f)
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

internal fun rentalPreviewBorderColor(details: AdminRentalPreview): Color {
    if (!details.periodEnd.isNullOrBlank()) return Color(0xFF6B7280)
    return if (details.sourceLabel == "lifecycle") Color(0xFFCB30E0) else Color(0xFF34C759)
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
internal fun MetricStack(title: String, value: String, valueColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title.uppercase(),
            color = AppDesign.SubtleText,
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
internal fun AdminDetailsReadonlyField(label: String, value: String?) {
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
internal fun AdminBikeCatalogRow(
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
    val normalizedPipelineStatus = item.rentalPipelineStatus.orEmpty().trim().lowercase()
    val isLongTermSelected = item.rentalIsActive && normalizedPipelineStatus != "soon_return"
    val isSoonReturnSelected = item.rentalIsActive && normalizedPipelineStatus == "soon_return"
    val isMineSelected = !item.rentalIsActive

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(77.dp)
            .clickable { onDetails() }
            .padding(horizontal = 9.dp)
            .testTag(if (isFirst) "admin_rent_card_first" else "admin_rent_card_${item.rentalId ?: item.clientId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(59.dp)
                    .clickable { isPipelineMenuOpen = true }
                    .testTag(avatarTag)
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
            androidx.compose.material3.DropdownMenu(
                expanded = isPipelineMenuOpen,
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

@Composable
internal fun AdminRentalDebtAdjustmentDialog(
    title: String,
    onDismiss: () -> Unit,
    onApply: (amountRub: Int, sign: String, comment: String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }
    var selectedSign by remember { mutableStateOf("+") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = AppDesign.TitleText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { selectedSign = "+" },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedSign == "+") AppDesign.Accent.copy(alpha = 0.08f) else Color.White,
                            contentColor = AppDesign.Accent
                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text("+", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { selectedSign = "-" },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedSign == "-") AppDesign.Accent.copy(alpha = 0.08f) else Color.White,
                            contentColor = AppDesign.Accent
                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text("-", fontWeight = FontWeight.Bold) }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                    singleLine = true,
                    label = { Text("Сумма, ₽") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text("Комментарий (необязательно)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val amount = amountText.toIntOrNull() ?: 0
            OutlinedButton(
                onClick = { onApply(amount, selectedSign, commentText) },
                enabled = amount > 0
            ) {
                Text("Применить")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

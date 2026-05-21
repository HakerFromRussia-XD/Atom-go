package com.atomgo.android.presentation.logic

import com.atomgo.android.presentation.model.AdminBikeFilter
import com.atomgo.android.presentation.model.AdminBikeFilterCounters
import com.atomgo.android.presentation.model.AdminClientFilter
import com.atomgo.android.presentation.model.AdminClientFilterCounters
import com.atomgo.android.presentation.model.AdminFilterCounters
import com.atomgo.android.presentation.model.AdminRentFilter
import com.atomgo.android.presentation.model.normalizedPipelineStatus
import com.atomgo.shared.api.AdminBikeResponse
import com.atomgo.shared.api.AdminClientSummaryResponse

internal data class AdminCatalogDerivedData(
    val filteredRents: List<AdminClientSummaryResponse>,
    val filteredClients: List<AdminClientSummaryResponse>,
    val filteredBikes: List<AdminBikeResponse>,
    val rentCounters: AdminFilterCounters,
    val clientCounters: AdminClientFilterCounters,
    val bikeCounters: AdminBikeFilterCounters
)

internal object AdminCatalogFilterEngine {
    internal fun derive(
        rents: List<AdminClientSummaryResponse>,
        rentsSearch: String,
        rentsFilter: AdminRentFilter,
        clientsCatalog: List<AdminClientSummaryResponse>,
        clientsSearch: String,
        clientsFilter: AdminClientFilter,
        bikesCatalog: List<AdminBikeResponse>,
        bikesSearch: String,
        bikesFilter: AdminBikeFilter
    ): AdminCatalogDerivedData {
        val normalizedRentQuery = rentsSearch.trim()
        val searchedRents = rents.filter { item ->
            normalizedRentQuery.isEmpty() ||
                item.fullName.contains(normalizedRentQuery, ignoreCase = true) ||
                item.bikeModel.contains(normalizedRentQuery, ignoreCase = true) ||
                (item.clientLogin ?: "").contains(normalizedRentQuery, ignoreCase = true)
        }
        val filteredRents = searchedRents.filter { item ->
            when (rentsFilter) {
                AdminRentFilter.All -> true
                AdminRentFilter.SoonReturn -> item.rentalIsActive && normalizedPipelineStatus(item.rentalPipelineStatus) == "soon_return"
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

        return AdminCatalogDerivedData(
            filteredRents = filteredRents,
            filteredClients = filteredClients,
            filteredBikes = filteredBikes,
            rentCounters = AdminFilterCounters(
                all = rents.size,
                soonReturn = rents.count {
                    it.rentalIsActive && normalizedPipelineStatus(it.rentalPipelineStatus) == "soon_return"
                },
                debtors = rents.count { it.debtRub > 0 },
                mine = rents.count { !it.rentalIsActive }
            ),
            clientCounters = AdminClientFilterCounters(
                all = clientsCatalog.size,
                debtors = clientsCatalog.count { it.debtRub > 0 },
                active = clientsCatalog.count { it.rentalIsActive }
            ),
            bikeCounters = AdminBikeFilterCounters(
                all = bikesCatalog.size,
                free = bikesCatalog.count { !it.bikeIsInRental },
                rented = bikesCatalog.count { it.bikeIsInRental }
            )
        )
    }
}

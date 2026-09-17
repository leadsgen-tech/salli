package lk.salli.app.features.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import lk.salli.data.db.SalliDatabase
import lk.salli.data.db.entities.FuelPassRecordEntity
import lk.salli.domain.FuelPassRules

data class FillUp(val timestamp: Long, val litresMilli: Long, val stationCode: String?)

data class VehicleCard(
    val vehicle: String,
    val weeklyBalanceMilli: Long,
    val resetsOn: LocalDate?,
    val eligibleToday: Boolean,
    /** Latest day before the reset on which this plate may still fill up. */
    val lastEligibleDay: LocalDate?,
    val fillUps: List<FillUp>,
)

data class FuelPassUiState(
    val vehicles: List<VehicleCard> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class FuelPassViewModel @Inject constructor(
    db: SalliDatabase,
) : ViewModel() {

    private val colombo = ZoneId.of("Asia/Colombo")

    val state: StateFlow<FuelPassUiState> = db.fuelPass().observeAll()
        .map { records ->
            val today = LocalDate.now(colombo)
            FuelPassUiState(
                vehicles = records.groupBy { FuelPassRules.canonicalVehicle(it.vehicle) }
                    .map { (vehicle, list) -> card(vehicle, list, today) }
                    .sortedBy { it.vehicle },
                loading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FuelPassUiState())

    private fun card(vehicle: String, records: List<FuelPassRecordEntity>, today: LocalDate): VehicleCard {
        val sorted = records.sortedByDescending { it.timestamp }
        val latest = sorted.first()
        val resets = latest.resetsOn?.let { Instant.ofEpochMilli(it).atZone(colombo).toLocalDate() }
        // Once the reset date has passed, the quota we last read has been refreshed to a full
        // week we can't see until the next fill-up; -1 tells the UI to say so instead of
        // showing a stale litre count.
        val balanceIsCurrent = resets == null || today.isBefore(resets)
        return VehicleCard(
            vehicle = vehicle,
            weeklyBalanceMilli = if (balanceIsCurrent) latest.weeklyBalanceMilli else -1L,
            resetsOn = resets,
            eligibleToday = FuelPassRules.isEligible(vehicle, today),
            lastEligibleDay = resets?.takeIf { today.isBefore(it) }?.let { FuelPassRules.lastEligibleDayBefore(vehicle, it) },
            fillUps = sorted.take(8).map { FillUp(it.timestamp, it.litresMilli, it.stationCode) },
        )
    }
}

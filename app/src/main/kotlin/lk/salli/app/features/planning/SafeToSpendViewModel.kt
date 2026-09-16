package lk.salli.app.features.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import lk.salli.data.planning.PlanningService
import lk.salli.data.planning.PlanningSnapshot

@HiltViewModel
class SafeToSpendViewModel @Inject constructor(
    planning: PlanningService,
) : ViewModel() {

    val snapshot: StateFlow<PlanningSnapshot?> = planning.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

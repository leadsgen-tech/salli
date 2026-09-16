package lk.salli.app.sms

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import lk.salli.app.R
import lk.salli.design.components.PullRefreshOutcome

/** User-facing copy for the result of a local inbox scan. */
@Composable
fun refreshOutcome(status: RefreshStatus): PullRefreshOutcome? = when (status) {
    RefreshStatus.Idle, RefreshStatus.Running -> null
    is RefreshStatus.Done -> PullRefreshOutcome(
        message = when {
            status.inserted == 0 && status.queued == 0 -> stringResource(R.string.activity_refresh_up_to_date)
            status.queued > 0 -> stringResource(R.string.activity_refresh_new_and_review, status.inserted, status.queued)
            else -> stringResource(R.string.activity_refresh_new_transactions, status.inserted)
        },
    )
    is RefreshStatus.Failed -> PullRefreshOutcome(stringResource(R.string.activity_refresh_failed), failed = true)
}

package lk.salli.design.format

import lk.salli.domain.Money
import lk.salli.domain.money.MoneyFormat as Domain

/**
 * Compatibility shim. The real formatter moved to `:domain`
 * ([lk.salli.domain.money.MoneyFormat]) so the pure-Kotlin modules — and any future KMP target
 * — can reach it too. Every function here forwards; there is exactly one implementation of how
 * a rupee is written.
 *
 * It is an `object` rather than a `typealias` on purpose: `MoneyFormat::format` is used as a
 * *bound* callable reference (see `SplitGroupViewModel.shareText`), and a typealias to an
 * object makes that reference unbound, which changes its arity and breaks the call site.
 *
 * Prefer the `:domain` import in new code; this file exists so the move didn't have to rewrite
 * a dozen screens in the same commit.
 */
object MoneyFormat {

    /** @see lk.salli.domain.money.MoneyFormat.format */
    fun format(money: Money, signed: Boolean = false): String = Domain.format(money, signed)

    /** @see lk.salli.domain.money.MoneyFormat.bare */
    fun formatBare(money: Money): String = Domain.bare(money)
}

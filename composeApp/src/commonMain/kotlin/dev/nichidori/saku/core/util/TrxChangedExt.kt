package dev.nichidori.saku.core.util

import dev.nichidori.saku.core.event.AppEvent
import kotlinx.datetime.YearMonth

fun AppEvent.TrxChanged.affectedMonths(): Set<YearMonth> = when (this) {
    is AppEvent.TrxChanged.Created -> setOf(trx.transactionAt.toYearMonth())
    is AppEvent.TrxChanged.Updated -> setOf(
        before.transactionAt.toYearMonth(),
        after.transactionAt.toYearMonth(),
    )
    is AppEvent.TrxChanged.Deleted -> setOf(trx.transactionAt.toYearMonth())
    is AppEvent.TrxChanged.Copied -> emptySet()
}
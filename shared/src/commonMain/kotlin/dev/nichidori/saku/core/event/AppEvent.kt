package dev.nichidori.saku.core.event

import dev.nichidori.saku.domain.model.Trx

sealed interface AppEvent {
    sealed interface TrxChanged : AppEvent {
        data class Created(val trx: Trx) : TrxChanged
        data class Updated(val before: Trx, val after: Trx) : TrxChanged
        data class Deleted(val trx: Trx) : TrxChanged
        data class Copied(val trxId: String) : TrxChanged
    }

    sealed interface AccountChanged : AppEvent {
        data class Deleted(val accountId: String) : AccountChanged
    }
}
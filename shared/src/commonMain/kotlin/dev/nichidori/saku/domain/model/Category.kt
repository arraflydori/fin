package dev.nichidori.saku.domain.model

import kotlin.time.Instant

data class Category(
    val id: String,
    val name: String,
    val type: TrxType,
    val parent: Category? = null,
    val createdAt: Instant,
    val updatedAt: Instant?,
    val icon: String? = null,
    val sortOrder: Int = 0
)
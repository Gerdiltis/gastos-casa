package com.gastoscasa.domain

import java.time.Instant

data class Member(val userId: String, val name: String)

data class Group(
    val id: String,
    val name: String,
    val inviteCode: String,
    val createdBy: String,
    val members: List<Member>,
) {
    fun memberName(userId: String): String = members.firstOrNull { it.userId == userId }?.name ?: "¿?"
}

data class Category(
    val id: String,
    val groupId: String,
    val name: String,
    val icon: String,
    val color: String,
)

data class Split(val userId: String, val shareCents: Long)

data class Expense(
    val id: String,
    val groupId: String,
    val paidBy: String,
    val description: String,
    val amountCents: Long,
    val categoryId: String?,
    val isSettlement: Boolean,
    val createdBy: String,
    val createdAt: Instant,
    val archivedAt: Instant?,
    val splits: List<Split>,
) {
    val isArchived: Boolean get() = archivedAt != null
    fun shareOf(userId: String): Long = splits.firstOrNull { it.userId == userId }?.shareCents ?: 0L
}

/** Pago sugerido para saldar deudas: [from] paga [amountCents] a [to]. */
data class Transfer(val from: String, val to: String, val amountCents: Long)

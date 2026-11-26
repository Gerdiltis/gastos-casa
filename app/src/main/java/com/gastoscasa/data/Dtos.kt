package com.gastoscasa.data

import com.gastoscasa.domain.Category
import com.gastoscasa.domain.Expense
import com.gastoscasa.domain.Frequency
import com.gastoscasa.domain.RecurringExpense
import com.gastoscasa.domain.ShoppingItem
import com.gastoscasa.domain.Split
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val invite_code: String,
    val created_by: String,
)

@Serializable
data class ProfileRef(val display_name: String)

@Serializable
data class MemberDto(
    val group_id: String,
    val user_id: String,
    val profiles: ProfileRef? = null,
)

@Serializable
data class ProfileDto(val id: String, val display_name: String)

@Serializable
data class CategoryDto(
    val id: String,
    val group_id: String,
    val name: String,
    val icon: String,
    val color: String,
) {
    fun toDomain() = Category(id, group_id, name, icon, color)
}

@Serializable
data class CategoryInsert(val group_id: String, val name: String, val icon: String, val color: String)

@Serializable
data class SplitDto(val user_id: String, val share_cents: Long)

@Serializable
data class ExpenseDto(
    val id: String,
    val group_id: String,
    val paid_by: String,
    val description: String,
    val amount_cents: Long,
    val category_id: String? = null,
    val is_settlement: Boolean = false,
    val created_by: String,
    val created_at: String,
    val archived_at: String? = null,
    val recurring_id: String? = null,
    val occurrence: Int? = null,
    val expense_splits: List<SplitDto> = emptyList(),
) {
    fun toDomain() = Expense(
        id = id, groupId = group_id, paidBy = paid_by, description = description,
        amountCents = amount_cents, categoryId = category_id, isSettlement = is_settlement,
        createdBy = created_by, createdAt = parseInstant(created_at),
        archivedAt = archived_at?.let(::parseInstant),
        splits = expense_splits.map { Split(it.user_id, it.share_cents) },
        recurringId = recurring_id, occurrence = occurrence,
    )
}

@Serializable
data class RecurringDto(
    val id: String,
    val group_id: String,
    val created_by: String,
    val description: String,
    val amount_cents: Long,
    val category_id: String? = null,
    val paid_by: String,
    val participants: List<String>,
    val frequency: String,
    val start_date: String,
    val end_date: String,
    val occurrence: Int,
    val next_date: String? = null,
    val active: Boolean,
) {
    fun toDomain() = RecurringExpense(
        id, group_id, created_by, description, amount_cents, category_id, paid_by, participants,
        Frequency.of(frequency), LocalDate.parse(start_date), LocalDate.parse(end_date), occurrence,
        next_date?.let(LocalDate::parse), active,
    )
}

@Serializable
data class ShoppingItemDto(
    val id: String,
    val group_id: String,
    val name: String,
    val added_by: String,
    val created_at: String,
) {
    fun toDomain() = ShoppingItem(id, group_id, name, added_by, parseInstant(created_at))
}

@Serializable
data class ShoppingItemInsert(val group_id: String, val name: String, val added_by: String)

@Serializable
data class GithubAsset(val name: String, val browser_download_url: String)

@Serializable
data class GithubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

internal fun parseInstant(s: String): Instant = OffsetDateTime.parse(s).toInstant()

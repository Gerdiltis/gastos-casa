package com.gastoscasa.data

import com.gastoscasa.domain.Balances
import com.gastoscasa.domain.Category
import com.gastoscasa.domain.Expense
import com.gastoscasa.domain.Group
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/** Foto completa de los datos del usuario (todos sus grupos). */
data class Overview(
    val me: String,
    val myName: String,
    val groups: List<Group>,
    val expenses: List<Expense>,
    val categories: List<Category>,
) {
    fun expensesOf(groupId: String) = expenses.filter { it.groupId == groupId }
    fun categoryOf(id: String?) = categories.firstOrNull { it.id == id }
    fun groupOf(id: String) = groups.firstOrNull { it.id == id }

    /** Saldo del usuario actual en un grupo (positivo = le deben). */
    fun myBalance(groupId: String): Long {
        val g = groupOf(groupId) ?: return 0
        return Balances.compute(expensesOf(groupId), g.members.map { it.userId })[me] ?: 0
    }
}

class LoadOverview @Inject constructor(
    private val auth: AuthRepository,
    private val repo: GroupRepository,
) {
    suspend operator fun invoke(): Overview = coroutineScope {
        val me = auth.currentUserId ?: error("No autenticado")
        val groups = async { repo.myGroups() }
        val expenses = async { repo.expenses() }
        val categories = async { repo.categories() }
        val name = async { runCatching { auth.myName() }.getOrNull() }
        Overview(me, name.await() ?: "", groups.await(), expenses.await(), categories.await())
    }
}

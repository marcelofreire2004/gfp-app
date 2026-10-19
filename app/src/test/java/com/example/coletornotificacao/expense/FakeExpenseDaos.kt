package com.example.coletornotificacao.expense

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Fake em memória do ExpenseDao para testes de unidade (sem Room). */
class FakeExpenseDao : ExpenseDao {
    private val state = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    private var autoId = 1L

    fun snapshot(): List<ExpenseEntity> = state.value

    override suspend fun insert(expense: ExpenseEntity): Long {
        // Simula OnConflictStrategy.IGNORE no índice único sourceStableId.
        if (state.value.any { it.sourceStableId == expense.sourceStableId }) return -1L
        val id = autoId++
        state.value = state.value + expense.copy(id = id)
        return id
    }

    override suspend fun update(expense: ExpenseEntity) {
        state.value = state.value.map { if (it.id == expense.id) expense else it }
    }

    override suspend fun findById(id: Long): ExpenseEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun findBySource(stableId: String): ExpenseEntity? =
        state.value.firstOrNull { it.sourceStableId == stableId }

    override suspend fun existsForSource(stableId: String): Boolean =
        state.value.any { it.sourceStableId == stableId }

    override fun observeAll(): Flow<List<ExpenseEntity>> =
        state.map { l -> l.sortedByDescending { it.purchaseDate } }

    override fun observeByStatus(status: ExpenseStatus): Flow<List<ExpenseEntity>> =
        state.map { l -> l.filter { it.status == status } }

    override fun observeConfirmedTotalCents(): Flow<Long> =
        state.map { l ->
            l.filter { it.status == ExpenseStatus.CONFIRMED && it.type == ExpenseType.PURCHASE_APPROVED }
                .sumOf { it.amountCents ?: 0L }
        }

    override fun observeReviewCount(): Flow<Int> =
        state.map { l -> l.count { it.status == ExpenseStatus.NEEDS_REVIEW } }

    override fun observeTotalsByCard(): Flow<List<CardTotal>> =
        state.map { l ->
            l.filter { it.status == ExpenseStatus.CONFIRMED && it.type == ExpenseType.PURCHASE_APPROVED }
                .groupBy { it.cardId }
                .map { (cardId, items) -> CardTotal(cardId, items.sumOf { it.amountCents ?: 0L }) }
        }

    override fun observeTotalsByCategory(): Flow<List<CategoryTotal>> =
        state.map { l ->
            l.filter { it.status == ExpenseStatus.CONFIRMED && it.type == ExpenseType.PURCHASE_APPROVED }
                .groupBy { it.category ?: "Sem categoria" }
                .map { (cat, items) -> CategoryTotal(cat, items.sumOf { it.amountCents ?: 0L }) }
        }
}

/** Fake em memória do CardDao. */
class FakeCardDao : CardDao {
    private val state = MutableStateFlow<List<CardEntity>>(emptyList())
    private var autoId = 1L

    override suspend fun insert(card: CardEntity): Long {
        val id = autoId++
        state.value = state.value + card.copy(id = id)
        return id
    }

    override suspend fun update(card: CardEntity) {
        state.value = state.value.map { if (it.id == card.id) card else it }
    }

    override fun observeAll(): Flow<List<CardEntity>> = state
    override suspend fun listActive(): List<CardEntity> = state.value.filter { it.active }
    override suspend fun observeAllOnce(): List<CardEntity> = state.value
    override suspend fun findById(id: Long): CardEntity? = state.value.firstOrNull { it.id == id }
}

/** Fake em memória do MerchantCategoryDao. */
class FakeMerchantCategoryDao : MerchantCategoryDao {
    private val map = mutableMapOf<String, String>()

    override suspend fun upsert(entry: MerchantCategoryEntity): Long {
        map[entry.merchantKey] = entry.categoryName
        return 1L
    }

    override suspend fun categoryFor(merchantKey: String): String? = map[merchantKey]
}

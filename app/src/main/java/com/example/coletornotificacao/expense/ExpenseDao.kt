package com.example.coletornotificacao.expense

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    /**
     * Insere uma despesa. Em conflito no índice único [ExpenseEntity.sourceStableId]
     * (mesma notificação já gerou um lançamento), IGNORA — garante "no máximo um
     * lançamento principal por notificação" (RF01). Retorna o rowId ou -1 se ignorado.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(expense: ExpenseEntity): Long

    /** Atualiza um lançamento existente (correção/confirmação — RF09, sem duplicar). */
    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun findById(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE sourceStableId = :stableId LIMIT 1")
    suspend fun findBySource(stableId: String): ExpenseEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE sourceStableId = :stableId)")
    suspend fun existsForSource(stableId: String): Boolean

    @Query("SELECT * FROM expenses ORDER BY purchaseDate DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE status = :status ORDER BY purchaseDate DESC")
    fun observeByStatus(status: ExpenseStatus): Flow<List<ExpenseEntity>>

    // ---- Resumos (RF08): consideram SOMENTE lançamentos confirmados ----

    @Query("SELECT COALESCE(SUM(amountCents),0) FROM expenses WHERE status = 'CONFIRMED' AND type = 'PURCHASE_APPROVED'")
    fun observeConfirmedTotalCents(): Flow<Long>

    @Query("SELECT COUNT(*) FROM expenses WHERE status = 'NEEDS_REVIEW'")
    fun observeReviewCount(): Flow<Int>

    @Query(
        """
        SELECT cardId AS cardId, COALESCE(SUM(amountCents),0) AS totalCents
        FROM expenses
        WHERE status = 'CONFIRMED' AND type = 'PURCHASE_APPROVED'
        GROUP BY cardId
        """
    )
    fun observeTotalsByCard(): Flow<List<CardTotal>>

    @Query(
        """
        SELECT COALESCE(category, 'Sem categoria') AS category, COALESCE(SUM(amountCents),0) AS totalCents
        FROM expenses
        WHERE status = 'CONFIRMED' AND type = 'PURCHASE_APPROVED'
        GROUP BY category
        """
    )
    fun observeTotalsByCategory(): Flow<List<CategoryTotal>>
}

/** Projeção para "gastos por cartão" (RF08). */
data class CardTotal(val cardId: Long?, val totalCents: Long)

/** Projeção para "gastos por categoria" (RF08). */
data class CategoryTotal(val category: String, val totalCents: Long)

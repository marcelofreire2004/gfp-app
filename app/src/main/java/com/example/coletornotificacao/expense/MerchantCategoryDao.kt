package com.example.coletornotificacao.expense

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MerchantCategoryDao {

    /** Insere/atualiza a associação estabelecimento→categoria (upsert por chave única). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MerchantCategoryEntity): Long

    @Query("SELECT categoryName FROM merchant_categories WHERE merchantKey = :merchantKey LIMIT 1")
    suspend fun categoryFor(merchantKey: String): String?
}

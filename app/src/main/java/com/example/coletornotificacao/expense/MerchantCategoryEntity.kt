package com.example.coletornotificacao.expense

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Associação APRENDIDA entre um estabelecimento e uma categoria (RF07).
 *
 * Quando o usuário confirma/corrige a categoria de uma despesa, a associação
 * (estabelecimento normalizado → categoria) é memorizada aqui e reutilizada
 * para sugerir a categoria de despesas futuras do mesmo estabelecimento.
 *
 * A "aprendizagem" é DETERMINÍSTICA, local e explicável — sem IA.
 */
@Entity(
    tableName = "merchant_categories",
    indices = [Index(value = ["merchantKey"], unique = true)]
)
data class MerchantCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Chave normalizada do estabelecimento (minúsculas, sem espaços extras). */
    @ColumnInfo(name = "merchantKey") val merchantKey: String,

    /** Nome da categoria associada (referência por nome, estável). */
    @ColumnInfo(name = "categoryName") val categoryName: String,

    @ColumnInfo(name = "updatedAt") val updatedAt: Long = System.currentTimeMillis()
)

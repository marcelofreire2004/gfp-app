package com.example.coletornotificacao.expense

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cartão de crédito cadastrado pelo usuário (RF04).
 *
 * NUNCA armazena número completo, CVV, senha ou credenciais bancárias.
 * Guarda apenas dados de identificação e os parâmetros de fechamento/vencimento
 * para uso FUTURO pelo módulo de faturas (não implementado nesta entrega).
 */
@Entity(
    tableName = "cards",
    indices = [Index(value = ["last4"]), Index(value = ["institution"])]
)
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Nome de identificação do cartão (ex.: "Nubank Marcelo"). */
    val name: String,

    /** Instituição financeira (ex.: "Nubank", "C6"). */
    val institution: String,

    /** Apelido livre definido pelo usuário. */
    val nickname: String? = null,

    /** Últimos 4 dígitos (opcional). Nunca o número completo. */
    @ColumnInfo(name = "last4") val last4: String? = null,

    /** Dia de vencimento da fatura (1–31). Para uso futuro em faturas. */
    @ColumnInfo(name = "dueDay") val dueDay: Int? = null,

    /** Quantidade de dias antes do vencimento em que a fatura fecha. */
    @ColumnInfo(name = "closingDaysBeforeDue") val closingDaysBeforeDue: Int? = null,

    /**
     * Palavra-chave usada para casar a origem da notificação (ex.: o pacote do
     * app ou um termo que apareça no texto). Opcional; ajuda a identificar o
     * cartão a partir da notificação (RF04).
     */
    @ColumnInfo(name = "matchKeyword") val matchKeyword: String? = null,

    @ColumnInfo(name = "active", defaultValue = "1") val active: Boolean = true,

    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis()
)

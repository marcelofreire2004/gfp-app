package com.example.coletornotificacao.expense

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lançamento financeiro derivado de uma notificação (RF06).
 *
 * É independente da entidade de notificação: a notificação original NUNCA é
 * apagada e permanece como registro próprio. A despesa referencia a notificação
 * de origem por [sourceStableId] (chave estável), e esse campo é ÚNICO para
 * garantir a regra "no máximo um lançamento principal por notificação" (RF01) —
 * reinterpretar/reabrir o app não cria duplicata.
 *
 * Valor armazenado em CENTAVOS inteiros ([amountCents]) para precisão (RF06).
 * A data da compra ([purchaseDate]) nunca se confunde com data de pagamento de
 * fatura (fora do escopo desta entrega).
 */
@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["sourceStableId"], unique = true),
        Index(value = ["status"]),
        Index(value = ["cardId"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Chave estável da notificação de origem (RF01). Única. */
    @ColumnInfo(name = "sourceStableId") val sourceStableId: String,

    /** Valor em centavos inteiros. Null quando não identificado (→ revisão). */
    @ColumnInfo(name = "amountCents") val amountCents: Long? = null,

    /** Estabelecimento/favorecido. Pode ficar pendente de classificação (RF05). */
    val merchant: String? = null,

    /** Data/hora da operação (epoch millis). Default = horário da notificação. */
    @ColumnInfo(name = "purchaseDate") val purchaseDate: Long,

    /** Instituição financeira de origem (quando identificada). */
    val institution: String? = null,

    /** Cartão associado, quando identificado sem ambiguidade. */
    @ColumnInfo(name = "cardId") val cardId: Long? = null,

    /** Últimos 4 dígitos extraídos da notificação (quando houver). */
    @ColumnInfo(name = "cardLast4") val cardLast4: String? = null,

    /** Número da parcela (quando informado). */
    @ColumnInfo(name = "installmentNumber") val installmentNumber: Int? = null,

    /** Total de parcelas (quando informado). */
    @ColumnInfo(name = "installmentTotal") val installmentTotal: Int? = null,

    /** Natureza da operação (RF03). */
    @ColumnInfo(name = "type") val type: ExpenseType,

    /** Categoria (nome). Pode ficar nula/pendente de classificação. */
    val category: String? = null,

    /** Estado do lançamento (RF05/RF06). */
    @ColumnInfo(name = "status") val status: ExpenseStatus,

    /** true = registrado automaticamente; false = confirmado manualmente. */
    @ColumnInfo(name = "autoRegistered", defaultValue = "0") val autoRegistered: Boolean = false,

    /**
     * Motivos pelos quais o item exige revisão (RF05), separados por "; ".
     * Vazio quando confirmado automaticamente sem ressalvas.
     */
    @ColumnInfo(name = "reviewReasons") val reviewReasons: String? = null,

    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updatedAt") val updatedAt: Long = System.currentTimeMillis()
)

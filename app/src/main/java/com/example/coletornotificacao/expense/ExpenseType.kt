package com.example.coletornotificacao.expense

/**
 * Natureza da operação financeira interpretada a partir de uma notificação.
 *
 * Mapeia os tipos exigidos pelo RF03. A classificação é feita pelo
 * interpretador local ([NotificationParser]) com base em regras determinísticas.
 */
enum class ExpenseType {
    /** Compra aprovada — candidata a virar despesa de consumo. */
    PURCHASE_APPROVED,

    /** Compra recusada/negada — NÃO gera despesa confirmada. */
    PURCHASE_DECLINED,

    /** Estorno ou reembolso — evento financeiro distinto (crédito). */
    REFUND,

    /** Cancelamento de uma compra anterior — evento distinto. */
    CANCELLATION,

    /** Pagamento de fatura — NÃO é nova despesa de consumo. */
    INVOICE_PAYMENT,

    /** Aviso de vencimento ou fechamento de fatura — informativo. */
    INVOICE_NOTICE,

    /** Notificação sem relação direta com uma despesa. */
    UNRELATED,

    /** Natureza não pôde ser determinada com segurança → revisão. */
    UNKNOWN
}

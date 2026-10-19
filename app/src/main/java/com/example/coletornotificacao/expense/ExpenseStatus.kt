package com.example.coletornotificacao.expense

/**
 * Estado do lançamento financeiro (RF05/RF06).
 *
 * Distingue, no mínimo, um lançamento CONFIRMADO de um item aguardando revisão.
 * Totais e relatórios só consideram lançamentos CONFIRMED (RF08).
 */
enum class ExpenseStatus {
    /**
     * Aguardando revisão do usuário: os critérios objetivos de registro
     * automático não foram atendidos, ou o usuário ainda não confirmou.
     * NÃO entra em totais de despesas confirmadas.
     */
    NEEDS_REVIEW,

    /**
     * Lançamento confirmado (automaticamente pelos critérios objetivos ou
     * manualmente pelo usuário). Entra nos totais.
     */
    CONFIRMED,

    /**
     * Item rejeitado pelo usuário (ex.: compra recusada, falso positivo).
     * Não é despesa; mantido para rastreabilidade e para impedir
     * reprocessamento da mesma notificação.
     */
    REJECTED
}

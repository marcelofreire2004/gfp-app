package com.example.coletornotificacao.expense

/**
 * Resultado da interpretação local de uma notificação ([NotificationParser]).
 *
 * É um objeto de dados puro (sem dependência de Android/Room), o que o torna
 * diretamente testável. Todos os campos extraídos são opcionais: a ausência é
 * tratada como motivo de dúvida, não como erro.
 */
data class ParsedNotification(
    /** Natureza classificada da operação (RF03). */
    val type: ExpenseType,

    /** Valor em centavos inteiros, ou null se não identificado/validado. */
    val amountCents: Long? = null,

    /** Estabelecimento/favorecido, ou null se não identificado. */
    val merchant: String? = null,

    /** Instituição financeira inferida (ex.: "Nubank"), ou null. */
    val institution: String? = null,

    /** Últimos 4 dígitos do cartão extraídos do texto, ou null. */
    val cardLast4: String? = null,

    /** Número da parcela (ex.: 2 em "2/10"), ou null. */
    val installmentNumber: Int? = null,

    /** Total de parcelas (ex.: 10 em "2/10"), ou null. */
    val installmentTotal: Int? = null,

    /**
     * Motivos de dúvida / campos não identificados (RF02). Lista explicável que
     * alimenta a decisão de revisão. Vazia quando a interpretação é limpa.
     */
    val reasons: List<String> = emptyList()
)

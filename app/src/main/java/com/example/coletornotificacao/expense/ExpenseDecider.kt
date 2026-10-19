package com.example.coletornotificacao.expense

/**
 * Decisão de registro automático por critérios OBJETIVOS (RF05).
 *
 * Não usa "percentual de confiança": aplica regras verificáveis sobre o
 * resultado do [NotificationParser] e a resolução do cartão. Produz uma
 * [ExpenseEntity] já com o estado correto (CONFIRMED automático ou NEEDS_REVIEW)
 * e os motivos de revisão preenchidos.
 *
 * Puro e testável (sem Android/Room).
 */
object ExpenseDecider {

    /**
     * Resultado da resolução do cartão a partir da notificação (RF04):
     * - RESOLVED: exatamente um cartão identificado.
     * - NONE: nenhum cartão cadastrado casou (não impede, mas é motivo de dúvida).
     * - AMBIGUOUS: mais de um cartão possível → nunca escolher arbitrariamente.
     */
    sealed interface CardResolution {
        data class Resolved(val cardId: Long) : CardResolution
        data object None : CardResolution
        data object Ambiguous : CardResolution
    }

    /**
     * Constrói a despesa a partir da interpretação e da resolução de cartão,
     * decidindo status (CONFIRMED vs NEEDS_REVIEW) por critérios objetivos.
     *
     * @param parsed       saída do interpretador
     * @param sourceStableId chave estável da notificação de origem (RF01)
     * @param notificationCreatedAt horário da notificação (default da data da compra)
     * @param cardResolution resolução de cartão (RF04)
     * @param suggestedCategory categoria sugerida de forma determinística (RF07), ou null
     */
    fun build(
        parsed: ParsedNotification,
        sourceStableId: String,
        notificationCreatedAt: Long,
        cardResolution: CardResolution,
        suggestedCategory: String?,
        now: Long = System.currentTimeMillis()
    ): ExpenseEntity {
        val reasons = parsed.reasons.toMutableList()

        val cardId = when (cardResolution) {
            is CardResolution.Resolved -> cardResolution.cardId
            CardResolution.None -> { reasons.add("Cartão não identificado"); null }
            CardResolution.Ambiguous -> { reasons.add("Cartão ambíguo: múltiplas possibilidades"); null }
        }

        // Critérios objetivos de registro automático (RF05):
        //  - tipo é compra aprovada;
        //  - valor identificado e validado (> 0);
        //  - cartão identificado sem ambiguidade;
        //  - sem conflito relevante (sem outros motivos de dúvida além dos
        //    campos que PODEM ficar pendentes: estabelecimento e categoria).
        val ehCompraAprovada = parsed.type == ExpenseType.PURCHASE_APPROVED
        val valorOk = parsed.amountCents != null && parsed.amountCents > 0
        val cartaoOk = cardResolution is CardResolution.Resolved

        // Estabelecimento e categoria PODEM permanecer pendentes sem bloquear o
        // registro (RF05). Logo, não contam como conflito para a decisão.
        val motivosBloqueantes = reasons.filterNot { motivo ->
            motivo.startsWith("Estabelecimento não identificado") ||
                motivo.startsWith("Instituição não identificada")
        }

        val podeAuto = ehCompraAprovada && valorOk && cartaoOk && motivosBloqueantes.isEmpty()

        if (!ehCompraAprovada && parsed.type != ExpenseType.UNKNOWN) {
            // Operações não-compra (recusada, estorno, cancelamento, pagamento,
            // aviso, não relacionada) nunca viram despesa confirmada automática.
            reasons.add(0, "Operação do tipo ${parsed.type.name} não gera despesa confirmada automaticamente")
        }

        val status = if (podeAuto) ExpenseStatus.CONFIRMED else ExpenseStatus.NEEDS_REVIEW

        return ExpenseEntity(
            sourceStableId = sourceStableId,
            amountCents = parsed.amountCents,
            merchant = parsed.merchant,
            purchaseDate = notificationCreatedAt,
            institution = parsed.institution,
            cardId = cardId,
            cardLast4 = parsed.cardLast4,
            installmentNumber = parsed.installmentNumber,
            installmentTotal = parsed.installmentTotal,
            type = parsed.type,
            category = suggestedCategory,
            status = status,
            autoRegistered = podeAuto,
            reviewReasons = if (status == ExpenseStatus.NEEDS_REVIEW) reasons.joinToString("; ").ifBlank { null } else null,
            createdAt = now,
            updatedAt = now
        )
    }
}

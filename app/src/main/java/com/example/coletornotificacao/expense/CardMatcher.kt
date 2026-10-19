package com.example.coletornotificacao.expense

import com.example.coletornotificacao.expense.ExpenseDecider.CardResolution

/**
 * Resolve qual cartão cadastrado corresponde a uma notificação (RF04), de forma
 * determinística e sem jamais escolher arbitrariamente.
 *
 * Critérios, em ordem:
 *  1. Últimos 4 dígitos extraídos da notificação casando com [CardEntity.last4].
 *  2. Palavra-chave de origem ([CardEntity.matchKeyword]) presente no pacote/texto.
 *  3. Instituição detectada casando com a instituição do cartão.
 *
 * Se mais de um cartão casar (e não houver desempate por last4), retorna
 * AMBIGUOUS. Se nenhum casar, retorna NONE. Pura e testável.
 */
object CardMatcher {

    fun resolve(
        cards: List<CardEntity>,
        parsed: ParsedNotification,
        packageName: String
    ): CardResolution {
        val ativos = cards.filter { it.active }
        if (ativos.isEmpty()) return CardResolution.None

        // 1) Desempate forte por últimos 4 dígitos.
        val last4 = parsed.cardLast4
        if (last4 != null) {
            val porLast4 = ativos.filter { it.last4 == last4 }
            when (porLast4.size) {
                1 -> return CardResolution.Resolved(porLast4.first().id)
                in 2..Int.MAX_VALUE -> return CardResolution.Ambiguous
                else -> { /* nenhum com esse last4; segue para outros critérios */ }
            }
        }

        val pkg = packageName.lowercase()

        // 2) Palavra-chave de origem configurada.
        val porKeyword = ativos.filter { c ->
            val kw = c.matchKeyword?.trim()?.lowercase()
            !kw.isNullOrBlank() && pkg.contains(kw)
        }
        if (porKeyword.size == 1) return CardResolution.Resolved(porKeyword.first().id)
        if (porKeyword.size >= 2) return CardResolution.Ambiguous

        // 3) Instituição detectada.
        val inst = parsed.institution?.trim()?.lowercase()
        if (!inst.isNullOrBlank()) {
            val porInst = ativos.filter { it.institution.trim().lowercase() == inst }
            if (porInst.size == 1) return CardResolution.Resolved(porInst.first().id)
            if (porInst.size >= 2) return CardResolution.Ambiguous
        }

        return CardResolution.None
    }
}

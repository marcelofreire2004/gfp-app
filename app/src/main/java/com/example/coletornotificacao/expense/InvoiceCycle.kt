package com.example.coletornotificacao.expense

import java.util.Calendar
import java.util.Locale

/**
 * Cálculo do ciclo de fatura de um cartão a partir de [dueDay] (dia de
 * vencimento) e [closingDaysBeforeDue] (dias antes do vencimento em que a
 * fatura fecha). Regra de fechamento alinhada ao backend:
 *
 *     diaFechamento = dueDay - closingDaysBeforeDue   (ajustado para meses curtos)
 *
 * A "fatura atual" é o ciclo cujo fechamento é o PRÓXIMO fechamento a ocorrer
 * (ou que fecha hoje). O intervalo de compras do ciclo vai do fechamento
 * anterior (exclusivo) até o fechamento atual (inclusive).
 *
 * Quando o cartão não tem os parâmetros preenchidos, [compute] retorna null e a
 * UI deve exibir "—" e tratar a fatura como o total geral do cartão.
 */
data class InvoiceCycle(
    /** Início do ciclo (epoch millis, inclusive) — dia seguinte ao fechamento anterior. */
    val periodStart: Long,
    /** Fim do ciclo (epoch millis, inclusive) — data de fechamento. */
    val periodEnd: Long,
    /** Data de fechamento (epoch millis, meia-noite). */
    val closingDate: Long,
    /** Data de vencimento (epoch millis, meia-noite). */
    val dueDate: Long
) {
    companion object {

        /** Retorna o ciclo atual do cartão ou null se faltarem parâmetros. */
        fun compute(
            dueDay: Int?,
            closingDaysBeforeDue: Int?,
            now: Long = System.currentTimeMillis()
        ): InvoiceCycle? {
            if (dueDay == null || closingDaysBeforeDue == null) return null
            if (dueDay !in 1..31 || closingDaysBeforeDue < 0) return null

            val base = midnight(now)

            // Candidato: fechamento no mês corrente.
            var closing = closingForMonth(base, dueDay, closingDaysBeforeDue)
            // Se o fechamento deste mês já passou, a fatura atual fecha no próximo mês.
            if (closing < base) {
                val next = addMonths(base, 1)
                closing = closingForMonth(next, dueDay, closingDaysBeforeDue)
            }

            // Fechamento anterior = fechamento do mês anterior ao do fechamento atual.
            val prevMonth = addMonths(closing, -1)
            val prevClosing = closingForMonth(prevMonth, dueDay, closingDaysBeforeDue)

            // Vencimento = no mês do fechamento (ou seguinte, conforme a regra do
            // emissor). Aqui: vencimento no mesmo mês do fechamento se dia de
            // vencimento ainda não passou; caso típico dueDay > diaFechamento.
            val due = dueForClosing(closing, dueDay)

            val start = startOfNextDay(prevClosing)

            return InvoiceCycle(
                periodStart = start,
                periodEnd = endOfDay(closing),
                closingDate = closing,
                dueDate = due
            )
        }

        /** Data de fechamento no mês da referência, com dia = dueDay - closingDays (ajustado). */
        private fun closingForMonth(ref: Long, dueDay: Int, closingDays: Int): Long {
            val c = calendar(ref)
            val maxDia = c.getActualMaximum(Calendar.DAY_OF_MONTH)
            var diaFech = dueDay - closingDays
            // Fechamento em mês anterior quando diaFech <= 0: reposiciona no mês anterior.
            if (diaFech <= 0) {
                c.add(Calendar.MONTH, -1)
                val maxAnt = c.getActualMaximum(Calendar.DAY_OF_MONTH)
                diaFech += maxAnt
                diaFech = diaFech.coerceIn(1, maxAnt)
            } else {
                diaFech = diaFech.coerceAtMost(maxDia)
            }
            c.set(Calendar.DAY_OF_MONTH, diaFech)
            zero(c)
            return c.timeInMillis
        }

        /** Vencimento associado ao fechamento: mesmo mês do fechamento se dueDay >= diaFech. */
        private fun dueForClosing(closing: Long, dueDay: Int): Long {
            val c = calendar(closing)
            val diaFech = c.get(Calendar.DAY_OF_MONTH)
            if (dueDay < diaFech) {
                // Vence no mês seguinte ao fechamento.
                c.add(Calendar.MONTH, 1)
            }
            val max = c.getActualMaximum(Calendar.DAY_OF_MONTH)
            c.set(Calendar.DAY_OF_MONTH, dueDay.coerceIn(1, max))
            zero(c)
            return c.timeInMillis
        }

        private fun midnight(ts: Long): Long {
            val c = calendar(ts); zero(c); return c.timeInMillis
        }

        private fun endOfDay(ts: Long): Long {
            val c = calendar(ts)
            c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59)
            c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999)
            return c.timeInMillis
        }

        private fun startOfNextDay(ts: Long): Long {
            val c = calendar(ts); zero(c); c.add(Calendar.DAY_OF_MONTH, 1); return c.timeInMillis
        }

        private fun addMonths(ts: Long, months: Int): Long {
            val c = calendar(ts); c.add(Calendar.MONTH, months); return c.timeInMillis
        }

        private fun zero(c: Calendar) {
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        }

        private fun calendar(ts: Long): Calendar =
            Calendar.getInstance(Locale("pt", "BR")).apply { timeInMillis = ts }
    }
}

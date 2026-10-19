package com.example.coletornotificacao.expense

import java.util.Locale

/** Formatação monetária BRL a partir de centavos inteiros. */
object Money {
    fun format(cents: Long?): String {
        if (cents == null) return "—"
        val reais = cents / 100
        val cent = (cents % 100).toInt()
        return String.format(Locale("pt", "BR"), "R$ %,d,%02d", reais, cent)
    }

    /** Converte texto digitado ("1.234,56" ou "1234,56" ou "1234.56") em centavos. */
    fun parseToCents(texto: String): Long? {
        val limpo = texto.trim()
        if (limpo.isEmpty()) return null
        // Remove separador de milhar "." quando há vírgula decimal; normaliza vírgula.
        val normal = if (limpo.contains(",")) {
            limpo.replace(".", "").replace(",", ".")
        } else {
            limpo
        }
        val valor = normal.toDoubleOrNull() ?: return null
        if (valor < 0) return null
        return Math.round(valor * 100)
    }
}

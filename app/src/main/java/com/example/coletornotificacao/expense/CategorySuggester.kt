package com.example.coletornotificacao.expense

/**
 * Sugestão de categoria DETERMINÍSTICA e explicável (RF07), sem IA.
 *
 * Ordem de precedência:
 *  1. Associação aprendida (estabelecimento → categoria) previamente confirmada
 *     pelo usuário. É a fonte mais confiável.
 *  2. Palavras-chave locais no nome do estabelecimento.
 *
 * Se o nome for ambíguo ou ausente, NÃO sugere nada (retorna null) — a categoria
 * fica pendente de classificação, sem "chutar" com base em nome ambíguo.
 */
object CategorySuggester {

    /** Normaliza o nome do estabelecimento para servir de chave de aprendizado. */
    fun merchantKey(merchant: String?): String? {
        val m = merchant?.trim()?.lowercase() ?: return null
        if (m.isBlank()) return null
        return m.replace(Regex("\\s+"), " ")
    }

    // Palavras-chave → categoria (RF07). Determinístico e explicável.
    private val REGRAS: List<Pair<List<String>, String>> = listOf(
        listOf("supermercado", "mercado", "atacad", "hortifruti", "carrefour", "pao de acucar") to "Supermercado",
        listOf("posto", "shell", "ipiranga", "petrobras", "combustivel", "gasolina") to "Combustível",
        listOf("restaurante", "lanchonete", "ifood", "pizzaria", "burger", "mcdonald", "padaria", "bar ") to "Alimentação fora de casa",
        listOf("farmacia", "drogaria", "hospital", "clinica", "laboratorio", "droga ") to "Saúde",
        listOf("escola", "faculdade", "curso", "livraria", "udemy", "alura") to "Educação",
        listOf("uber", "99", "taxi", "metro", "onibus", "estacionamento", "pedagio") to "Transporte",
        listOf("magazine", "americanas", "amazon", "mercado livre", "shopee", "aliexpress", "loja") to "Compras pessoais",
        listOf("energia", "luz", "agua", "gas", "aluguel", "condominio", "net ", "internet", "vivo", "claro", "tim ") to "Casa"
    )

    /**
     * Sugere uma categoria a partir do nome do estabelecimento e do histórico
     * aprendido. Retorna null quando não há evidência suficiente.
     *
     * @param learned função que resolve a categoria aprendida para uma chave
     *                (ex.: consulta ao MerchantCategoryDao). Pode retornar null.
     */
    fun suggest(merchant: String?, learned: (String) -> String?): String? {
        val key = merchantKey(merchant) ?: return null

        // 1) Associação aprendida tem prioridade.
        learned(key)?.let { return it }

        // 2) Palavras-chave locais.
        for ((termos, categoria) in REGRAS) {
            if (termos.any { key.contains(it) }) return categoria
        }

        // Nome ambíguo/sem correspondência → não sugere (RF07).
        return null
    }
}

package com.example.coletornotificacao.expense

/**
 * Interpretador LOCAL de notificações (RF02/RF03).
 *
 * Usa regras determinísticas e explicáveis (sem IA, sem rede, sem serviços
 * externos). É puro (não depende de Android) e, portanto, testável por unidade.
 *
 * Estratégia:
 *  1. Classifica a NATUREZA (type) por palavras-chave, em ordem de prioridade —
 *     termos negativos/não-despesa (recusada, estorno, cancelamento, pagamento,
 *     vencimento) são avaliados ANTES de "aprovada" para evitar falsos positivos.
 *  2. Extrai valor (BRL), estabelecimento, parcelas, 4 últimos dígitos e a
 *     instituição a partir do pacote/título/texto.
 *  3. Acumula "motivos de dúvida" sempre que um dado esperado não é encontrado,
 *     alimentando a decisão de revisão ([ExpenseDecider]).
 */
object NotificationParser {

    fun parse(packageName: String, title: String, message: String): ParsedNotification {
        val texto = normalizar("$title\n$message")
        val reasons = mutableListOf<String>()

        val type = classificarTipo(texto)

        val institution = detectarInstituicao(packageName, texto)
        if (institution == null) reasons.add("Instituição não identificada")

        val last4 = extrairUltimos4(texto)
        val (parcNum, parcTot) = extrairParcelas(texto)

        // Valor só é relevante para operações que o carregam.
        val amount = extrairValorCentavos(message) ?: extrairValorCentavos(title)

        var merchant: String? = null

        when (type) {
            ExpenseType.PURCHASE_APPROVED -> {
                if (amount == null) reasons.add("Valor não identificado")
                merchant = extrairEstabelecimento(message) ?: extrairEstabelecimento(title)
                if (merchant == null) reasons.add("Estabelecimento não identificado")
            }
            ExpenseType.REFUND, ExpenseType.CANCELLATION -> {
                if (amount == null) reasons.add("Valor não identificado")
                merchant = extrairEstabelecimento(message) ?: extrairEstabelecimento(title)
                // Vínculo com a compra original NÃO é inventado aqui (RF03).
                reasons.add("Estorno/cancelamento: vínculo com a compra original requer revisão")
            }
            ExpenseType.PURCHASE_DECLINED -> {
                // Compra recusada não gera despesa; não exige valor/estabelecimento.
            }
            ExpenseType.INVOICE_PAYMENT -> {
                // Pagamento de fatura não é nova despesa de consumo (RF03).
            }
            ExpenseType.INVOICE_NOTICE -> {
                // Aviso informativo.
            }
            ExpenseType.UNRELATED -> {
                // Sem relação com despesa.
            }
            ExpenseType.UNKNOWN -> {
                reasons.add("Natureza da operação não pôde ser determinada")
            }
        }

        return ParsedNotification(
            type = type,
            amountCents = amount,
            merchant = merchant,
            institution = institution,
            cardLast4 = last4,
            installmentNumber = parcNum,
            installmentTotal = parcTot,
            reasons = reasons.toList()
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Classificação de tipo (ordem de prioridade importa)
    // ─────────────────────────────────────────────────────────────────────────
    private fun classificarTipo(texto: String): ExpenseType {
        fun contemAlgum(vararg termos: String) = termos.any { texto.contains(it) }

        // 1) Recusada/negada (antes de "aprovada").
        if (contemAlgum("recusada", "negada", "nao autorizada", "não autorizada",
                "transacao negada", "compra negada", "nao aprovada", "não aprovada")) {
            return ExpenseType.PURCHASE_DECLINED
        }

        // 2) Estorno/reembolso.
        if (contemAlgum("estorno", "estornada", "estornado", "reembolso", "devolucao", "devolução")) {
            return ExpenseType.REFUND
        }

        // 3) Cancelamento.
        if (contemAlgum("cancelada", "cancelado", "cancelamento")) {
            return ExpenseType.CANCELLATION
        }

        // 4) Pagamento de fatura (não é consumo novo).
        if (contemAlgum("pagamento da fatura", "pagamento de fatura", "fatura paga",
                "recebemos o pagamento", "pagamento recebido", "fatura foi paga")) {
            return ExpenseType.INVOICE_PAYMENT
        }

        // 5) Aviso de vencimento/fechamento de fatura.
        if (contemAlgum("fatura fechou", "fatura fechada", "fechamento da fatura",
                "fatura disponivel", "fatura disponível", "vencimento da fatura",
                "fatura vence", "sua fatura")) {
            return ExpenseType.INVOICE_NOTICE
        }

        // 6) Compra aprovada.
        if (contemAlgum("compra aprovada", "aprovada", "compra de", "compra no valor",
                "compra realizada", "transacao aprovada", "transação aprovada",
                "aprovamos sua compra", "gasto de")) {
            return ExpenseType.PURCHASE_APPROVED
        }

        // 7) Sinais de operação financeira sem rótulo explícito: valor presente.
        if (contemAlgum("r$")) {
            // Há um valor monetário mas sem rótulo claro → incerto, não assume compra.
            return ExpenseType.UNKNOWN
        }

        return ExpenseType.UNRELATED
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Extração de valor em centavos (formato BRL: 1.234,56)
    // ─────────────────────────────────────────────────────────────────────────
    private val REGEX_VALOR = Regex("""r\$\s*([0-9]{1,3}(?:\.[0-9]{3})*|[0-9]+)(?:,([0-9]{2}))?""")

    fun extrairValorCentavos(textoOriginal: String): Long? {
        val texto = normalizar(textoOriginal)
        val m = REGEX_VALOR.find(texto) ?: return null
        val inteiro = m.groupValues[1].replace(".", "")
        val centavos = m.groupValues[2].ifEmpty { "00" }
        val reais = inteiro.toLongOrNull() ?: return null
        val cent = centavos.toLongOrNull() ?: return null
        val total = reais * 100 + cent
        // Validação: valor positivo e plausível (> 0).
        return if (total > 0) total else null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Estabelecimento: "em <LOJA>", "no <LOJA>", "na <LOJA>" (até pontuação/parcelas)
    // Usa o texto ORIGINAL (preserva maiúsculas), mas casa de forma case-insensitive.
    // ─────────────────────────────────────────────────────────────────────────
    private val REGEX_ESTAB = Regex(
        """\b(?:em|no|na|para)\s+(.+?)(?=\s*(?:[.;,\n]|$|\bno dia\b|\bvalor\b|\bparcel|\bem \d+x\b|\d+/\d+|\bno cart[aã]o\b|\bcart[aã]o\b|\bfinal\b|\bterminad))""",
        RegexOption.IGNORE_CASE
    )

    fun extrairEstabelecimento(textoOriginal: String): String? {
        val m = REGEX_ESTAB.find(textoOriginal) ?: return null
        val bruto = m.groupValues[1].trim().trim('*', '-', ':').trim()
        if (bruto.isBlank()) return null
        // Evita capturar fragmentos puramente numéricos ou muito curtos.
        if (bruto.length < 2) return null
        if (bruto.all { it.isDigit() }) return null
        return bruto
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parcelas: "2/10", "2 de 10", "em 10x"
    // ─────────────────────────────────────────────────────────────────────────
    private val REGEX_PARC_FRACAO = Regex("""\b(\d{1,2})\s*/\s*(\d{1,2})\b""")
    private val REGEX_PARC_DE = Regex("""\b(\d{1,2})\s*de\s*(\d{1,2})\b""")
    private val REGEX_PARC_X = Regex("""\bem\s*(\d{1,2})\s*x\b""")

    fun extrairParcelas(textoNormalizado: String): Pair<Int?, Int?> {
        REGEX_PARC_FRACAO.find(textoNormalizado)?.let {
            return it.groupValues[1].toInt() to it.groupValues[2].toInt()
        }
        REGEX_PARC_DE.find(textoNormalizado)?.let {
            return it.groupValues[1].toInt() to it.groupValues[2].toInt()
        }
        REGEX_PARC_X.find(textoNormalizado)?.let {
            return 1 to it.groupValues[1].toInt()
        }
        return null to null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Últimos 4 dígitos: "final 1234", "****1234", "terminado em 1234"
    // ─────────────────────────────────────────────────────────────────────────
    private val REGEX_LAST4 = Regex("""(?:final|terminad[oa] em|cartao|cartão|\*+)\s*(?:\*+\s*)?(\d{4})\b""")

    fun extrairUltimos4(textoNormalizado: String): String? {
        return REGEX_LAST4.find(textoNormalizado)?.groupValues?.get(1)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Instituição: por pacote conhecido ou por nome no texto
    // ─────────────────────────────────────────────────────────────────────────
    private val INSTITUICOES_PACOTE = mapOf(
        "nubank" to "Nubank", "nu.production" to "Nubank",
        "c6" to "C6", "inter" to "Inter", "itau" to "Itaú",
        "santander" to "Santander", "bradesco" to "Bradesco",
        "bb." to "Banco do Brasil", "caixa" to "Caixa", "safra" to "Safra",
        "xp" to "XP", "mercadopago" to "Mercado Pago", "mercadolibre" to "Mercado Livre"
    )
    private val INSTITUICOES_TEXTO = mapOf(
        "nubank" to "Nubank", "c6 bank" to "C6", "banco inter" to "Inter",
        "itau" to "Itaú", "santander" to "Santander", "bradesco" to "Bradesco",
        "banco do brasil" to "Banco do Brasil", "caixa" to "Caixa",
        "safra" to "Safra", "mercado pago" to "Mercado Pago"
    )

    fun detectarInstituicao(packageName: String, textoNormalizado: String): String? {
        val pkg = packageName.lowercase()
        INSTITUICOES_PACOTE.entries.firstOrNull { pkg.contains(it.key) }?.let { return it.value }
        INSTITUICOES_TEXTO.entries.firstOrNull { textoNormalizado.contains(it.key) }?.let { return it.value }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Normalização: minúsculas + remoção de acentos, para casar palavras-chave.
    // ─────────────────────────────────────────────────────────────────────────
    private fun normalizar(s: String): String {
        val semAcento = s
            .replace('á', 'a').replace('à', 'a').replace('ã', 'a').replace('â', 'a')
            .replace('é', 'e').replace('ê', 'e')
            .replace('í', 'i')
            .replace('ó', 'o').replace('ô', 'o').replace('õ', 'o')
            .replace('ú', 'u').replace('ç', 'c')
        return semAcento.lowercase()
    }
}

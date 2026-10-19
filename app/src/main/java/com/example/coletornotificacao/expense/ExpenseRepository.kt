package com.example.coletornotificacao.expense

import kotlinx.coroutines.flow.Flow

/**
 * Orquestra interpretação → decisão → persistência de despesas, e as operações
 * de confirmação/correção. Fonte única do módulo de despesas.
 *
 * Garantias de domínio:
 *  - Anti-duplicação (RF01): uma notificação (sourceStableId) gera no máximo um
 *    lançamento. Reinterpretar/reprocessar não cria duplicata.
 *  - Confirmar/corrigir ATUALIZA o registro existente (RF09), nunca insere outro.
 *  - A notificação original nunca é tocada por este repositório.
 */
class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val cardDao: CardDao,
    private val merchantCategoryDao: MerchantCategoryDao
) {

    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.observeAll()
    fun expensesByStatus(status: ExpenseStatus): Flow<List<ExpenseEntity>> =
        expenseDao.observeByStatus(status)

    val confirmedTotalCents: Flow<Long> = expenseDao.observeConfirmedTotalCents()
    val reviewCount: Flow<Int> = expenseDao.observeReviewCount()
    val totalsByCard: Flow<List<CardTotal>> = expenseDao.observeTotalsByCard()
    val totalsByCategory: Flow<List<CategoryTotal>> = expenseDao.observeTotalsByCategory()

    /**
     * Interpreta uma notificação capturada e, se aplicável, cria o lançamento.
     *
     * Idempotente por [sourceStableId]: se já existe despesa para a notificação,
     * NÃO cria outra (retorna o resultado como "já existente"). A falha de
     * interpretação não deve impedir a captura — o chamador trata exceções.
     *
     * @return o resultado da tentativa (criado, já existente, ou ignorado).
     */
    suspend fun interpretarEPersistir(
        sourceStableId: String,
        packageName: String,
        title: String,
        message: String,
        notificationCreatedAt: Long
    ): InterpretResult {
        // Camada local de anti-duplicação (RF01 / seção 6): short-circuit se já há.
        if (expenseDao.existsForSource(sourceStableId)) {
            return InterpretResult.AlreadyExists
        }

        val parsed = NotificationParser.parse(packageName, title, message)

        // Operações claramente sem relação com despesa não geram lançamento.
        if (parsed.type == ExpenseType.UNRELATED) {
            return InterpretResult.Ignored(parsed.type)
        }

        val cards = cardDao.listActive()
        val resolution = CardMatcher.resolve(cards, parsed, packageName)

        // Resolve a associação aprendida (consulta suspensa) ANTES, e a passa ao
        // sugestor determinístico, que prioriza o aprendido sobre a regra local.
        val merchantKey = CategorySuggester.merchantKey(parsed.merchant)
        val aprendida = if (merchantKey != null) merchantCategoryDao.categoryFor(merchantKey) else null
        val suggested = CategorySuggester.suggest(parsed.merchant) { aprendida }

        val expense = ExpenseDecider.build(
            parsed = parsed,
            sourceStableId = sourceStableId,
            notificationCreatedAt = notificationCreatedAt,
            cardResolution = resolution,
            suggestedCategory = suggested
        )

        // OnConflict IGNORE no índice único garante a 2ª barreira contra corrida.
        val rowId = expenseDao.insert(expense)
        return if (rowId == -1L) InterpretResult.AlreadyExists
        else InterpretResult.Created(rowId, expense.status)
    }

    /**
     * Confirma um item em revisão (RF09). Atualiza o MESMO registro; nunca cria
     * outro. Opcionalmente aplica correções de campos editados pelo usuário.
     */
    suspend fun confirmar(
        id: Long,
        merchant: String? = null,
        amountCents: Long? = null,
        cardId: Long? = null,
        category: String? = null
    ) {
        val atual = expenseDao.findById(id) ?: return
        val corrigido = atual.copy(
            merchant = merchant ?: atual.merchant,
            amountCents = amountCents ?: atual.amountCents,
            cardId = cardId ?: atual.cardId,
            category = category ?: atual.category,
            status = ExpenseStatus.CONFIRMED,
            autoRegistered = false, // confirmação manual
            reviewReasons = null,
            updatedAt = System.currentTimeMillis()
        )
        expenseDao.update(corrigido)

        // Aprendizado determinístico: memoriza estabelecimento → categoria (RF07).
        aprenderCategoria(corrigido.merchant, corrigido.category)
    }

    /** Corrige campos de um lançamento sem alterar seu estado (RF09). */
    suspend fun corrigir(
        id: Long,
        merchant: String? = null,
        amountCents: Long? = null,
        cardId: Long? = null,
        category: String? = null
    ) {
        val atual = expenseDao.findById(id) ?: return
        val corrigido = atual.copy(
            merchant = merchant ?: atual.merchant,
            amountCents = amountCents ?: atual.amountCents,
            cardId = cardId ?: atual.cardId,
            category = category ?: atual.category,
            updatedAt = System.currentTimeMillis()
        )
        expenseDao.update(corrigido)
        if (category != null) aprenderCategoria(corrigido.merchant, category)
    }

    /** Rejeita um item em revisão (ex.: falso positivo). Mantém o registro. */
    suspend fun rejeitar(id: Long) {
        val atual = expenseDao.findById(id) ?: return
        expenseDao.update(
            atual.copy(status = ExpenseStatus.REJECTED, updatedAt = System.currentTimeMillis())
        )
    }

    private suspend fun aprenderCategoria(merchant: String?, category: String?) {
        val key = CategorySuggester.merchantKey(merchant) ?: return
        if (category.isNullOrBlank()) return
        merchantCategoryDao.upsert(MerchantCategoryEntity(merchantKey = key, categoryName = category))
    }

    /** Resultado de [interpretarEPersistir]. */
    sealed interface InterpretResult {
        data class Created(val id: Long, val status: ExpenseStatus) : InterpretResult
        data object AlreadyExists : InterpretResult
        data class Ignored(val type: ExpenseType) : InterpretResult
    }
}

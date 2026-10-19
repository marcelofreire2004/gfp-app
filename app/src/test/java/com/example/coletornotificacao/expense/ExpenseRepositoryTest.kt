package com.example.coletornotificacao.expense

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Testes de integração do módulo de despesas com fakes em memória:
 * decisão de registro automático, revisão, anti-duplicação, confirmação e
 * aprendizado/correção de categoria.
 */
class ExpenseRepositoryTest {

    private lateinit var expenseDao: FakeExpenseDao
    private lateinit var cardDao: FakeCardDao
    private lateinit var merchantDao: FakeMerchantCategoryDao
    private lateinit var repo: ExpenseRepository

    private val PKG_NU = "com.nu.production"

    @Before
    fun setup() {
        expenseDao = FakeExpenseDao()
        cardDao = FakeCardDao()
        merchantDao = FakeMerchantCategoryDao()
        repo = ExpenseRepository(expenseDao, cardDao, merchantDao)
    }

    private suspend fun cadastrarCartao(
        institution: String, last4: String? = null, keyword: String? = null, active: Boolean = true
    ): Long = cardDao.insert(
        CardEntity(name = institution, institution = institution, last4 = last4, matchKeyword = keyword, active = active)
    )

    @Test
    fun `compra aprovada com cartao unico vira despesa confirmada automatica`() = runTest {
        cadastrarCartao("Nubank", last4 = "1234")
        val r = repo.interpretarEPersistir(
            sourceStableId = "s1", packageName = PKG_NU,
            title = "Compra aprovada",
            message = "Compra aprovada de R$ 50,00 em PADARIA CENTRAL no cartão final 1234",
            notificationCreatedAt = 1_000L
        )
        assertTrue(r is ExpenseRepository.InterpretResult.Created)
        val e = expenseDao.snapshot().first()
        assertEquals(ExpenseStatus.CONFIRMED, e.status)
        assertTrue(e.autoRegistered)
        assertEquals(5000L, e.amountCents)
        assertNotNull(e.cardId)
    }

    @Test
    fun `compra aprovada sem valor vai para revisao`() = runTest {
        cadastrarCartao("Nubank", last4 = "1234")
        repo.interpretarEPersistir("s2", PKG_NU, "Compra aprovada", "Compra aprovada em LOJA no cartão final 1234", 1_000L)
        val e = expenseDao.snapshot().first()
        assertEquals(ExpenseStatus.NEEDS_REVIEW, e.status)
        assertFalse(e.autoRegistered)
        assertTrue(e.reviewReasons!!.contains("Valor", ignoreCase = true))
    }

    @Test
    fun `cartao ambiguo impede registro automatico e vai para revisao`() = runTest {
        // Dois cartões Nubank ativos, sem last4 na notificação → ambíguo.
        cadastrarCartao("Nubank")
        cadastrarCartao("Nubank")
        repo.interpretarEPersistir("s3", PKG_NU, "Compra aprovada", "Compra aprovada de R$ 20,00 em MERCADO", 1_000L)
        val e = expenseDao.snapshot().first()
        assertEquals(ExpenseStatus.NEEDS_REVIEW, e.status)
        assertTrue(e.reviewReasons!!.contains("ambíguo", ignoreCase = true))
    }

    @Test
    fun `compra recusada nao vira despesa confirmada`() = runTest {
        cadastrarCartao("Nubank", last4 = "1234")
        repo.interpretarEPersistir("s4", PKG_NU, "Compra recusada", "Compra de R$ 99,00 recusada no cartão final 1234", 1_000L)
        val e = expenseDao.snapshot().first()
        assertEquals(ExpenseType.PURCHASE_DECLINED, e.type)
        assertEquals(ExpenseStatus.NEEDS_REVIEW, e.status)
        assertFalse(e.autoRegistered)
    }

    @Test
    fun `pagamento de fatura nao vira despesa de consumo`() = runTest {
        repo.interpretarEPersistir("s5", PKG_NU, "Fatura", "Recebemos o pagamento da fatura de R$ 500,00", 1_000L)
        val e = expenseDao.snapshot().first()
        assertEquals(ExpenseType.INVOICE_PAYMENT, e.type)
        assertEquals(ExpenseStatus.NEEDS_REVIEW, e.status)
        // Não entra no total de despesas confirmadas.
        assertEquals(0L, repo.confirmedTotalCents.first())
    }

    @Test
    fun `reprocessar a mesma notificacao nao duplica`() = runTest {
        cadastrarCartao("Nubank", last4 = "1234")
        val msg = "Compra aprovada de R$ 50,00 em PADARIA no cartão final 1234"
        val r1 = repo.interpretarEPersistir("dup", PKG_NU, "Compra aprovada", msg, 1_000L)
        val r2 = repo.interpretarEPersistir("dup", PKG_NU, "Compra aprovada", msg, 1_000L)
        assertTrue(r1 is ExpenseRepository.InterpretResult.Created)
        assertTrue(r2 is ExpenseRepository.InterpretResult.AlreadyExists)
        assertEquals(1, expenseDao.snapshot().size)
    }

    @Test
    fun `confirmar item em revisao atualiza o mesmo registro sem duplicar`() = runTest {
        repo.interpretarEPersistir("s6", PKG_NU, "Compra aprovada", "Compra aprovada de R$ 10,00", 1_000L)
        val antes = expenseDao.snapshot().first()
        assertEquals(ExpenseStatus.NEEDS_REVIEW, antes.status)

        repo.confirmar(antes.id, merchant = "MERCADO X", cardId = null, category = "Supermercado")

        val depois = expenseDao.snapshot()
        assertEquals(1, depois.size) // não duplicou
        assertEquals(ExpenseStatus.CONFIRMED, depois.first().status)
        assertEquals("Supermercado", depois.first().category)
        assertFalse(depois.first().autoRegistered)
    }

    @Test
    fun `sugestao de categoria por palavra-chave e correcao com aprendizado`() = runTest {
        cadastrarCartao("Nubank", last4 = "1234")
        // "posto shell" casa com a regra de Combustível.
        repo.interpretarEPersistir("s7", PKG_NU, "Compra aprovada", "Compra aprovada de R$ 200,00 em POSTO SHELL no cartão final 1234", 1_000L)
        val e = expenseDao.snapshot().first()
        assertEquals("Combustível", e.category)

        // Usuário corrige a categoria → vira associação aprendida.
        repo.corrigir(e.id, category = "Transporte")
        val key = CategorySuggester.merchantKey(e.merchant)!!
        val aprendida = merchantDao.categoryFor(key)
        assertEquals("Transporte", CategorySuggester.suggest(e.merchant) { aprendida })
    }

    @Test
    fun `categoria sugerida vem do aprendizado antes da regra`() = runTest {
        cadastrarCartao("Nubank", last4 = "1234")
        // Ensina MERCADO BOM → Casa (contraria a regra padrão de supermercado).
        merchantDao.upsert(MerchantCategoryEntity(merchantKey = "mercado bom", categoryName = "Casa"))
        repo.interpretarEPersistir("s8", PKG_NU, "Compra aprovada", "Compra aprovada de R$ 15,00 em MERCADO BOM no cartão final 1234", 1_000L)
        val e = expenseDao.snapshot().first()
        assertEquals("Casa", e.category)
    }

    @Test
    fun `notificacao sem relacao nao gera lancamento`() = runTest {
        val r = repo.interpretarEPersistir("s9", "com.app.x", "Oi", "Mensagem qualquer", 1_000L)
        assertTrue(r is ExpenseRepository.InterpretResult.Ignored)
        assertEquals(0, expenseDao.snapshot().size)
    }
}

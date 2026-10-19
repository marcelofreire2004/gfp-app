package com.example.coletornotificacao.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do interpretador local (RF02/RF03) e da extração de campos.
 * Cobre os casos de natureza de operação e de extração de valor/parcelas.
 */
class NotificationParserTest {

    private val PKG_NU = "com.nu.production"

    @Test
    fun `compra aprovada com todos os campos`() {
        val p = NotificationParser.parse(
            PKG_NU,
            "Compra aprovada",
            "Compra aprovada de R$ 123,45 em POSTO SHELL no cartão final 1234"
        )
        assertEquals(ExpenseType.PURCHASE_APPROVED, p.type)
        assertEquals(12345L, p.amountCents)
        assertEquals("Nubank", p.institution)
        assertEquals("1234", p.cardLast4)
        assertNotNull(p.merchant)
        assertTrue(p.merchant!!.contains("SHELL", ignoreCase = true))
    }

    @Test
    fun `valor ausente gera motivo de duvida`() {
        val p = NotificationParser.parse(PKG_NU, "Compra aprovada", "Compra aprovada em LOJA X")
        assertEquals(ExpenseType.PURCHASE_APPROVED, p.type)
        assertNull(p.amountCents)
        assertTrue(p.reasons.any { it.contains("Valor", ignoreCase = true) })
    }

    @Test
    fun `estabelecimento desconhecido gera motivo de duvida`() {
        val p = NotificationParser.parse(PKG_NU, "Compra aprovada", "Compra aprovada de R$ 10,00")
        assertEquals(ExpenseType.PURCHASE_APPROVED, p.type)
        assertEquals(1000L, p.amountCents)
        assertNull(p.merchant)
        assertTrue(p.reasons.any { it.contains("Estabelecimento", ignoreCase = true) })
    }

    @Test
    fun `compra recusada e classificada como declined`() {
        val p = NotificationParser.parse(PKG_NU, "Compra recusada", "Sua compra de R$ 80,00 foi recusada")
        assertEquals(ExpenseType.PURCHASE_DECLINED, p.type)
    }

    @Test
    fun `pagamento de fatura e classificado como invoice payment`() {
        val p = NotificationParser.parse(PKG_NU, "Fatura", "Recebemos o pagamento da fatura no valor de R$ 500,00")
        assertEquals(ExpenseType.INVOICE_PAYMENT, p.type)
    }

    @Test
    fun `estorno e classificado como refund`() {
        val p = NotificationParser.parse(PKG_NU, "Estorno", "Estorno de R$ 30,00 referente a sua compra")
        assertEquals(ExpenseType.REFUND, p.type)
        assertTrue(p.reasons.any { it.contains("vínculo", ignoreCase = true) || it.contains("vinculo", ignoreCase = true) })
    }

    @Test
    fun `cancelamento e classificado como cancellation`() {
        val p = NotificationParser.parse(PKG_NU, "Compra cancelada", "Sua compra de R$ 45,00 foi cancelada")
        assertEquals(ExpenseType.CANCELLATION, p.type)
    }

    @Test
    fun `aviso de fatura e classificado como invoice notice`() {
        val p = NotificationParser.parse(PKG_NU, "Fatura disponível", "Sua fatura fechou e vence em breve")
        assertEquals(ExpenseType.INVOICE_NOTICE, p.type)
    }

    @Test
    fun `notificacao sem relacao com despesa`() {
        val p = NotificationParser.parse("com.app.qualquer", "Bem-vindo", "Aproveite nossas novidades")
        assertEquals(ExpenseType.UNRELATED, p.type)
    }

    @Test
    fun `extrai parcelas no formato fracao`() {
        val p = NotificationParser.parse(PKG_NU, "Compra aprovada", "Compra de R$ 100,00 em LOJA 2/10")
        assertEquals(2, p.installmentNumber)
        assertEquals(10, p.installmentTotal)
    }

    @Test
    fun `valor brasileiro com milhar`() {
        assertEquals(123456L, NotificationParser.extrairValorCentavos("Total R$ 1.234,56"))
        assertEquals(1000L, NotificationParser.extrairValorCentavos("R$ 10"))
        assertNull(NotificationParser.extrairValorCentavos("sem valor aqui"))
    }
}

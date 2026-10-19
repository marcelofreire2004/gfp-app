package com.example.coletornotificacao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IdempotencyKeyTest {

    @Test
    fun `mesmo conteudo gera o mesmo id estavel`() {
        val a = IdempotencyKey.generate("com.nu.production", "Compra", "R$ 10", 1_000L)
        val b = IdempotencyKey.generate("com.nu.production", "Compra", "R$ 10", 1_000L)
        assertEquals(a, b)
    }

    @Test
    fun `conteudo diferente gera id diferente`() {
        val a = IdempotencyKey.generate("com.nu.production", "Compra", "R$ 10", 1_000L)
        val b = IdempotencyKey.generate("com.nu.production", "Compra", "R$ 20", 1_000L)
        assertNotEquals(a, b)
    }

    @Test
    fun `createdAt faz parte da chave (nao usa horario atual arbitrario)`() {
        val a = IdempotencyKey.generate("p", "t", "m", 1_000L)
        val b = IdempotencyKey.generate("p", "t", "m", 2_000L)
        assertNotEquals(a, b)
    }

    @Test
    fun `id tem formato sha-256 hex de 64 caracteres`() {
        val id = IdempotencyKey.generate("p", "t", "m", 1_000L)
        assertEquals(64, id.length)
        assertEquals(true, id.all { it in '0'..'9' || it in 'a'..'f' })
    }
}

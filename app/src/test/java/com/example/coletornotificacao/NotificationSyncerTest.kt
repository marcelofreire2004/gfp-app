package com.example.coletornotificacao

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class NotificationSyncerTest {

    private lateinit var dao: FakeNotificationDao
    private lateinit var repository: NotificationRepository
    private lateinit var api: FakeNotificationApi
    private lateinit var syncer: NotificationSyncer

    @Before
    fun setup() {
        dao = FakeNotificationDao()
        repository = NotificationRepository(dao)
        api = FakeNotificationApi()
        syncer = NotificationSyncer(repository, api)
    }

    @Test
    fun `primeira tentativa com sucesso marca SYNCED e conta 1 tentativa`() = runTest {
        repository.insertCaptured("p", "t", "m", 1_000L)
        api.behavior = FakeNotificationApi.Behavior.Success(201)

        val result = syncer.syncPending()

        assertEquals(1, result.success)
        assertEquals(0, result.failed)
        val rec = dao.snapshot().first()
        assertEquals(SyncState.SYNCED, rec.syncState)
        assertNull(rec.lastError)
        // attemptCount reflete tentativas INICIADAS, inclusive a bem-sucedida.
        assertEquals(1, rec.attemptCount)
    }

    @Test
    fun `primeira tentativa com falha conta 1 tentativa`() = runTest {
        repository.insertCaptured("p", "t", "m", 1_000L)
        api.behavior = FakeNotificationApi.Behavior.HttpError(500)

        syncer.syncPending()

        val rec = dao.snapshot().first()
        assertEquals(SyncState.FAILED, rec.syncState)
        assertEquals(1, rec.attemptCount)
    }

    @Test
    fun `falha seguida de sucesso conta 2 tentativas`() = runTest {
        repository.insertCaptured("p", "t", "m", 1_000L)

        api.behavior = FakeNotificationApi.Behavior.NetworkFailure(IOException("x"))
        syncer.syncPending()
        api.behavior = FakeNotificationApi.Behavior.Success()
        syncer.syncPending()

        val rec = dao.snapshot().first()
        assertEquals(SyncState.SYNCED, rec.syncState)
        assertEquals(2, rec.attemptCount)
    }

    @Test
    fun `falha de rede mantem o registro pendente (FAILED)`() = runTest {
        repository.insertCaptured("p", "t", "m", 1_000L)
        api.behavior = FakeNotificationApi.Behavior.NetworkFailure(IOException("timeout"))

        val result = syncer.syncPending()

        assertEquals(0, result.success)
        assertEquals(1, result.failed)
        val rec = dao.snapshot().first()
        assertEquals(SyncState.FAILED, rec.syncState)
        assertEquals(1, rec.attemptCount)
        // Ainda é considerado pendente para reenvio.
        assertEquals(1, repository.getPendingCount())
    }

    @Test
    fun `resposta HTTP de erro nao marca como SYNCED`() = runTest {
        repository.insertCaptured("p", "t", "m", 1_000L)
        api.behavior = FakeNotificationApi.Behavior.HttpError(500)

        syncer.syncPending()

        val rec = dao.snapshot().first()
        assertEquals(SyncState.FAILED, rec.syncState)
        assertEquals("HTTP 500", rec.lastError)
    }

    @Test
    fun `data original da captura e preservada no envio`() = runTest {
        val original = 1_234_567_890L
        repository.insertCaptured("p", "titulo", "msg", original)
        api.behavior = FakeNotificationApi.Behavior.Success()

        syncer.syncPending()

        assertEquals(1, api.requests.size)
        assertEquals(original, api.requests.first().createdAt)
    }

    @Test
    fun `tentativa posterior reenvia um registro que falhou antes`() = runTest {
        repository.insertCaptured("p", "t", "m", 1_000L)

        // Primeira tentativa falha.
        api.behavior = FakeNotificationApi.Behavior.NetworkFailure(IOException("offline"))
        syncer.syncPending()
        assertEquals(SyncState.FAILED, dao.snapshot().first().syncState)

        // Rede volta: segunda tentativa tem sucesso (FAILED ainda é elegível).
        api.behavior = FakeNotificationApi.Behavior.Success()
        val result = syncer.syncPending()

        assertEquals(1, result.success)
        assertEquals(SyncState.SYNCED, dao.snapshot().first().syncState)
    }

    @Test
    fun `reenvio do mesmo registro mantem o mesmo stableId`() = runTest {
        repository.insertCaptured("p", "t", "m", 1_000L)
        val stableBefore = dao.snapshot().first().stableId

        api.behavior = FakeNotificationApi.Behavior.NetworkFailure(IOException("x"))
        syncer.syncPending()
        api.behavior = FakeNotificationApi.Behavior.Success()
        syncer.syncPending()

        // Todas as requisições enviadas usaram o mesmo stableId.
        assertTrue(api.requests.isNotEmpty())
        assertTrue(api.requests.all { it.stableId == stableBefore })
    }

    @Test
    fun `lastError e um rotulo tecnico controlado sem conteudo nem mensagem de excecao`() = runTest {
        // Nem o conteúdo sensível nem a mensagem da exceção devem aparecer.
        repository.insertCaptured("com.nu", "Compra R$ 999", "Pix para Fulano", 1_000L)
        api.behavior =
            FakeNotificationApi.Behavior.NetworkFailure(IOException("Connection refused to 10.0.0.1"))

        syncer.syncPending()

        val err = dao.snapshot().first().lastError ?: ""
        assertTrue(err.isNotBlank())
        // Rótulo controlado esperado para IOException genérica.
        assertEquals("IO_ERROR", err)
        // Garante ausência de conteúdo da notificação e da mensagem bruta.
        assertFalseContains(err, "Compra R$ 999")
        assertFalseContains(err, "Pix para Fulano")
        assertFalseContains(err, "Connection refused")
        assertFalseContains(err, "10.0.0.1")
    }

    @Test
    fun `cancelamento da coroutine e repropagado e nao marca FAILED`() = runTest {
        repository.insertCaptured("p", "t", "m", 1_000L)
        api.behavior =
            FakeNotificationApi.Behavior.NetworkFailure(CancellationException("cancelado"))

        var propagated = false
        try {
            syncer.syncPending()
        } catch (ce: CancellationException) {
            propagated = true
        }

        assertTrue("CancellationException deve ser repropagada", propagated)
        val rec = dao.snapshot().first()
        // O cancelamento não vira falha de rede: o registro continua pendente.
        assertEquals(SyncState.PENDING, rec.syncState)
        assertNull(rec.lastError)
        // A tentativa foi iniciada antes do cancelamento.
        assertEquals(1, rec.attemptCount)
    }

    private fun assertFalseContains(haystack: String, needle: String) {
        assertTrue(
            "lastError não deve conter conteúdo sensível: '$needle'",
            !haystack.contains(needle)
        )
    }
}

package com.example.coletornotificacao

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationRepositoryTest {

    private lateinit var dao: FakeNotificationDao
    private lateinit var repository: NotificationRepository

    @Before
    fun setup() {
        dao = FakeNotificationDao()
        repository = NotificationRepository(dao)
    }

    @Test
    fun `notificacao capturada e salva localmente como PENDING`() = runTest {
        val inserted = repository.insertCaptured("com.nu.production", "Compra", "R$ 10", 1_000L)
        assertTrue(inserted)

        val all = repository.allNotifications.first()
        assertEquals(1, all.size)
        assertEquals(SyncState.PENDING, all[0].syncState)
        assertEquals(1, repository.getPendingCount())
    }

    @Test
    fun `notificacao salva permanece disponivel (sem depender de rede)`() = runTest {
        repository.insertCaptured("com.nu.production", "Compra", "R$ 10", 1_000L)
        // Nenhuma sincronização ocorre; o registro continua consultável localmente.
        val all = repository.allNotifications.first()
        assertEquals(1, all.size)
        assertEquals("Compra", all[0].title)
    }

    @Test
    fun `captura duplicada (mesmo conteudo) nao gera registro duplicado`() = runTest {
        val first = repository.insertCaptured("p", "t", "m", 1_000L)
        val second = repository.insertCaptured("p", "t", "m", 1_000L)
        assertTrue(first)
        assertFalse(second) // ignorada pelo stableId único
        assertEquals(1, repository.allNotifications.first().size)
    }

    @Test
    fun `deleteSynced remove apenas SYNCED e preserva pendentes`() = runTest {
        // Um SYNCED, um PENDING e um FAILED.
        repository.insertCaptured("p", "t", "sync", 1_000L)
        repository.insertCaptured("p", "t", "pend", 2_000L)
        repository.insertCaptured("p", "t", "fail", 3_000L)
        val ids = dao.snapshot().associate { it.message to it.id }
        repository.registerAttempt(ids.getValue("sync"))
        repository.markSynced(ids.getValue("sync"))
        repository.registerAttempt(ids.getValue("fail"))
        repository.markFailed(ids.getValue("fail"), "HTTP 500")

        val removed = repository.deleteSynced()

        assertEquals(1, removed)
        val remaining = repository.allNotifications.first()
        assertEquals(2, remaining.size)
        // Nenhum registro restante está SYNCED; pendentes preservados.
        assertTrue(remaining.none { it.syncState == SyncState.SYNCED })
        assertTrue(remaining.any { it.syncState == SyncState.PENDING })
        assertTrue(remaining.any { it.syncState == SyncState.FAILED })
    }

    @Test
    fun `requeueFailed volta registros FAILED para PENDING`() = runTest {
        repository.insertCaptured("p", "t", "m", 1_000L)
        val id = dao.snapshot().first().id
        repository.markFailed(id, "HTTP 500")
        assertEquals(SyncState.FAILED, dao.snapshot().first().syncState)

        val requeued = repository.requeueFailed()
        assertEquals(1, requeued)
        assertEquals(SyncState.PENDING, dao.snapshot().first().syncState)
    }
}

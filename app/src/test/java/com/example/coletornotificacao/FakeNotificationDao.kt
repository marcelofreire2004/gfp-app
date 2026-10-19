package com.example.coletornotificacao

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Implementação em memória do [NotificationDao] para testes de unidade do
 * Repository e do Syncer, sem depender do Room nem de um dispositivo.
 */
class FakeNotificationDao : NotificationDao {

    private val state = MutableStateFlow<List<NotificationEntity>>(emptyList())
    private var autoId = 1L

    fun snapshot(): List<NotificationEntity> = state.value

    override suspend fun insert(notification: NotificationEntity): Long {
        // Simula OnConflictStrategy.IGNORE no índice único stableId.
        if (state.value.any { it.stableId == notification.stableId }) return -1L
        val id = autoId++
        state.value = state.value + notification.copy(id = id)
        return id
    }

    override fun getAllNotifications(): Flow<List<NotificationEntity>> =
        state.map { list -> list.sortedByDescending { it.createdAt } }

    override fun getPackageCounts(): Flow<List<PackageCount>> =
        state.map { list ->
            list.groupBy { it.packageName }
                .map { (pkg, items) -> PackageCount(pkg, items.size) }
                .sortedByDescending { it.totalMessages }
        }

    override fun getNotificationsByPackage(packageName: String): Flow<List<NotificationEntity>> =
        state.map { list ->
            list.filter { it.packageName == packageName }.sortedByDescending { it.createdAt }
        }

    override suspend fun getPendingForSync(): List<NotificationEntity> =
        state.value.filter { it.syncState == SyncState.PENDING || it.syncState == SyncState.FAILED }
            .sortedBy { it.createdAt }

    override fun observePendingCount(): Flow<Int> =
        state.map { list -> list.count { it.syncState != SyncState.SYNCED } }

    override suspend fun getPendingCount(): Int =
        state.value.count { it.syncState != SyncState.SYNCED }

    override suspend fun registerAttempt(id: Long, attemptAt: Long) {
        state.value = state.value.map {
            if (it.id == id) it.copy(
                attemptCount = it.attemptCount + 1,
                lastAttemptAt = attemptAt
            ) else it
        }
    }

    override suspend fun markSynced(id: Long, attemptAt: Long) {
        state.value = state.value.map {
            if (it.id == id) it.copy(
                syncState = SyncState.SYNCED,
                lastAttemptAt = attemptAt,
                lastError = null
            ) else it
        }
    }

    override suspend fun markFailed(id: Long, attemptAt: Long, error: String) {
        state.value = state.value.map {
            if (it.id == id) it.copy(
                syncState = SyncState.FAILED,
                lastAttemptAt = attemptAt,
                lastError = error
            ) else it
        }
    }

    override suspend fun requeueFailed(): Int {
        var count = 0
        state.value = state.value.map {
            if (it.syncState == SyncState.FAILED) {
                count++
                it.copy(syncState = SyncState.PENDING)
            } else it
        }
        return count
    }

    override suspend fun deleteSynced(): Int {
        val before = state.value.size
        state.value = state.value.filter { it.syncState != SyncState.SYNCED }
        return before - state.value.size
    }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}

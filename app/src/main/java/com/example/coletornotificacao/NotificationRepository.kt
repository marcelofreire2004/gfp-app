package com.example.coletornotificacao

import kotlinx.coroutines.flow.Flow

/**
 * Fonte única de acesso a dados e sincronização.
 *
 * Centraliza persistência local e as regras de estado de sincronização.
 * Não executa chamadas de rede diretamente: o envio é feito pelo
 * [NotificationSyncer]/WorkManager, que usa este repositório para ler
 * pendentes e atualizar estados.
 */
class NotificationRepository(private val dao: NotificationDao) {

    val allNotifications: Flow<List<NotificationEntity>> = dao.getAllNotifications()

    fun packageCounts(): Flow<List<PackageCount>> = dao.getPackageCounts()

    fun notificationsByPackage(packageName: String): Flow<List<NotificationEntity>> =
        dao.getNotificationsByPackage(packageName)

    /** Quantidade de registros pendentes (PENDING + FAILED), observável pela UI. */
    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun getPendingCount(): Int = dao.getPendingCount()

    /**
     * Insere uma notificação capturada a partir dos campos originais.
     *
     * Calcula o [IdempotencyKey] estável e define o estado inicial PENDING.
     * Se a mesma notificação já existir (mesmo stableId), a inserção é
     * ignorada (ver DAO) e o registro existente é preservado.
     *
     * @return true se um novo registro foi inserido; false se já existia.
     */
    suspend fun insertCaptured(
        packageName: String,
        title: String,
        message: String,
        createdAt: Long
    ): Boolean {
        val stableId = IdempotencyKey.generate(packageName, title, message, createdAt)
        val entity = NotificationEntity(
            stableId = stableId,
            packageName = packageName,
            title = title,
            message = message,
            createdAt = createdAt,
            syncState = SyncState.PENDING
        )
        return dao.insert(entity) != -1L
    }

    /**
     * Calcula o stableId de uma captura sem persistir. Útil para que o
     * interpretador de despesas referencie a notificação de origem pela mesma
     * chave estável (RF01), mantendo a geração da chave em um único lugar.
     */
    fun stableIdFor(packageName: String, title: String, message: String, createdAt: Long): String =
        IdempotencyKey.generate(packageName, title, message, createdAt)

    /** Registros que ainda precisam ser enviados. */
    suspend fun getPendingForSync(): List<NotificationEntity> = dao.getPendingForSync()

    /**
     * Registra o início de uma tentativa de envio (incrementa attemptCount).
     * Deve ser chamado imediatamente antes da chamada de rede.
     */
    suspend fun registerAttempt(id: Long, attemptAt: Long = System.currentTimeMillis()) =
        dao.registerAttempt(id, attemptAt)

    /** Marca como sincronizado após confirmação válida do servidor. */
    suspend fun markSynced(id: Long, attemptAt: Long = System.currentTimeMillis()) =
        dao.markSynced(id, attemptAt)

    /** Registra o resultado malsucedido de uma tentativa, mantendo o registro pendente. */
    suspend fun markFailed(
        id: Long,
        error: String,
        attemptAt: Long = System.currentTimeMillis()
    ) = dao.markFailed(id, attemptAt, error)

    /** Reenfileira (volta para PENDING) os registros FAILED. Usado no reenvio manual. */
    suspend fun requeueFailed(): Int = dao.requeueFailed()

    /**
     * Exclusão segura: apaga somente o histórico SYNCED, preservando os
     * registros pendentes (PENDING/FAILED). Retorna quantas linhas removeu.
     */
    suspend fun deleteSynced(): Int = dao.deleteSynced()

    suspend fun deleteAll() = dao.deleteAll()
}

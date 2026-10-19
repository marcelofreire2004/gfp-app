package com.example.coletornotificacao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    /**
     * Insere uma notificação. Em caso de conflito no índice único [NotificationEntity.stableId]
     * (mesma notificação capturada novamente), ignora — evita duplicar o registro local.
     * Retorna o rowId inserido, ou -1 quando ignorado por conflito.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: NotificationEntity): Long

    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query(
        """
        SELECT packageName, COUNT(*) as totalMessages
        FROM notifications
        GROUP BY packageName
        ORDER BY totalMessages DESC
        """
    )
    fun getPackageCounts(): Flow<List<PackageCount>>

    @Query(
        """
        SELECT * FROM notifications
        WHERE packageName = :packageName
        ORDER BY createdAt DESC
        """
    )
    fun getNotificationsByPackage(packageName: String): Flow<List<NotificationEntity>>

    // ---- Sincronização ----

    /** Registros que ainda precisam ser enviados: PENDING ou FAILED. */
    @Query(
        """
        SELECT * FROM notifications
        WHERE syncState IN ('PENDING', 'FAILED')
        ORDER BY createdAt ASC
        """
    )
    suspend fun getPendingForSync(): List<NotificationEntity>

    /** Fluxo observável da quantidade de registros pendentes (PENDING + FAILED). */
    @Query("SELECT COUNT(*) FROM notifications WHERE syncState IN ('PENDING', 'FAILED')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE syncState IN ('PENDING', 'FAILED')")
    suspend fun getPendingCount(): Int

    /**
     * Registra o INÍCIO de uma tentativa de envio: incrementa attemptCount e
     * marca lastAttemptAt. Deve ser chamado imediatamente antes da chamada de
     * rede. É o único ponto que incrementa attemptCount, garantindo a semântica
     * de "tentativas iniciadas" sem contagem duplicada.
     */
    @Query(
        """
        UPDATE notifications
        SET attemptCount = attemptCount + 1,
            lastAttemptAt = :attemptAt
        WHERE id = :id
        """
    )
    suspend fun registerAttempt(id: Long, attemptAt: Long)

    /**
     * Marca um registro como sincronizado após confirmação do servidor.
     * Não altera attemptCount (já contabilizado em registerAttempt).
     */
    @Query(
        """
        UPDATE notifications
        SET syncState = 'SYNCED',
            lastAttemptAt = :attemptAt,
            lastError = NULL
        WHERE id = :id
        """
    )
    suspend fun markSynced(id: Long, attemptAt: Long)

    /**
     * Registra o resultado malsucedido de uma tentativa, mantendo o registro
     * pendente. Não altera attemptCount (já contabilizado em registerAttempt).
     */
    @Query(
        """
        UPDATE notifications
        SET syncState = 'FAILED',
            lastAttemptAt = :attemptAt,
            lastError = :error
        WHERE id = :id
        """
    )
    suspend fun markFailed(id: Long, attemptAt: Long, error: String)

    /** Reenfileira registros que falharam, voltando-os para PENDING (reenvio manual). */
    @Query("UPDATE notifications SET syncState = 'PENDING' WHERE syncState = 'FAILED'")
    suspend fun requeueFailed(): Int

    /**
     * Exclusão SEGURA: remove apenas o histórico já sincronizado (SYNCED).
     * Nunca apaga registros PENDING ou FAILED, preservando o que ainda não
     * foi confirmado pelo servidor. Retorna a quantidade de linhas removidas.
     */
    @Query("DELETE FROM notifications WHERE syncState = 'SYNCED'")
    suspend fun deleteSynced(): Int

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
}

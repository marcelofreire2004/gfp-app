package com.example.coletornotificacao

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Notificação capturada e persistida localmente.
 *
 * A persistência local é a fonte imediata de consulta da interface. O estado
 * de sincronização ([syncState]) acompanha o envio para a API sem jamais
 * apagar o registro local.
 *
 * [stableId] é um identificador estável derivado do conteúdo da captura
 * (pacote + título + mensagem + horário original). Ele não muda entre
 * tentativas de reenvio e serve como chave de idempotência do lado do
 * cliente. Ver [IdempotencyKey].
 */
@Entity(
    tableName = "notifications",
    indices = [Index(value = ["stableId"], unique = true)]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /**
     * Identificador estável/idempotente derivado do conteúdo da captura.
     *
     * defaultValue '' alinha a entidade à MIGRATION_1_2 (registros antigos
     * recebem um stableId determinístico no backfill da migração).
     */
    @ColumnInfo(name = "stableId", defaultValue = "") val stableId: String,

    val packageName: String,
    val title: String,
    val message: String,

    /** Horário original da captura (epoch millis). Nunca é sobrescrito pelo envio. */
    val createdAt: Long,

    /** Estado de sincronização atual. Persistido como texto do enum. */
    @ColumnInfo(name = "syncState", defaultValue = "PENDING")
    val syncState: SyncState = SyncState.PENDING,

    /** Epoch millis da última tentativa de envio, ou null se nunca tentou. */
    @ColumnInfo(name = "lastAttemptAt") val lastAttemptAt: Long? = null,

    /** Quantidade de tentativas de envio realmente iniciadas (sucesso ou falha). */
    @ColumnInfo(name = "attemptCount", defaultValue = "0") val attemptCount: Int = 0,

    /**
     * Mensagem técnica resumida do último erro (ex.: "HTTP 500", "timeout").
     * Nunca contém conteúdo financeiro ou pessoal da notificação.
     */
    @ColumnInfo(name = "lastError") val lastError: String? = null
)

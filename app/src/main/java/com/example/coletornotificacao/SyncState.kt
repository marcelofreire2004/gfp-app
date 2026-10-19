package com.example.coletornotificacao

/**
 * Estado de sincronização de uma notificação com a API remota.
 *
 * É independente de qualquer estado futuro de processamento financeiro:
 * uma notificação pode estar SYNCED sem ter sido interpretada como transação.
 */
enum class SyncState {
    /** Capturada e salva localmente, aguardando o primeiro envio. */
    PENDING,

    /** Envio confirmado pelo servidor (resposta de sucesso válida). */
    SYNCED,

    /** Última tentativa falhou; permanece aguardando nova tentativa. */
    FAILED
}

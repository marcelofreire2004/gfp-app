package com.example.coletornotificacao

/**
 * Corpo da requisição enviada para a API (POST /notificacoes).
 *
 * Mantém compatibilidade com o contrato atual do backend, que aceita
 * exatamente estes campos. Não é uma entidade Room.
 *
 * [stableId] é a chave de idempotência do cliente. O backend atual a ignora
 * (ver docs/api.md), mas ela é enviada para permitir que o servidor passe a
 * desduplicar no futuro sem nova mudança de contrato no cliente.
 */
data class NotificationRequest(
    val packageName: String,
    val title: String,
    val message: String,
    val createdAt: Long,
    val stableId: String
)

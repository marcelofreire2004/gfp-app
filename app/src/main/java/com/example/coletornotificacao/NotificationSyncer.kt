package com.example.coletornotificacao

import android.util.Log
import kotlinx.coroutines.CancellationException

/**
 * Componente de sincronização: lê os registros pendentes do banco (via
 * [NotificationRepository]) e tenta enviá-los para a API.
 *
 * Regras:
 *  - Só marca SYNCED após uma resposta de sucesso válida do servidor.
 *  - Em timeout, falta de conexão, erro HTTP ou indisponibilidade, mantém o
 *    registro pendente (FAILED) e grava um erro técnico resumido.
 *  - Nunca apaga registros locais.
 *  - Não bloqueia a thread principal (as chamadas são suspend; o chamador
 *    deve executar em Dispatchers.IO).
 *
 * Esta classe não depende do Android, o que a torna testável com uma
 * implementação simulada de [NotificationApi] (ex.: MockWebServer).
 */
class NotificationSyncer(
    private val repository: NotificationRepository,
    private val api: NotificationApi
) {

    data class Result(val success: Int, val failed: Int)

    /**
     * Tenta sincronizar todos os registros pendentes.
     * @return contagem de sucessos e falhas nesta execução.
     */
    suspend fun syncPending(): Result {
        val pending = repository.getPendingForSync()
        Log.i(TAG, "Iniciando sync: ${pending.size} registro(s) pendente(s)")

        var success = 0
        var failed = 0

        for (item in pending) {
            val now = System.currentTimeMillis()

            // Registra a tentativa ANTES da chamada de rede. Assim attemptCount
            // reflete "tentativas iniciadas" mesmo que o processo seja
            // interrompido durante o envio. markSynced/markFailed NÃO incrementam
            // mais, evitando contagem duplicada.
            repository.registerAttempt(item.id, now)

            try {
                val request = NotificationRequest(
                    packageName = item.packageName,
                    title = item.title,
                    message = item.message,
                    createdAt = item.createdAt, // horário ORIGINAL preservado
                    stableId = item.stableId
                )
                val response = api.enviarNotificacao(request)

                if (response.isSuccessful) {
                    repository.markSynced(item.id, now)
                    success++
                    Log.i(TAG, "Registro id=${item.id} sincronizado (HTTP ${response.code()})")
                } else {
                    val err = "HTTP ${response.code()}"
                    repository.markFailed(item.id, err, now)
                    failed++
                    Log.w(TAG, "Falha ao sincronizar id=${item.id}: $err")
                }
            } catch (ce: CancellationException) {
                // O cancelamento da coroutine (ex.: WorkManager parando o worker,
                // escopo encerrado) NÃO é uma falha de rede: repropaga para que o
                // cancelamento ocorra corretamente e o registro permaneça
                // pendente (não marcado como FAILED).
                Log.i(TAG, "Sync cancelado durante id=${item.id}; repropagando")
                throw ce
            } catch (e: Exception) {
                // Erro técnico CONTROLADO: apenas o tipo da exceção.
                // Não persiste e.message, que pode conter URL, host ou outros
                // dados arbitrários. Nunca inclui conteúdo da notificação.
                val err = classifyError(e)
                repository.markFailed(item.id, err, now)
                failed++
                Log.w(TAG, "Erro de rede ao sincronizar id=${item.id}: $err")
            }
        }

        Log.i(TAG, "Sync concluído: sucesso=$success, falha=$failed")
        return Result(success, failed)
    }

    /**
     * Classifica a exceção em um rótulo técnico controlado (sem mensagem
     * arbitrária). Mantém o valor curto e previsível para o campo lastError.
     */
    private fun classifyError(e: Throwable): String = when (e) {
        is java.net.SocketTimeoutException -> "TIMEOUT"
        is java.net.UnknownHostException -> "NO_CONNECTION"
        is java.net.ConnectException -> "CONNECT_FAILED"
        is java.io.IOException -> "IO_ERROR"
        else -> "ERROR:${e::class.simpleName ?: "Unknown"}"
    }

    companion object {
        private const val TAG = "GFP_SYNC"
    }
}

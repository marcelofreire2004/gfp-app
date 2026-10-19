package com.example.coletornotificacao

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Agendamento da sincronização via WorkManager.
 *
 * Estratégia contra condição de corrida (ver abaixo):
 *  - Restrição de rede: só executa quando há conectividade.
 *  - Backoff exponencial: novas tentativas automáticas após falha.
 *  - Trabalho único encadeado com [ExistingWorkPolicy.APPEND_OR_REPLACE]: se já
 *    houver uma execução em andamento, uma nova captura NÃO é descartada —
 *    uma segunda execução é enfileirada para rodar em seguida. Como o worker
 *    sempre reconsulta o banco (fonte de verdade), a notificação recém-salva
 *    é garantidamente processada nessa execução subsequente.
 *
 * Por que não KEEP: com KEEP, uma captura ocorrida enquanto o worker já lia o
 * banco poderia ser ignorada (a nova request é descartada e a notificação só
 * seria enviada em um disparo futuro incerto). APPEND_OR_REPLACE garante o
 * reprocessamento sem criar execuções paralelas simultâneas.
 *
 * Por que não cancelar a execução em andamento: APPEND encadeia em vez de
 * cancelar, evitando interromper uma requisição de rede já em voo.
 */
object SyncScheduler {

    const val UNIQUE_WORK_NAME = "gfp_sync_pending"

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private fun buildRequest(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

    /**
     * Agenda uma sincronização após uma captura.
     *
     * APPEND_OR_REPLACE: encadeia após o trabalho existente (sem paralelismo e
     * sem cancelar a rede em andamento), assegurando uma execução que vai
     * reconsultar o banco e enviar os registros ainda pendentes.
     */
    fun scheduleSync(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            buildRequest()
        )
    }

    /**
     * Sincronização manual disparada pelo usuário.
     *
     * Também usa APPEND_OR_REPLACE: reflete a intenção do usuário garantindo
     * uma execução subsequente, sem cancelar desnecessariamente uma requisição
     * de rede que já esteja em andamento. O WorkManager cuida de executar o
     * encadeamento assim que a execução atual terminar.
     */
    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            buildRequest()
        )
    }
}

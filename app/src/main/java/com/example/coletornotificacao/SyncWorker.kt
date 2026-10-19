package com.example.coletornotificacao

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker do WorkManager que consulta o banco local e envia os registros
 * pendentes. É retomável após o app ser fechado ou o dispositivo reiniciar,
 * pois lê o estado persistido no Room (não depende de nada em memória).
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getDatabase(applicationContext).notificationDao()
        val repository = NotificationRepository(dao)
        val syncer = NotificationSyncer(repository, ApiClient.api)

        try {
            val result = syncer.syncPending()
            Log.i(TAG, "Worker: sucesso=${result.success}, falha=${result.failed}")
            // Se restaram pendentes por falha, pede retry (backoff cuida do intervalo).
            if (result.failed > 0) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Worker falhou: ${e::class.simpleName}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "GFP_SYNC"
    }
}

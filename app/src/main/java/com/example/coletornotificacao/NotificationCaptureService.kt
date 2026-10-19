package com.example.coletornotificacao

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Captura notificações de apps bancários/financeiros.
 *
 * Fluxo: salva localmente no Room (fonte imediata da UI) e agenda a
 * sincronização via WorkManager. A captura NÃO faz chamadas de rede
 * diretamente nem é bloqueada por elas.
 *
 * Privacidade: NÃO registra em log o conteúdo das notificações (título,
 * mensagem ou extras). Logs trazem apenas informação técnica (pacote, estado).
 */
class NotificationCaptureService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repository by lazy {
        NotificationRepository(
            AppDatabase.getDatabase(applicationContext).notificationDao()
        )
    }

    private val expenseRepository by lazy {
        val db = AppDatabase.getDatabase(applicationContext)
        com.example.coletornotificacao.expense.ExpenseRepository(
            db.expenseDao(), db.cardDao(), db.merchantCategoryDao()
        )
    }

    private fun isBanco(packageName: String): Boolean {
        val bancos = listOf(
            "c6", "nubank", "nu.production", "inter", "itau", "santander",
            "bradesco", "bb.", "caixa", "safra", "xp",
            "mercadolibre.", "mercadopago.", "coletor"
        )
        return bancos.any { packageName.lowercase().contains(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isBanco(sbn.packageName)) return

        try {
            val extras = sbn.notification.extras

            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
            val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""

            val message = when {
                bigText.isNotBlank() -> bigText
                text.isNotBlank() -> text
                subText.isNotBlank() -> subText
                summaryText.isNotBlank() -> summaryText
                else -> "Sem Mensagem"
            }
            val finalTitle = if (title.isBlank()) "Sem Título" else title

            if (message == "Sem Mensagem" && finalTitle == "Sem Título") return

            // Horário ORIGINAL da captura: usa o postTime do sistema quando
            // disponível (>0); caso contrário, o relógio atual como alternativa.
            val capturedAt = if (sbn.postTime > 0L) sbn.postTime else System.currentTimeMillis()

            serviceScope.launch {
                val inserted = repository.insertCaptured(
                    packageName = sbn.packageName,
                    title = finalTitle,
                    message = message,
                    createdAt = capturedAt
                )
                Log.i(
                    TAG,
                    "Captura pacote=${sbn.packageName} novo=$inserted"
                )
                // Agenda o envio em background (não bloqueia a captura).
                SyncScheduler.scheduleSync(applicationContext)

                // Interpretação de despesa em etapa SEPARADA e ISOLADA: uma falha
                // aqui não pode impedir a captura nem as próximas notificações.
                // CancellationException é repropagada (não é tratada como erro).
                if (inserted) {
                    try {
                        val stableId = repository.stableIdFor(
                            sbn.packageName, finalTitle, message, capturedAt
                        )
                        expenseRepository.interpretarEPersistir(
                            sourceStableId = stableId,
                            packageName = sbn.packageName,
                            title = finalTitle,
                            message = message,
                            notificationCreatedAt = capturedAt
                        )
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        // Não loga conteúdo da notificação; apenas o tipo do erro.
                        Log.w(TAG, "Falha ao interpretar despesa: ${e::class.simpleName}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao capturar notificação: ${e::class.simpleName}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Sem ação; sem log de conteúdo.
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Listener conectado")
        // Ao conectar, tenta sincronizar o que estiver pendente.
        SyncScheduler.scheduleSync(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "Listener desconectado")
    }

    companion object {
        private const val TAG = "GFP_CAPTURE"
    }
}

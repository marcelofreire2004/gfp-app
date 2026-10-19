# Arquitetura

Estrutura em camadas usando componentes padrão do Android (Activities, Service), com Room para persistência, WorkManager para sincronização em background e Retrofit/OkHttp para rede. O `NotificationRepository` é a fonte única de acesso a dados e regras de estado de sincronização.

## Camadas

| Camada | Responsabilidade | Arquivos |
|--------|------------------|----------|
| Apresentação (UI) | Tela central, detalhe e listas | `MainActivity`, `PackageNotificationsActivity`, `PackageAdapter`, `NotificationAdapter`, layouts XML |
| Captura | Interceptação de notificações do sistema | `NotificationCaptureService` |
| Sincronização | Envio confiável em background | `SyncWorker`, `SyncScheduler`, `NotificationSyncer` |
| Dados / Persistência | Banco local e regras de estado | `AppDatabase`, `NotificationDao`, `NotificationEntity`, `NotificationRepository`, `Converters`, `SyncState`, `PackageCount`, `IdempotencyKey` |
| Rede / API | Comunicação com backend | `ApiClient`, `NotificationApi`, `NotificationRequest` |

## Diagrama de fluxo

```
┌──────────────────────┐
│  Sistema Android      │
│ (notificação postada) │
└───────────┬──────────┘
            │ onNotificationPosted()
            ▼
┌───────────────────────────────┐
│  NotificationCaptureService    │
│  - isBanco()? filtra pacotes   │
│  - extrai título/mensagem      │
│  - preserva horário original   │
└───────────────┬───────────────┘
                │ repository.insertCaptured() (PENDING, stableId)
                ▼
        ┌───────────────┐        agenda (APPEND_OR_REPLACE, requer rede)
        │ Room (local)  │◄──────────────────────────────┐
        │ notifications │                                │
        └───────┬───────┘                        ┌───────┴────────┐
                │ Flow                            │ SyncScheduler  │
                ▼                                 └───────┬────────┘
┌───────────────────────────────┐                        ▼
│  MainActivity (tela central)   │                ┌───────────────┐
│  - lista + estado de sync      │                │  SyncWorker    │
│  - contador de pendentes       │                │ (WorkManager)  │
│  - sincronizar agora           │                └───────┬────────┘
│  - limpar sincronizadas        │                        │ lê pendentes
└───────────────┬───────────────┘                        ▼
                │ clique no pacote               ┌─────────────────────┐
                ▼                                 │ NotificationSyncer  │
┌────────────────────────────────────┐           │ - registerAttempt   │
│  PackageNotificationsActivity       │           │ - POST /notificacoes│
│  notificationsByPackage()           │           │ - markSynced/Failed │
└────────────────────────────────────┘           └──────────┬──────────┘
                                                             ▼
                                                    ┌──────────────────┐
                                                    │ ApiClient (OkHttp│
                                                    │ + Retrofit/Gson) │
                                                    └──────────────────┘
```

## Fluxo de sincronização (detalhe)

1. A captura salva o registro como `PENDING` e chama `SyncScheduler.scheduleSync` (trabalho único `APPEND_OR_REPLACE`, com restrição de rede e backoff exponencial).
2. O `SyncWorker` (CoroutineWorker) roda em `Dispatchers.IO`, cria um `NotificationSyncer` e chama `syncPending()`.
3. Para cada pendente, o syncer **registra a tentativa antes da chamada de rede** (`registerAttempt`, incrementa `attemptCount`), envia o `NotificationRequest` e:
   - resposta de sucesso → `markSynced` (só então vira `SYNCED`);
   - erro HTTP/rede → `markFailed` com rótulo técnico controlado;
   - `CancellationException` → repropaga (não vira falha; registro permanece pendente).
4. Se restaram falhas, o worker retorna `Result.retry()` (novas tentativas com backoff). O estado persistido no Room é a fonte de verdade — o processo é retomável após fechar o app ou reiniciar.

## Padrões adotados

- **Repository como fonte única**: Activities, serviço e worker acessam dados via `NotificationRepository`.
- **Singleton do banco**: `AppDatabase.getDatabase()` com double-checked locking (`@Volatile` + `synchronized`) e `addMigrations(MIGRATION_1_2)`.
- **Singleton de rede**: `ApiClient` é um `object` com OkHttp (timeouts) + Retrofit por `lazy`.
- **Reatividade**: consultas do DAO retornam `Flow`, coletadas em `lifecycleScope`.
- **Listas**: `ListAdapter` + `DiffUtil`.
- **Idempotência do cliente**: `IdempotencyKey` (SHA-256) + índice único em `stableId`.
- **Separação captura/rede**: a captura só persiste e agenda; a rede roda no WorkManager, fora da thread principal.

# Componentes

Descrição de cada arquivo-fonte em `app/src/main/java/com/example/coletornotificacao/`.

## UI / Apresentação

### `MainActivity.kt`
Tela central de notificações.
- Verifica em `onCreate` se o acesso a notificações está habilitado (`isNotificationServiceEnabled`) e, se não, abre as configurações do sistema.
- Solicita `POST_NOTIFICATIONS` em Android 13+ (`checkPostNotificationPermission`).
- Usa o `NotificationRepository` como fonte de dados.
- `setupRecyclerView`: lista todas as notificações com `NotificationAdapter` (mostrando o estado de sincronização).
- `observeNotifications` / `observePendingCount`: observam `Flow`s do repositório (lista e contador de pendentes).
- `setupSyncButton` ("Sincronizar agora"): reenfileira falhas (`requeueFailed`) e dispara `SyncScheduler.syncNow`.
- `setupClearButton` ("Limpar sincronizadas"): exclusão **segura** — diálogo explícito e `deleteSynced()`, que remove apenas registros `SYNCED` e preserva os pendentes.
- `setupTestButton`: gera uma notificação local de teste.
- `createNotificationChannel`: cria o canal em Android 8+.

Layout: `res/layout/layout.xml`.

### `PackageNotificationsActivity.kt`
Tela de detalhe por aplicativo (navegação preservada). Recebe `PACKAGE_NAME` por `Intent`, usa o repositório (`notificationsByPackage`) e lista via `NotificationAdapter`.

Layout: `res/layout/activity_package_notifications.xml`.

### `PackageAdapter.kt`
`ListAdapter<PackageCount, ...>` com `DiffUtil`. Exibe pacote e total de notificações. Classe preservada (a navegação por pacote continua disponível via `PackageNotificationsActivity`).

Layout do item: `res/layout/item_package.xml`.

### `NotificationAdapter.kt`
`ListAdapter<NotificationEntity, ...>` com `DiffUtil`. Exibe pacote, título, prévia da mensagem (máx. 2 linhas), data formatada (`dd/MM/yyyy HH:mm:ss`), o **estado de sincronização** (✓ Sincronizado / ⏳ Pendente / ⚠ Falha com nº de tentativas) e, quando `FAILED`, o último erro técnico.

Layout do item: `res/layout/item_notification.xml`.

## Captura

### `NotificationCaptureService.kt`
Estende `NotificationListenerService`.
- `isBanco(packageName)`: `true` se o pacote contém alguma substring da lista (c6, nubank, nu.production, inter, itau, santander, bradesco, bb., caixa, safra, xp, mercadolibre., mercadopago., coletor).
- `onNotificationPosted(sbn)`: ignora pacotes fora do filtro; extrai o conteúdo priorizando `EXTRA_BIG_TEXT` → `EXTRA_TEXT` → `EXTRA_SUB_TEXT` → `EXTRA_SUMMARY_TEXT`; preserva o **horário original** (`sbn.postTime`, com fallback para o relógio atual); chama `repository.insertCaptured(...)` (estado `PENDING`) e agenda `SyncScheduler.scheduleSync`. **Não faz chamadas de rede.**
- `onListenerConnected`: agenda uma sincronização ao reconectar.
- **Privacidade**: não registra conteúdo de notificação no log; apenas pacote e se é novo registro.

Usa `CoroutineScope(SupervisorJob() + Dispatchers.IO)`.

## Sincronização

### `NotificationSyncer.kt`
Classe pura (sem dependência de Android, testável com API simulada). `syncPending()` lê os pendentes do repositório e, para cada um:
- `registerAttempt` **antes** da chamada de rede (incrementa `attemptCount`);
- envia o `NotificationRequest` preservando o `createdAt` original e o `stableId`;
- sucesso → `markSynced`; erro → `markFailed` com rótulo de `classifyError` (`TIMEOUT`, `NO_CONNECTION`, `CONNECT_FAILED`, `IO_ERROR`, `HTTP <código>`, `ERROR:<Tipo>`);
- `CancellationException` é repropagada (não vira falha).

### `SyncWorker.kt`
`CoroutineWorker` que monta repositório + syncer e executa `syncPending()` em `Dispatchers.IO`. Retorna `Result.retry()` se houve falha, `Result.success()` caso contrário. Lê o estado do Room (retomável).

### `SyncScheduler.kt`
Agendamento via WorkManager com trabalho único `UNIQUE_WORK_NAME`:
- `scheduleSync` (após captura) e `syncNow` (manual) usam `ExistingWorkPolicy.APPEND_OR_REPLACE`, encadeando uma nova execução sem descartar capturas nem cancelar uma requisição em andamento;
- restrição `NetworkType.CONNECTED` e `BackoffPolicy.EXPONENTIAL` (30s).

## Dados / Persistência

### `AppDatabase.kt`
Room `@Database` **versão 2**, `@TypeConverters(Converters)`, `exportSchema = true`. Singleton com `addMigrations(MIGRATION_1_2)`. Arquivo `notification_db`. A migração 1→2 é aditiva e preserva os dados (ver [Banco de Dados](banco-de-dados.md)).

### `NotificationDao.kt`
- `insert` (`OnConflictStrategy.IGNORE` no `stableId`) — não duplica localmente.
- `getAllNotifications`, `getPackageCounts`, `getNotificationsByPackage` (`Flow`).
- `getPendingForSync` (PENDING+FAILED), `observePendingCount`/`getPendingCount`.
- `registerAttempt` (único ponto que incrementa `attemptCount` + `lastAttemptAt`).
- `markSynced` / `markFailed` (atualizam estado e erro, sem mexer no contador).
- `requeueFailed` (FAILED → PENDING).
- `deleteSynced` (apaga só `SYNCED`) e `deleteAll` (uso interno/teste).

### `NotificationEntity.kt`
Tabela `notifications`: `id` (PK autoincremento), `stableId` (índice único, `defaultValue ''`), `packageName`, `title`, `message`, `createdAt`, `syncState` (`defaultValue 'PENDING'`), `lastAttemptAt?`, `attemptCount` (`defaultValue '0'`), `lastError?`.

### `SyncState.kt`
Enum `PENDING` / `SYNCED` / `FAILED`.

### `Converters.kt`
`TypeConverter` do enum `SyncState` ↔ `String`.

### `IdempotencyKey.kt`
Gera o `stableId` = SHA-256 de `packageName|title|message|createdAt` (horário original). Estável entre reenvios; não usa o horário atual.

### `PackageCount.kt`
Projeção da agregação: `packageName` + `totalMessages`.

### `NotificationRepository.kt`
Fonte única de dados e regras de sincronização: `insertCaptured` (calcula `stableId`, estado `PENDING`), consultas, `registerAttempt`, `markSynced`, `markFailed`, `requeueFailed`, `observePendingCount`, `deleteSynced`, `deleteAll`.

## Rede / API

### `ApiClient.kt`
`object` singleton: OkHttp com timeouts (connect 15s, read/write 30s) + Retrofit/Gson. `BASE_URL = "https://node-zero-qk0s.onrender.com/"`. Sem credenciais no código.

### `NotificationApi.kt`
Interface Retrofit. `POST notificacoes`, corpo `NotificationRequest`, retorno `Response<Unit>`.

### `NotificationRequest.kt`
DTO de rede (sem anotações Room): `packageName`, `title`, `message`, `createdAt`, `stableId`. Compatível com o contrato atual; `stableId` é aditivo (ver [API](api.md)).

# Banco de Dados

O app usa **Room** (abstração sobre SQLite) para persistência local. É a fonte imediata de consulta da interface.

## Configuração

- Classe: `AppDatabase` (`@Database`, `version = 2`, `exportSchema = true`, `@TypeConverters(Converters)`).
- Nome do arquivo: `notification_db`.
- Instância única via `AppDatabase.getDatabase(context)` (double-checked locking) com `addMigrations(MIGRATION_1_2)`.
- Esquemas exportados em `app/schemas/com.example.coletornotificacao.AppDatabase/` (`1.json`, `2.json`), usados pelo teste de migração.

## Tabela: `notifications`

Definida por `NotificationEntity`.

| Coluna | Tipo | Default | Observações |
|--------|------|---------|-------------|
| `id` | `INTEGER` | — | Chave primária, autoincremento |
| `stableId` | `TEXT` | `''` | Identificador estável/idempotente (SHA-256). **Índice único** |
| `packageName` | `TEXT` | — | Pacote do app de origem |
| `title` | `TEXT` | — | Título (ou "Sem Título") |
| `message` | `TEXT` | — | Conteúdo (ou "Sem Mensagem") |
| `createdAt` | `INTEGER` | — | Horário **original** da captura (epoch millis) |
| `syncState` | `TEXT` | `'PENDING'` | `PENDING` / `SYNCED` / `FAILED` |
| `lastAttemptAt` | `INTEGER` | — | Epoch millis da última tentativa (nullable) |
| `attemptCount` | `INTEGER` | `0` | Tentativas de envio **iniciadas** |
| `lastError` | `TEXT` | — | Rótulo técnico do último erro (nullable) |

Os `defaultValue` declarados na entidade correspondem exatamente aos usados na migração e ao schema exportado (`2.json`).

## Consultas (DAO)

### Leitura (retornam `Flow`)
```sql
SELECT * FROM notifications ORDER BY createdAt DESC                 -- getAllNotifications
SELECT packageName, COUNT(*) AS totalMessages FROM notifications
  GROUP BY packageName ORDER BY totalMessages DESC                 -- getPackageCounts
SELECT * FROM notifications WHERE packageName = :p
  ORDER BY createdAt DESC                                          -- getNotificationsByPackage
SELECT COUNT(*) FROM notifications
  WHERE syncState IN ('PENDING','FAILED')                          -- observePendingCount
```

### Sincronização
```sql
-- getPendingForSync (suspend)
SELECT * FROM notifications WHERE syncState IN ('PENDING','FAILED') ORDER BY createdAt ASC

-- registerAttempt: único ponto que incrementa o contador, ANTES da chamada de rede
UPDATE notifications SET attemptCount = attemptCount + 1, lastAttemptAt = :t WHERE id = :id

-- markSynced: só após confirmação do servidor (não mexe no attemptCount)
UPDATE notifications SET syncState = 'SYNCED', lastAttemptAt = :t, lastError = NULL WHERE id = :id

-- markFailed: mantém pendente, grava erro técnico (não mexe no attemptCount)
UPDATE notifications SET syncState = 'FAILED', lastAttemptAt = :t, lastError = :e WHERE id = :id

-- requeueFailed: reenvio manual
UPDATE notifications SET syncState = 'PENDING' WHERE syncState = 'FAILED'
```

### Inserção e exclusão
```sql
-- insert com OnConflictStrategy.IGNORE no índice único stableId (não duplica)
-- deleteSynced: exclusão SEGURA, preserva pendentes
DELETE FROM notifications WHERE syncState = 'SYNCED'
-- deleteAll: uso interno/teste
DELETE FROM notifications
```

## `attemptCount` — semântica

Representa **tentativas iniciadas**, não apenas falhas. É incrementado exclusivamente em `registerAttempt`, chamado **antes** da chamada de rede. Assim, mesmo que o processo seja interrompido durante o envio, a tentativa é contabilizada e o registro permanece pendente para reenvio. `markSynced`/`markFailed` não alteram o contador (evita duplicidade).

## Migração v1 → v2 (`MIGRATION_1_2`)

Aditiva e não destrutiva (sem `fallbackToDestructiveMigration`):

```sql
ALTER TABLE notifications ADD COLUMN stableId TEXT NOT NULL DEFAULT '';
ALTER TABLE notifications ADD COLUMN syncState TEXT NOT NULL DEFAULT 'PENDING';
ALTER TABLE notifications ADD COLUMN lastAttemptAt INTEGER;
ALTER TABLE notifications ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0;
ALTER TABLE notifications ADD COLUMN lastError TEXT;
UPDATE notifications SET stableId = 'legacy-' || id WHERE stableId = '';
CREATE UNIQUE INDEX IF NOT EXISTS index_notifications_stableId ON notifications(stableId);
```

- Preserva `packageName`, `title`, `message`, `createdAt`.
- Registros antigos ficam `PENDING` e recebem um `stableId` determinístico e único (`legacy-<id>`); o hash de conteúdo passa a valer para capturas novas.
- Validada pelo teste instrumentado `MigrationTest` (ver [Configuração e Build](configuracao-build.md)).

## Reatividade

As consultas de leitura retornam `Flow`, atualizando a UI automaticamente a cada mudança na tabela. As telas coletam esses fluxos em `lifecycleScope`.

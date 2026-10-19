# Integração com API

# Integração com API

O envio das notificações para o backend usa **Retrofit 2** com conversor **Gson** sobre um cliente **OkHttp** com timeouts.

## Configuração (`ApiClient`)

- `BASE_URL`: `https://node-zero-qk0s.onrender.com/`
- Cliente: OkHttp (connect 15s, read/write 30s).
- Conversor: `GsonConverterFactory`.
- Instância `api` criada via `lazy`. Sem credenciais no código.

## Endpoint (`NotificationApi`)

| Método | Caminho | Corpo | Resposta |
|--------|---------|-------|----------|
| `POST` | `notificacoes` | `NotificationRequest` (JSON) | `Response<Unit>` — `201` ao criar, `200` em reenvio idempotente |

URL completa resultante: `https://node-zero-qk0s.onrender.com/notificacoes`

## Corpo da requisição (`NotificationRequest`)

```json
{
  "packageName": "com.nu.production",
  "title": "Compra aprovada",
  "message": "Compra no valor de R$ 50,00 aprovada",
  "createdAt": 1760000000000,
  "stableId": "a1b2c3...（SHA-256）"
}
```

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `packageName` | string | Pacote do app de origem |
| `title` | string | Título da notificação |
| `message` | string | Conteúdo da notificação |
| `createdAt` | number | Horário **original** da captura em milissegundos |
| `stableId` | string | Chave de idempotência (SHA-256). Persistida e desduplicada pelo backend; opcional (clientes antigos sem o campo continuam aceitos) |

## Quando é chamado

O POST **não** é mais disparado pelo serviço de captura. A captura apenas persiste a notificação (`PENDING`) e agenda o WorkManager. O envio é feito pelo `NotificationSyncer`, executado dentro do `SyncWorker` em `Dispatchers.IO`, que lê os pendentes do Room e envia cada um:

- registra a tentativa antes da chamada de rede (`registerAttempt`);
- envia o `NotificationRequest` preservando o `createdAt` original e o `stableId`;
- marca `SYNCED` apenas com resposta de sucesso; em falha, mantém o registro pendente (`FAILED`) com um rótulo técnico em `lastError`;
- `CancellationException` é repropagada (o cancelamento não vira falha).

Falhas levam a novas tentativas automáticas (backoff do WorkManager) e o processo é retomável após o app fechar ou o dispositivo reiniciar.

## Configuração de rede e privacidade

- A comunicação usa **HTTPS** (`BASE_URL` em domínio público). O tráfego em texto puro (cleartext/HTTP) está **desabilitado**: `network_security_config.xml` usa `cleartextTrafficPermitted="false"` e o `usesCleartextTraffic` foi removido do manifest. Nenhuma exceção insegura é introduzida para testes.
- O banco local `notification_db` guarda textos de notificações financeiras e é **excluído dos backups** (Auto Backup em Android ≤11 via `backup_rules.xml` e Data Extraction em Android 12+ via `data_extraction_rules.xml`), evitando que esse conteúdo sensível vá para a nuvem ou transferência entre aparelhos.
- O campo `lastError` persiste apenas rótulos técnicos controlados (`TIMEOUT`, `NO_CONNECTION`, `CONNECT_FAILED`, `IO_ERROR`, `HTTP <código>`), nunca a mensagem bruta da exceção nem conteúdo da notificação.

## Idempotência (cliente + servidor)

A idempotência agora é garantida de ponta a ponta:

**No cliente:** o `stableId` é um hash SHA-256 estável derivado de
`packageName + title + message + createdAt` (horário original). A mesma
notificação sempre gera a mesma chave entre tentativas de reenvio, e o índice
único local evita duplicação no Room.

**No servidor (FinMentor):** o backend passou a:

1. Persistir o `stableId` recebido no POST (coluna `stable_id`).
2. Aplicar um índice único parcial `(id_usuario, package_name, stable_id)` com
   `WHERE stable_id IS NOT NULL`.
3. Fazer `INSERT ... ON CONFLICT DO NOTHING` no `POST /notificacoes`:
   - primeiro envio → `201` com o registro criado;
   - reenvio do mesmo `stableId` → `200` com o registro existente, **sem** criar duplicata.

Esse comportamento é coberto por testes de integração no backend
(`tests/notificacoes.test.js`): reenviar o mesmo `stableId` mantém um único
registro, e `stableId`s distintos não colidem.

**Compatibilidade:** o envio do `stableId` é aditivo. Clientes antigos que não o
enviam continuam funcionando (o servidor sempre insere nesse caso), então o
contrato existente não quebra.

Resultado: reenvios por falha de rede ou ausência de confirmação não geram mais
duplicatas no servidor, e o cliente nunca perde nem duplica o registro local.

## Pontos de atenção

- A `BASE_URL` está fixada no código (hardcoded). Para múltiplos ambientes, considere `buildConfigField` ou variantes de build.
- Não há autenticação (token/cabeçalho) no envio.

Veja mais em [Melhorias Sugeridas](melhorias.md).

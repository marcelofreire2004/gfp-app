# Melhorias e Dívida Técnica

Situação atual dos pontos levantados nas revisões. Vários já foram resolvidos na implementação do módulo de captura do GFP.

## Já resolvidos

- **Repository como fonte única.** `NotificationRepository` agora centraliza persistência e regras de sincronização; Activities, serviço e worker o utilizam.
- **`NotificationRequest` sem anotações Room.** Virou `data class` simples, alinhada ao contrato da API, com `stableId` aditivo.
- **Persistência e rede desacopladas.** A captura só persiste e agenda; o envio roda no `SyncWorker` (WorkManager), fora da thread principal.
- **Logs sem conteúdo sensível.** O serviço não registra título/mensagem/extras; `lastError` guarda apenas rótulos técnicos controlados.
- **Retry/fila offline.** WorkManager com restrição de rede, backoff exponencial e leitura dos pendentes no Room (retomável). Registros nunca são perdidos.
- **Cleartext desabilitado.** HTTPS para produção; `cleartextTrafficPermitted="false"`.
- **Backup de dados sensíveis.** `notification_db` excluído de cloud-backup e device-transfer.
- **Migração segura com testes.** v1→v2 aditiva, preservando dados, validada por teste instrumentado.
- **Idempotência do cliente.** `stableId` estável + índice único; sem duplicação local.
- **Idempotência do servidor.** O backend FinMentor agora persiste o `stableId`, aplica índice único parcial `(id_usuario, package_name, stable_id)` e faz `INSERT ... ON CONFLICT DO NOTHING`. Reenvio do mesmo `stableId` responde 200 com o registro existente (em vez de 201) e **não** cria duplicata. Confirmado por testes de integração no backend (`tests/notificacoes.test.js`). Clientes antigos sem `stableId` continuam aceitos (comportamento de sempre inserir).
- **Exclusão segura.** O botão apaga só `SYNCED`; pendentes são preservados.
- **Cobertura de testes.** Testes de repositório, syncer (com API simulada), migração, cancelamento e exclusão segura.

## Em aberto

1. **`BASE_URL` hardcoded.** Usar `buildConfigField`/flavors para separar dev/produção.
2. **Sem autenticação (lado cliente).** O POST ainda não envia token/cabeçalho. O backend já **suporta** token de dispositivo no `POST /notificacoes` (via `Authorization: Bearer`/`X-Device-Token`, com cadastro em `/dispositivos`), mas mantém um **fallback público** nesta fase (env `COLLECTOR_PUBLIC_FALLBACK`, ligado por padrão) porque o coletor ainda não autentica. Falta o cliente passar a enviar o token; quando o fallback for desligado, a ingestão vira multiusuário segura. Coordenar em release conjunto.
4. **Filtro por substring.** `isBanco` usa `contains`; substrings curtas (`"xp"`, `"bb."`) podem casar com pacotes não relacionados. Considerar lista de pacotes exatos.
5. **Dependências Compose não usadas.** O `libs.versions.toml` declara Compose/`kotlin-compose` sem uso. Remover reduz ruído e tempo de build.
6. **`isMinifyEnabled = false` no release.** Habilitar R8/ProGuard reduz o APK e ofusca o código.
7. **`attemptCount` conta tentativas iniciadas.** Por design, uma interrupção após `registerAttempt` e antes da resposta conta a tentativa mas mantém o registro pendente — o contador pode exceder o número de respostas efetivamente recebidas. É a semântica desejada (não perder dados).
8. **Política de retenção/LGPD.** Definir retenção do conteúdo original e consentimento explícito antes de evoluir o módulo financeiro.

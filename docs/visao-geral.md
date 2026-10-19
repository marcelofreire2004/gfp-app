# Visão Geral

## Propósito

O **Coletor de Notificações** é o módulo de captura do Gerente Financeiro e Patrimonial (GFP). Ele intercepta notificações da barra de status, com foco em aplicativos bancários e de pagamento (C6, Nubank, Inter, Itaú, Santander, Bradesco, Banco do Brasil, Caixa, Safra, XP, Mercado Livre e Mercado Pago).

Para cada notificação relevante, o app:

1. Extrai título e mensagem, preservando o horário original da captura.
2. Salva a notificação no banco local (Room/SQLite) com estado `PENDING` e um identificador estável (`stableId`).
3. Agenda uma sincronização em background (WorkManager) que envia o registro à API e atualiza seu estado.

O envio **não** é feito dentro do serviço de captura nem na interface: a captura apenas persiste e agenda. Isso garante que a indisponibilidade de rede não bloqueie a captura nem cause perda de dados.

## Funcionalidades

- **Captura automática** via `NotificationListenerService`.
- **Filtro por apps financeiros** por correspondência de substring no nome do pacote.
- **Persistência local com estado de sincronização** (`PENDING`, `SYNCED`, `FAILED`), número de tentativas e último erro técnico.
- **Sincronização confiável** com WorkManager: executa em background, respeita conectividade, tenta novamente com backoff e é retomável após o app fechar ou o dispositivo reiniciar.
- **Idempotência do lado cliente** via `stableId` (hash estável), evitando duplicação local e mantendo a mesma identidade entre reenvios.
- **Tela central** (`MainActivity`) com a lista de notificações, estado de sincronização, prévia do conteúdo, data da captura, contador de pendentes e botão de sincronização manual.
- **Detalhe por aplicativo** preservado (`PackageNotificationsActivity`).
- **Botão de teste** que gera uma notificação local de exemplo.
- **Exclusão segura**: o botão de limpar apaga apenas o histórico já sincronizado (`SYNCED`); registros pendentes nunca são apagados.

## Fluxo principal

1. Ao abrir, a `MainActivity` verifica se o acesso às notificações está habilitado; se não, abre as configurações do sistema.
2. Em Android 13+ (API 33), solicita `POST_NOTIFICATIONS`.
3. O `NotificationCaptureService` recebe cada notificação via `onNotificationPosted`. Pacotes fora do filtro são ignorados.
4. Notificações válidas são persistidas no Room como `PENDING` (sem duplicar, graças ao `stableId`) e uma sincronização é agendada.
5. O `SyncWorker` lê os pendentes, envia à API e marca `SYNCED` em caso de sucesso, ou mantém o registro pendente (`FAILED`) em caso de falha.
6. A tela central observa o banco via `Flow` e reflete o estado em tempo real.

## Estados de sincronização

| Estado | Significado |
|--------|-------------|
| `PENDING` | Capturada e salva, aguardando o primeiro envio ou reenfileirada. |
| `SYNCED` | Envio confirmado pelo servidor (resposta de sucesso válida). |
| `FAILED` | Última tentativa falhou; permanece elegível para nova tentativa. |

O estado de sincronização é independente de qualquer processamento financeiro futuro — uma notificação pode estar `SYNCED` sem ter sido interpretada como transação.

## Permissões necessárias

| Permissão | Uso |
|-----------|-----|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Permite ao serviço escutar notificações (concedida pelo usuário nas configurações do sistema). |
| `POST_NOTIFICATIONS` | Necessária em Android 13+ para exibir a notificação de teste. |
| `INTERNET` | Envio das notificações para a API. |

## Privacidade

- Os logs **não** registram o conteúdo das notificações (título/mensagem/extras). Apenas informação técnica (pacote, estado, código/tipo de erro).
- O banco local `notification_db` é **excluído dos backups** do Android (cloud e device-transfer), evitando que conteúdo sensível vá para a nuvem.
- O campo de erro (`lastError`) guarda apenas rótulos técnicos controlados, nunca a mensagem bruta da exceção.
- A comunicação é por HTTPS; tráfego em texto puro (cleartext) está desabilitado.

Mesmo com essas medidas, qualquer uso em produção deve considerar a LGPD e o consentimento do usuário. Ver [Melhorias Sugeridas](melhorias.md).

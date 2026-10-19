# Coletor de Notificações — Módulo de Captura do GFP

Aplicativo Android (Kotlin) que captura notificações de aplicativos bancários/financeiros, persiste localmente com estado de sincronização e as envia de forma confiável para uma API remota via WorkManager. É o módulo de captura do **Gerente Financeiro e Patrimonial (GFP)**.

A tela central lista as notificações capturadas com o respectivo estado de sincronização e permite sincronização manual. A persistência local é a fonte imediata da interface — a falta de internet não impede a captura nem causa perda de registros.

## Índice da documentação

- [Visão Geral](visao-geral.md) — propósito, funcionalidades e fluxo principal
- [Arquitetura](arquitetura.md) — camadas, componentes e diagrama de fluxo
- [Componentes](componentes.md) — descrição detalhada de cada classe/arquivo
- [Banco de Dados](banco-de-dados.md) — modelo Room, estados e migração
- [Integração com API](api.md) — contrato, sincronização e idempotência
- [Despesas (Etapa 2)](despesas.md) — interpretação de notificações e registro automático de despesas
- [Configuração e Build](configuracao-build.md) — requisitos, build, permissões e privacidade
- [Melhorias Sugeridas](melhorias.md) — pontos de atenção e dívida técnica

## Resumo rápido

| Item | Valor |
|------|-------|
| Plataforma | Android (nativo) |
| Linguagem | Kotlin |
| `applicationId` | `com.example.coletornotificacao` |
| `minSdk` / `targetSdk` / `compileSdk` | 24 / 36 / 36 |
| Persistência | Room (SQLite), versão do schema **3** (v3 adiciona o módulo de despesas) |
| Sincronização | WorkManager + Retrofit + Gson + OkHttp |
| UI | Views XML + RecyclerView |
| Versão | 1.0 (versionCode 1) |

## Como funciona em uma frase

Um `NotificationListenerService` escuta as notificações do sistema, filtra as de apps bancários, salva no Room (estado `PENDING`) e agenda um `WorkManager` que lê os pendentes e envia à API, marcando `SYNCED` apenas com confirmação do servidor; a `MainActivity` é a tela central que mostra as notificações, o estado de sincronização e o contador de pendentes.

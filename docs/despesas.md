# Módulo de Despesas (Etapa 2) — interpretação e registro automático

Primeira versão do módulo que interpreta notificações de cartão de crédito e
registra despesas automaticamente quando os critérios objetivos são atendidos,
encaminhando para revisão nos demais casos. Preserva integralmente o coletor v4
(captura, fila offline, sincronização, idempotência da notificação).

## Visão geral do fluxo

```
NotificationCaptureService (captura)
  └─ NotificationRepository.insertCaptured(...)  → persiste a NOTIFICAÇÃO (Room)
  └─ [etapa isolada] ExpenseRepository.interpretarEPersistir(...)
         ├─ NotificationParser.parse(...)        → tipo + campos + motivos
         ├─ CardMatcher.resolve(...)             → cartão (ou NONE/AMBIGUOUS)
         ├─ CategorySuggester.suggest(...)       → categoria (aprendido > regra)
         └─ ExpenseDecider.build(...)            → CONFIRMED (auto) ou NEEDS_REVIEW
```

A interpretação roda **depois** da captura, em etapa separada e isolada por
`try/catch` (com repropagação de `CancellationException`). Uma falha ao
interpretar **não** impede a captura nem as próximas notificações (req. técnico).
A notificação original **nunca** é apagada (RF01).

## Componentes (todos em `app/.../expense/`)

| Arquivo | Responsabilidade |
|---|---|
| `ExpenseType.kt` / `ExpenseStatus.kt` | Enums de natureza e estado do lançamento |
| `NotificationParser.kt` | Interpretação local pura (regras determinísticas, sem IA/rede) |
| `ExpenseDecider.kt` | Decisão objetiva de auto-registro vs revisão |
| `CardMatcher.kt` | Resolução de cartão (last4 → keyword → instituição) |
| `CategorySuggester.kt` | Sugestão determinística de categoria |
| `ExpenseRepository.kt` | Orquestra interpretar→decidir→persistir; confirmar/corrigir/rejeitar |
| `CardRepository.kt` / `CategoryRepository.kt` | CRUD de cartões / categorias |
| `*Entity.kt` + `*Dao.kt` | Persistência Room |
| `ExpensesActivity` / `ExpenseDetailActivity` / `CardsActivity` | UI |

## Natureza da operação (RF03)

`PURCHASE_APPROVED`, `PURCHASE_DECLINED`, `REFUND`, `CANCELLATION`,
`INVOICE_PAYMENT`, `INVOICE_NOTICE`, `UNRELATED`, `UNKNOWN`.

- Compra **recusada** não gera despesa confirmada.
- **Pagamento de fatura** não é despesa de consumo (não entra nos totais).
- **Estorno/cancelamento** são eventos distintos; o vínculo com a compra
  original **não** é inventado — fica como motivo de revisão quando os dados são
  insuficientes.
- Natureza indeterminada (`UNKNOWN`) → revisão.

## Critérios objetivos de registro automático (RF05)

Uma despesa é **CONFIRMED automaticamente** apenas quando, de forma verificável:

1. a notificação é **compra aprovada**;
2. o **valor** foi identificado e validado (`> 0`, em centavos);
3. o **cartão** foi identificado **sem ambiguidade**;
4. não há conflito relevante nos dados.

Estabelecimento e categoria **podem** ficar pendentes sem bloquear o registro.
Qualquer outro caso vira **NEEDS_REVIEW**, com os motivos registrados em
`reviewReasons`. Não há "percentual de confiança" — a decisão é por regras.

## Anti-duplicação em duas camadas (seção 6 do escopo)

### Camada local (implementada e testada)

- Cada despesa referencia a notificação de origem por `sourceStableId`, que tem
  **índice único**. `ExpenseDao.insert` usa `OnConflictStrategy.IGNORE`, e o
  `ExpenseRepository` ainda faz um short-circuit por `existsForSource(...)`.
- Reinterpretar a mesma notificação, reabrir o app ou confirmar um item **não**
  cria outra despesa. Confirmar/corrigir **atualizam** o mesmo registro (RF09).
- A chave de origem é o `stableId` da notificação (hash estável de
  pacote+título+mensagem+horário original), gerado num único lugar
  (`IdempotencyKey`, exposto via `NotificationRepository.stableIdFor`).
- **Não** se usa (estabelecimento + valor + data) como chave: compras legítimas
  podem coincidir nesses campos.

### Camada de servidor (das NOTIFICAÇÕES — já disponível no backend)

O backend GFP já persiste o `stableId` da **notificação** com índice único
parcial `(id_usuario, package_name, stable_id)` e `INSERT ... ON CONFLICT DO
NOTHING` (reenvio → 200 sem duplicar). Portanto, para o fluxo de **notificações**
a idempotência de servidor existe. Ver `node-zero/docs/api.md` e
`node-zero/docs/coletor-auth.md`.

> Observação: a documentação antiga que dizia "o backend não implementa
> idempotência por stableId" está desatualizada — isso foi implementado em etapa
> anterior do backend.

## Sincronização de despesas — NÃO implementada nesta entrega (RF10)

As **despesas não são sincronizadas**. Só existe contrato de API para
**notificações** (`POST /notificacoes`). Conforme o escopo, despesas não devem
ser enviadas antes de existir um contrato de API adequado para esse tipo de
dado. Esta entrega mantém as despesas **somente locais** (Room).

### Contrato PROPOSTO (futuro, não implementado) para despesas

Quando for a hora de sincronizar despesas, sugere-se:

```
POST /despesas            (autenticado por dispositivo — ver coletor-auth.md)
{
  "sourceStableId": "<hash da notificação de origem>",  // chave idempotente
  "amountCents": 5000,
  "merchant": "PADARIA CENTRAL",
  "purchaseDate": "2026-09-04T14:05:00Z",
  "type": "PURCHASE_APPROVED",
  "installmentNumber": null,
  "installmentTotal": null,
  "cardLast4": "1234",
  "category": "Supermercado",
  "status": "CONFIRMED"
}
```

Requisitos do contrato futuro:
- unicidade por `(id_usuario, sourceStableId)` no servidor (idempotência);
- dono resolvido pelo **dispositivo autenticado**, nunca por id no payload;
- 201 ao criar, 200 em reenvio (sem duplicar);
- compatibilidade: não quebrar `POST /notificacoes`.

## Cartões (RF04)

Cadastro básico com nome, instituição, apelido, últimos 4 dígitos (opcionais),
dia de vencimento e dias de fechamento (guardados para o módulo de faturas
**futuro**), e situação ativa/inativa. **Nunca** armazena número completo, CVV,
senha ou credenciais. A resolução de cartão nunca escolhe arbitrariamente: em
ambiguidade, o item vai para revisão.

## Categorias e aprendizado (RF07)

Categorias iniciais semeadas na migração/instalação. A sugestão é
**determinística**: associação aprendida (estabelecimento→categoria confirmada
pelo usuário) tem prioridade sobre regras de palavra-chave locais. Nome ambíguo
ou ausente → sem sugestão (fica pendente). Sem treinamento de modelos.

## Privacidade e robustez (requisitos técnicos)

- Logs **não** registram conteúdo da notificação nem valores; só o tipo de erro.
- Interpretação roda fora da main thread (coroutine em `Dispatchers.IO`).
- Falha de interpretação é isolada e não afeta a captura.
- Migração v2→v3 **aditiva**: nenhuma notificação antiga é apagada.

## Migração do banco (Room v2 → v3)

`AppDatabase` passou para `version = 3`. `MIGRATION_2_3` cria `cards`,
`categories`, `expenses`, `merchant_categories` (com índices) e semeia as 9
categorias iniciais, sem tocar em `notifications`. Instalações novas semeiam via
`onCreate` (callback).

## Testes

- **Unitários (executados):** `NotificationParserTest` (11) e
  `ExpenseRepositoryTest` (10) cobrem os 10 casos do RF07 — compra aprovada
  completa, valor ausente, estabelecimento desconhecido, cartão ambíguo, compra
  recusada, pagamento de fatura, estorno/cancelamento, sugestão/correção de
  categoria, reprocessamento sem duplicar, confirmação de item em revisão. Mais
  os 20 testes existentes do coletor (preservados). Total: **41 testes, 0 falhas**
  via `./gradlew :app:testDebugUnitTest`.
- **Instrumentado (NÃO executado aqui):** `MigrationTest.migra_2_para_3_...`
  valida a migração preservando notificações e criando o módulo. Requer
  dispositivo/emulador: `./gradlew :app:connectedDebugAndroidTest`.

## Como rodar

```bash
./gradlew :app:testDebugUnitTest      # testes unitários (JVM)
./gradlew :app:assembleDebug          # compila o APK debug
./gradlew :app:connectedDebugAndroidTest   # testes instrumentados (exige device)
```

> Nota de build: o wrapper foi ajustado para Gradle 8.14.3 (compatível com o AGP
> 8.13.2 do projeto). O wrapper anterior apontava para Gradle 9.7.1, incompatível
> com AGP 8.x, o que impedia qualquer compilação.

## Limitações conhecidas / fora de escopo

- Despesas não sincronizam (sem contrato de API dedicado nesta entrega).
- Sem faturas/parcelas/pagamento (os campos de fechamento/vencimento do cartão
  ficam guardados para uso futuro). `installmentNumber/Total` são extraídos e
  guardados, mas não geram múltiplos lançamentos.
- Interpretação baseada em regras pt-BR; textos muito fora dos padrões dos bancos
  suportados tendem a cair em revisão (comportamento seguro e desejado).
- Vínculo de estorno/cancelamento à compra original não é inferido.
- Data registrada é a **da compra** (origem da notificação), nunca a de pagamento
  de fatura.

## Passos futuros

1. Definir e implementar o contrato `POST /despesas` idempotente no backend e a
   sincronização no cliente (reusando a fila/estados do coletor, sem criar
   segunda infraestrutura).
2. Módulo de faturas usando `dueDay`/`closingDaysBeforeDue`.
3. Tratamento de parcelas como lançamentos relacionados.
4. Vínculo explícito estorno/cancelamento ↔ compra.

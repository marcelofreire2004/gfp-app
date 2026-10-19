# Configuração e Build

## Requisitos

- **Android Studio** (compatível com AGP 8.13.x) ou Gradle 8.13 via wrapper.
- **JDK 17** (ambiente validado). O projeto define `sourceCompatibility`/`targetCompatibility = 11` e `jvmTarget = "11"`; compila normalmente sob o JDK 17.
- **Android SDK 36** (`compileSdk`/`targetSdk`).
- Dispositivo ou emulador com **Android 7.0+ (API 24)**.

## Configuração do módulo (`app/build.gradle.kts`)

| Propriedade | Valor |
|-------------|-------|
| `namespace` / `applicationId` | `com.example.coletornotificacao` |
| `compileSdk` | 36 |
| `minSdk` | 24 |
| `targetSdk` | 36 |
| `versionCode` | 1 |
| `versionName` | "1.0" |

### Plugins
- `com.android.application`
- `org.jetbrains.kotlin.android`
- `org.jetbrains.kotlin.kapt` (processamento de anotações do Room)

### Principais dependências de runtime
- `androidx.core:core-ktx`, `androidx.appcompat:appcompat`
- `com.google.android.material:material`, `androidx.recyclerview:recyclerview`
- `androidx.lifecycle:lifecycle-runtime-ktx`, `lifecycle-livedata-ktx`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android`
- `androidx.room:room-runtime`, `room-ktx` + `room-compiler` (kapt)
- `com.squareup.retrofit2:retrofit`, `converter-gson`
- `androidx.work:work-runtime-ktx` (sincronização em background)

### Dependências de teste
- `junit`, `kotlinx-coroutines-test`, `com.squareup.okhttp3:mockwebserver`
- `androidx.room:room-testing`, `androidx.test:core`, `org.robolectric:robolectric`
- (instrumentado) `androidx.test.ext:junit`, `espresso-core`, `room-testing`, `androidx.test:core`

### Opções relevantes
- Export de schema Room: `kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }`; os schemas ficam em `app/schemas/` e são disponibilizados ao `MigrationTestHelper` via `androidTest` assets.
- `testOptions.unitTests`: `isIncludeAndroidResources = true` e `isReturnDefaultValues = true` (permite `android.util.Log` em testes host).

> Observação: o `libs.versions.toml` ainda declara artefatos de Jetpack Compose e o plugin `kotlin-compose`, mas a UI é baseada em Views XML e o plugin Compose não é aplicado. São resquícios do template inicial.

## Compilar e instalar

Na raiz do projeto (Windows / PowerShell):

```powershell
.\gradlew.bat assembleDebug          # build debug
.\gradlew.bat installDebug           # instala no dispositivo/emulador conectado
.\gradlew.bat testDebugUnitTest      # testes unitários (host)
.\gradlew.bat connectedDebugAndroidTest  # testes instrumentados (migração) — requer dispositivo
```

Com o Android SDK fora do PATH, o `adb`/`emulator` ficam em `%LOCALAPPDATA%\Android\Sdk\platform-tools` e `...\emulator`.

## Testes

- **Unitários (host)**: `IdempotencyKeyTest`, `NotificationRepositoryTest`, `NotificationSyncerTest` (usam `FakeNotificationDao` e `FakeNotificationApi`), além do template `ExampleUnitTest`. Cobrem: salvar local, disponibilidade sem internet, sucesso→SYNCED, falha mantém pendente, HTTP erro não marca SYNCED, preservação do `createdAt`, reenvio de falha, `stableId` estável no reenvio, `lastError` sem conteúdo sensível, cancelamento repropagado, `attemptCount` consistente e exclusão segura.
- **Instrumentado (dispositivo)**: `MigrationTest.migra_1_para_2_preserva_dados_antigos` valida a migração 1→2 preservando dados; `ExampleInstrumentedTest`.

## Passos de uso no dispositivo

1. Instale o app.
2. Ao abrir, habilite o "Coletor de Notificações" em **Configurações → Acesso a notificações**.
3. Em Android 13+, conceda a permissão de notificações.
4. Use "Gerar Notificação de Teste" para validar a captura (o pacote da app casa com o filtro por conter "coletor").
5. A tela central mostra o estado de cada notificação; use "Sincronizar agora" para reenviar pendentes.

## Rede e privacidade

- **HTTPS obrigatório**: `network_security_config.xml` usa `base-config cleartextTrafficPermitted="false"`; o `usesCleartextTraffic` foi removido do manifest. Nenhuma exceção insegura para testes.
- **Backup**: o manifest referencia `backup_rules.xml` (Android ≤11) e `data_extraction_rules.xml` (Android 12+), ambos **excluindo** `notification_db` (+`-shm`/`-wal`) de cloud-backup e device-transfer.
- Para apontar para outro backend, edite `ApiClient.BASE_URL` (sem credenciais no código).

# Plano: Feature de Backup (Add-on com 2 modalidades)

*Criado em: 2026-05-02*

---

## Contexto

O músico precisa poder salvar suas configurações e instrumentos na nuvem para restaurar em caso de reinstalação, troca de dispositivo ou reset. A feature será oferecida como **serviço pago (add-on)** com duas modalidades de preço diferente.

---

## Modalidades

### Modalidade 1 — Backup de Configurações (leve, barato)

**O que salva:**
| Dado | Fonte | Formato | Tamanho típico |
|---|---|---|---|
| Settings globais | `SharedPreferences "stage_mobile_settings"` | JSON | ~2 KB |
| Set Stages (150 slots) | `SharedPreferences "stage_mobile_sets"` | JSON | ~50-200 KB |
| MIDI Learn mappings | Dentro de settings (JSON array) | JSON | ~1-5 KB |
| Nomes de bancos | Dentro de sets (bank_name_*) | JSON | <1 KB |
| Metadados SF2 (Firestore) | Coleção `soundfonts` | JSON | ~5-20 KB |

**Total estimado: <500 KB** — cabe tranquilamente no Firestore.

**O que NÃO salva:** arquivos .sf2 físicos (ficam apenas no device).

**Armazenamento:** Firebase Firestore — documento JSON na coleção `backups/{userId}/config_backups/{backupId}`.

### Modalidade 2 — Backup Completo (pesado, mais caro)

**Tudo da Modalidade 1 MAIS:**
| Dado | Fonte | Tamanho típico |
|---|---|---|
| Arquivos .sf2 físicos | `filesDir/soundfonts/` | 50 MB - 2 GB+ |

**Armazenamento:** Firebase Cloud Storage (por enquanto). Servidor definitivo a definir pelo usuário posteriormente. A implementação usará uma interface abstrata (`BackupStorageProvider`) pra permitir trocar o backend sem mexer no resto.

**Estrutura no Cloud Storage:**
```
backups/{userId}/full/{backupId}/
  ├── config.json          (configurações — mesmo da Modalidade 1)
  └── soundfonts/
      ├── Piano.sf2
      ├── Strings.sf2
      └── ...
```

---

## Arquitetura

### Modelo de dados

```kotlin
data class BackupMetadata(
    val id: String,                    // UUID
    val userId: String,                // Firebase Auth UID
    val type: BackupType,              // CONFIG ou FULL
    val createdAt: Timestamp,
    val deviceName: String,            // Build.MODEL
    val appVersion: String,            // BuildConfig.VERSION_NAME
    val configSizeBytes: Long,
    val sf2SizeBytes: Long,            // 0 para CONFIG backups
    val sf2FileCount: Int,             // 0 para CONFIG backups
    val status: BackupStatus           // PENDING, UPLOADING, COMPLETE, FAILED
)

enum class BackupType { CONFIG, FULL }
enum class BackupStatus { PENDING, UPLOADING, COMPLETE, FAILED }
```

### Camada de dados

```
BackupRepository.kt
  ├── createConfigBackup()      → serializa SharedPrefs + Firestore metadata → salva em Firestore
  ├── createFullBackup()        → config + upload SF2s via BackupStorageProvider
  ├── restoreConfigBackup()     → deserializa JSON → aplica em SharedPrefs + Firestore
  ├── restoreFullBackup()       → config + download SF2s
  ├── listBackups()             → lista backups do userId
  └── deleteBackup()            → remove backup + arquivos associados

BackupStorageProvider (interface)
  ├── FirebaseStorageProvider   → Firebase Cloud Storage (implementação inicial)
  └── (futuro: CustomServerProvider, S3Provider, etc.)
```

### Serialização das configurações

```kotlin
data class ConfigSnapshot(
    val version: Int = 1,                           // pra migrações futuras
    val settings: Map<String, Any>,                 // SharedPreferences "stage_mobile_settings" completo
    val setStages: Map<String, String>,             // SharedPreferences "stage_mobile_sets" completo (JSON strings)
    val soundFontMetadata: List<SoundFontMetadata>, // Firestore collection "soundfonts"
    val exportedAt: String,                         // ISO 8601 timestamp
    val deviceModel: String,
    val appVersion: String
)
```

**Serialização:** `SharedPreferences.getAll()` retorna `Map<String, *>`. Converter pra JSON com Gson. Na restauração, iterar o map e chamar `putString/putInt/putBoolean/putFloat` conforme o tipo.

### UI

**Tela de Backup** acessível via drawer/menu lateral:

```
┌──────────────────────────────────────────┐
│  BACKUP & RESTAURAÇÃO                     │
│                                           │
│  ┌─────────────────────────────────────┐ │
│  │ Último backup: 02/05/2026 14:30     │ │
│  │ Tipo: Configurações | 245 KB        │ │
│  └─────────────────────────────────────┘ │
│                                           │
│  [ Backup de Configurações ]   (Add-on)  │
│  [ Backup Completo ]           (Add-on)  │
│                                           │
│  ── Restauração ──                        │
│  📋 Config — 02/05/2026 14:30  [Restaurar]│
│  📦 Full — 01/05/2026 10:00   [Restaurar]│
│                                           │
│  1 backup por modalidade (sobrescreve)    │
└──────────────────────────────────────────┘
```

### Fluxo de Backup (Config)

```
1. Usuário toca "Backup de Configurações"
2. Verifica Custom Claim (add-on comprado?) → se não, mostra paywall
3. Serializa SharedPreferences "stage_mobile_settings" → JSON
4. Serializa SharedPreferences "stage_mobile_sets" → JSON
5. Busca coleção Firestore "soundfonts" → serializa como JSON array
6. Empacota tudo em ConfigSnapshot
7. Salva em Firestore: backups/{userId}/config_backups/{backupId}
8. Atualiza BackupMetadata
9. Toast: "Backup salvo com sucesso!"
```

### Fluxo de Backup (Full)

```
1-6. Mesmos passos do Config
7. Lista arquivos em filesDir/soundfonts/
8. Para cada .sf2:
   a. Upload pra Cloud Storage: backups/{userId}/full/{backupId}/soundfonts/{fileName}
   b. Atualiza progresso na UI (progress bar)
9. Salva ConfigSnapshot + metadata
10. Toast: "Backup completo salvo!"
```

### Fluxo de Restauração (Config)

```
1. Usuário seleciona backup da lista → toca "Restaurar"
2. Confirmação: "Isso substituirá todas as configurações atuais. Continuar?"
3. Busca ConfigSnapshot do Firestore
4. Limpa SharedPreferences "stage_mobile_settings" → aplica do snapshot
5. Limpa SharedPreferences "stage_mobile_sets" → aplica do snapshot
6. Atualiza coleção Firestore "soundfonts" com os metadados do snapshot
7. Reinicia o motor de áudio (reinitAudioEngine)
8. Toast: "Configurações restauradas!"
```

### Fluxo de Restauração (Full)

```
1-6. Mesmos passos do Config
7. Lista SF2s do backup no Cloud Storage
8. Para cada .sf2:
   a. Download pra filesDir/soundfonts/{fileName}
   b. Atualiza progresso
9. Reinicia o motor
10. Toast: "Restauração completa!"
```

---

## Gating (Add-on)

Mesmo pattern do Driver de Áudio:
- Firebase Custom Claim: `backupConfigAddon: true` e/ou `backupFullAddon: true`
- Validação server-side via Cloud Function + Google Play Billing
- UI: botões de backup mostram ícone de cadeado se claim não presente
- Por enquanto (desenvolvimento): gating desabilitado (todos têm acesso)

---

## Dependências Firebase necessárias

**Já temos:**
- `firebase-auth` ✅
- `firebase-firestore` ✅

**Precisamos adicionar:**
- `firebase-storage` — pra upload/download dos .sf2

```kotlin
// app/build.gradle.kts
implementation(libs.firebase.storage) // ou implementation("com.google.firebase:firebase-storage")
```

---

## Limites e proteções

| Regra | Valor |
|---|---|
| Máx backups por modalidade por usuário | **1** (novo backup sobrescreve o anterior com confirmação) |
| Tamanho máx por backup config | 1 MB (soft limit, alertar se exceder) |
| Tamanho máx por backup full | Definido pelo storage provider (Cloud Storage: 5 GB free tier) |
| Frequência mínima entre backups | 1 por hora (evitar spam) |

---

## Arquivos a criar/modificar

### Novos
| Arquivo | Conteúdo |
|---|---|
| `data/backup/BackupRepository.kt` | Lógica de serialização, upload, download, restauração |
| `data/backup/BackupStorageProvider.kt` | Interface abstrata + `FirebaseStorageProvider` |
| `data/backup/ConfigSnapshot.kt` | Data class do snapshot de configuração |
| `data/backup/BackupMetadata.kt` | Data class de metadados do backup |
| `ui/screens/BackupScreen.kt` | Tela de backup & restauração |

### Modificados
| Arquivo | Mudança |
|---|---|
| `app/build.gradle.kts` | Adicionar `firebase-storage` |
| `viewmodel/MixerViewModel.kt` | Expor função de backup/restore, navigation state |
| `MainActivity.kt` | Adicionar rota pra BackupScreen no drawer |

---

## Fases de implementação

| Fase | Escopo | Estimativa |
|---|---|---|
| **Fase 1** | Data layer: `ConfigSnapshot`, `BackupMetadata`, serialização de SharedPrefs | 1 dia |
| **Fase 2** | `BackupRepository`: `createConfigBackup()` + `restoreConfigBackup()` (Firestore only) | 1 dia |
| **Fase 3** | UI: `BackupScreen` com lista de backups, botões, confirmação de restore | 1 dia |
| **Fase 4** | Integração: drawer navigation, ViewModel functions, teste end-to-end config backup | 1 dia |
| **Fase 5** | Firebase Storage: `BackupStorageProvider` + `createFullBackup()` + upload SF2 com progresso | 2 dias |
| **Fase 6** | `restoreFullBackup()` + download SF2 com progresso | 1 dia |
| **Fase 7** | Limites (rotação FIFO, frequência), error handling, testes | 1 dia |

**Total estimado: ~8 dias**

---

## Segurança (Firestore Rules)

```javascript
match /backups/{userId}/{document=**} {
  allow read, write: if request.auth != null && request.auth.uid == userId;
}
```

Cada usuário só acessa seus próprios backups.

---

## Verificação

1. **Config backup:** salvar, fechar app, limpar dados do app, restaurar → todas as configurações voltam
2. **Full backup:** salvar com SF2s, limpar dados, restaurar → SF2s voltam + configurações
3. **Limite de 3:** criar 4 backups → o mais antigo é deletado automaticamente
4. **Multi-device:** fazer backup no Tab S9 FE, restaurar no S24 Ultra → tudo funciona
5. **Offline:** tentar backup sem internet → mensagem de erro clara

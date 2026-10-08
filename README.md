# anlobehub

A native Android client (Jetpack Compose) for LobeHub servers — official cloud
or self-hosted. It replicates the official app / PWA mobile experience:
server picker, email+password sign-in, sessions/topics/messages, streaming
chat, model switching, file upload, knowledge-base and assistant browsing,
settings.

## Tech stack

- AGP 9.4 + Gradle 9.6 + Kotlin 2.3.20 (AGP built-in Kotlin) + KSP 2.3.12 + Hilt 2.60.1
- Compose BOM 2026.09.00 (Material3) + type-safe Navigation Compose 2.10 + Paging + Coil 3 + mikepenz Markdown
- OkHttp 5 (tRPC + cookies + Gateway WebSocket) + kotlinx.serialization + DataStore + Room
- minSdk 26, target/compileSdk 37, JDK 17+ (verified with JDK 26/27)

## Modules

- `app`: MainActivity, NavHost (Startup→Server→Login→Sessions→Chat/Settings→KB/Assistants), session restore
- `core:common`: AnResult error model, normalizeBaseUrl, coroutine scopes
- `core:network`: SuperJson, TrpcClient (tRPC v11 batch), BetterAuthApi, PersistentCookieStore, AgentApi, GatewaySocket (WS v1/v2)
- `core:data`: ServerStore, UiPreferencesStore, Auth/Session/Topic/Message/Model/User/File/Discovery repositories
- `core:designsystem`: AnlobehubTheme, MarkdownText, AnTopBar, Loading/Empty/Error, UiText, shared strings
- `feature:auth/sessions/chat/settings`: screens + ViewModels, each with own `res/values[-zh-rCN]/strings.xml`

## Localization & theming

- English in default `values/` (fallback), Simplified Chinese in `values-zh-rCN/`.
- ViewModels expose `UiText`; screens resolve at the edge. Shared error
  mapping lives in `ErrorUiText.kt`.
- Language: System / Chinese / English (per-app locales via AppCompatDelegate).
- Theme: System / Light / Dark, persisted in DataStore, applied without restart.
- Both are switchable in Settings → Appearance.

## Protocol

See `docs/api-contract.md` (tRPC `/trpc/mobile` + superjson, Better Auth cookie
sign-in, Agent Gateway WS streaming). PWA parity scope: `docs/pwa-parity.md`.

## Build

```bash
# Requires Android SDK (platform 37) and JDK 17+
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/
./gradlew :app:assembleRelease      # R8, app-release-unsigned.apk
./gradlew test lint                  # unit tests + lint (0 errors)
```

Device/emulator smoke: install the debug APK → pick official cloud or enter a
self-hosted URL (e.g. `https://lobe.example.com`) → sign in with email+password →
open a session and send a message (server needs models + Agent Gateway).

## Self-hosted server requirements

- lobe-chat v2.x (verified against main, Better Auth; legacy NextAuth builds incompatible)
- Email+password sign-in enabled (`AUTH_DISABLE_EMAIL_PASSWORD` not true)
- Streaming requires the Agent Gateway (`config.getGlobalConfig.serverConfig.agentGatewayUrl` reachable)

## Known v1 limits

- Attachment fileIds not yet passed to execAgent (gateway attachment shape TBD); the upload chain itself works
- Knowledge bases / assistants are read-only; MCP/plugin execution, TTS, SSO/passkey, push, multi-agent orchestration not done

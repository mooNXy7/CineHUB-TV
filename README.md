# CineHUB TV — Kotlin oficial

Aplicativo nativo para Android TV e TV Box. A versão oficial atual é **1.3.3** (`versionCode` 133), com player Media3/ExoPlayer.

## OTA oficial

O atualizador Kotlin lê [`update-kotlin.json`](./update-kotlin.json), que aponta para a release estável `kotlin-v1.3.3` e valida o SHA-256 do APK publicado. A publicação oficial usa a chave de assinatura de produção configurada nos segredos do repositório.

## Compilar localmente

Requisitos: JDK 17, Android SDK 36 e Gradle 8.14.3.

```bash
gradle :app:assembleDebug --no-daemon
```

Para gerar uma release assinada, configure `CINEHUB_TV_KEYSTORE_BASE64`, `CINEHUB_TV_KEYSTORE_PASSWORD`, `CINEHUB_TV_KEY_ALIAS` e `CINEHUB_TV_KEY_PASSWORD`.

## Estrutura ativa

- `app/`: aplicativo Kotlin nativo, catálogo e player Media3/ExoPlayer.
- `.github/workflows/kotlin-ota-release.yml`: pipeline de release oficial, acionado apenas pela branch `release/kotlin-ota-v1.3.3` ou manualmente.
- `.github/workflows/kotlin-pr-validation.yml`: compilação e validação de metadados em pull requests para `main`.
- `frontend/` e `downloader-site/`: interfaces e página de download.
- `android/`: configurações de publicação do site, preservadas separadamente do app Android.

## Histórico

O aplicativo WebView 1.2.9 foi legado e substituído pela versão Kotlin nativa. A release e a tag históricas foram preservadas para auditoria e rollback; elas não são a versão oficial atual. A limpeza do código legado não altera o APK 1.3.3 já publicado nem o manifesto OTA estável.

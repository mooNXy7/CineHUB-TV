# CineHUB TV — Kotlin oficial

Aplicativo nativo para Android TV e TV Box. A versão oficial atual é **1.3.3** (versionCode 133), com player Media3/ExoPlayer.

## OTA oficial

O manifesto usado pelo atualizador Kotlin é [update-kotlin.json](./update-kotlin.json). Ele aponta para o APK da release estável kotlin-v1.3.3 e inclui o SHA-256 publicado pelo GitHub para validar o download.

## Compilar

Requisitos: JDK 17, Android SDK 36 e Gradle 8.14.3.

```bash
gradle :app:assembleDebug --no-daemon
```

Para a versão assinada de produção, configure as variáveis de assinatura CINEHUB_TV_KEYSTORE_BASE64, CINEHUB_TV_KEYSTORE_PASSWORD, CINEHUB_TV_KEY_ALIAS e CINEHUB_TV_KEY_PASSWORD no workflow oficial.

## Estrutura

- app/: aplicativo Kotlin nativo e player.
- .github/workflows/kotlin-ota-release.yml: workflow de release oficial (mantido na branch de release).
- frontend/ e downloader-site/: interfaces e página de download.
- android/: arquivos usados pela publicação do site; não é mais o projeto Android legado.

## Histórico

O projeto WebView 1.2.9 foi legado e separado do aplicativo Kotlin (pacote br.com.cinehub.tv). O código WebView foi retirado da árvore ativa nesta migração. A release histórica/tag antiga é preservada para auditoria e rollback; isso não a torna a versão atual nem altera o OTA Kotlin.

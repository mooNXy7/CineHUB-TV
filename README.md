# CineHUB TV — reconstrução nativa

Fundação inicial do CineHUB TV em Kotlin para Android TV / TV Box. Esta branch é isolada da versão estável.

## Estado desta fundação

- [x] Projeto Android Gradle em Kotlin
- [x] Manifesto Android TV e navegação em tela cheia
- [x] Primeira interface nativa com identidade escura/vermelha
- [x] Workflow GitHub Actions para compilar APK de depuração
- [ ] Importar os PNG oficiais para os recursos Android
- [ ] Player Media3 com tela de reprodução e controle remoto
- [ ] Catálogo real e fontes do CineHUB/SaimoPlayer
- [ ] Guia EPG/XMLTV e canais favoritos
- [ ] Filmes, séries, animes, doramas e rádio ligados a dados reais
- [ ] Testes de instalação em TV Box e validação de desempenho

Os cartões atuais são demonstração visual. Não representam catálogo conectado nem reprodução funcional ainda.

## Compilação

O GitHub Actions executa `gradle assembleDebug` com JDK 17 e publica o APK de teste como artefato do workflow. APK de depuração não deve ser tratado como lançamento oficial assinado.

## Referências de arquitetura

- SaimoTV Android: https://github.com/gabrielsaimo/SaimoTV-Android
- SaimoPlayer e dados de catálogo: https://github.com/gabrielsaimo/SaimoPlayer

A adaptação deve preservar os créditos/licenças aplicáveis e manter a identidade visual própria do CineHUB.

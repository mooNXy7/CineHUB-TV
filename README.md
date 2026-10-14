# CineHUB TV — reconstrução nativa

Fundação nativa em Kotlin para Android TV / TV Box. O desenvolvimento ocorre nesta branch separada para não substituir a versão estável.

## Implementado nesta etapa

- [x] Estrutura Android Gradle/Kotlin e tema escuro/vermelho
- [x] Navegação inicial com foco por controle remoto
- [x] Leitor do formato de catálogo `canal:`, `categoria:`, `logo:`, `fonte:`
- [x] Carregamento assíncrono do catálogo público de referência do SaimoPlayer
- [x] Tela inicial de Ao vivo com canais agrupados por categoria
- [x] Tela de reprodução com Media3/ExoPlayer para fontes individuais
- [x] Workflow GitHub Actions para tentar gerar APK de depuração

## Ainda em desenvolvimento

- [ ] Confirmar o resultado real da compilação no GitHub Actions
- [ ] Integrar logos PNG oficiais do CineHUB nos recursos Android
- [ ] Fallback automático entre fontes do mesmo canal e validação de links
- [ ] Busca, filtros e navegação de catálogo sem sobrecarregar TV Boxes fracas
- [ ] Guia EPG/XMLTV
- [ ] Filmes, séries, animes, doramas, favoritos, histórico e rádios
- [ ] Testes reais de instalação e reprodução em Android TV/TV Box

**Importante:** fontes públicas podem falhar, exigir cabeçalhos específicos ou não ter autorização de distribuição. A tela atual tenta tocar apenas a primeira fonte do canal; ainda não implementa fallback automático. Não considerar esta branch uma versão final.

## Build

O workflow usa JDK 17 e Gradle 8.9 para executar `assembleDebug`. O APK gerado é de depuração e não é o pacote oficial de lançamento.

## Referências de arquitetura e dados

- [SaimoTV Android](https://github.com/gabrielsaimo/SaimoTV-Android)
- [SaimoPlayer e catálogo](https://github.com/gabrielsaimo/SaimoPlayer)

A adaptação deve preservar os créditos/licenças aplicáveis e manter identidade visual própria do CineHUB.

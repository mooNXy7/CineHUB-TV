# CineHUB TV — reconstrução nativa

Fundação nativa em Kotlin para Android TV / TV Box. O desenvolvimento ocorre nesta branch separada para não substituir a versão estável.

## Implementado nesta etapa

- [x] Estrutura Android Gradle/Kotlin e tema escuro/vermelho
- [x] Navegação inicial com foco por controle remoto
- [x] Leitor do formato de catálogo `canal:`, `categoria:`, `logo:`, `fonte:`
- [x] Carregamento assíncrono do catálogo público de referência do SaimoPlayer
- [x] Tela inicial de Ao vivo com canais agrupados por categoria
- [x] Tela de reprodução com Media3/ExoPlayer e tentativa automática das fontes alternativas
- [x] Workflow GitHub Actions com APK de depuração gerado com sucesso

## Ainda em desenvolvimento

- [x] Confirmada a compilação de depuração no GitHub Actions
- [ ] Integrar logos PNG oficiais do CineHUB nos recursos Android
- [x] Fallback automático entre as URLs cadastradas no canal (ainda sem teste em aparelho)
- [ ] Busca, filtros e navegação de catálogo sem sobrecarregar TV Boxes fracas
- [ ] Guia EPG/XMLTV
- [ ] Filmes, séries, animes, doramas, favoritos, histórico e rádios
- [ ] Testes reais de instalação e reprodução em Android TV/TV Box

**Importante:** fontes públicas podem falhar, exigir cabeçalhos específicos ou não ter autorização de distribuição. O player tenta as fontes em sequência quando uma falha. Algumas fontes podem exigir cabeçalhos especiais (Referer/User-Agent) que ainda não estão integrados, e o build ainda precisa de teste em aparelho real. Não considerar esta branch uma versão final.

## Build

O workflow usa JDK 17 e Gradle 8.9 para executar `assembleDebug`. O APK gerado é de depuração e não é o pacote oficial de lançamento.

## Referências de arquitetura e dados

- [SaimoTV Android](https://github.com/gabrielsaimo/SaimoTV-Android)
- [SaimoPlayer e catálogo](https://github.com/gabrielsaimo/SaimoPlayer)

A adaptação deve preservar os créditos/licenças aplicáveis e manter identidade visual própria do CineHUB.

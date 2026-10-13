# CineHUB 2.2 — Integração SaimoPlayer + IPTV-Brasil-2026

## O que foi integrado

### Catálogo
- `dados/conteudo/manifest.js` passa a ser carregado no startup antes do loader.
- Índice lazy de filmes: `dados/conteudo/indices/filmes.json`.
- Índice lazy de séries: `dados/conteudo/indices/series.json`.
- Busca local expandida antes do catálogo online.
- Catálogo online continua como fallback e não é substituído.
- Capas são provenientes dos próprios registros M3U/TMDB usados pelas listas.
- Séries são agrupadas por título; episódios continuam sendo resolvidos sob demanda.

### Programação
- Consolidação de canais por nome para reduzir duplicações visuais.
- Fontes alternativas ficam agrupadas no mesmo canal.
- Logos com fallback.
- Contagem de canais por categoria e total filtrado.
- Busca e categorias dinâmicas.

### EPG
- Novo `src/js/epg.js`.
- Cache local de 6 horas.
- Fontes selecionáveis.
- Exibição de programa atual/próximo nos cards e no detalhe do canal.
- Janela de programação de curto prazo para evitar processamento desnecessário.

### Player
- Fontes alternativas permanecem dentro do player.
- HLS carregado somente quando necessário.
- Botão de copiar link da fonte.
- Removida a ação de abrir a fonte externamente.
- Séries locais tentam resolver temporada/episódio diretamente das listas.

### Performance
- Índices grandes não são carregados no splash.
- Listas M3U continuam sob demanda.
- EPG só é carregado ao entrar em Programação.
- Mantidos os modos Performance e Avançado.

## Verificações realizadas
- Sintaxe JavaScript validada com `node --check`.
- JSON dos índices validado.
- Manifesto presente e carregado antes de `largecatalog.js`.
- Player externo não é oferecido no fallback.

## 2.2.1 — EPG SaimoPlayer adaptado para Web/Android

- `src/js/epg.js` mantém a API `window.CineHUBEPG` já usada pela interface.
- A arquitetura de `EPGService` do SaimoPlayer foi portada para JavaScript: estado, cache, janela temporal, fontes independentes, matching e atualização por evento.
- O parser XMLTV usa varredura por bytes em vez de `DOMParser` para evitar construir uma árvore DOM gigante para os XMLTV maiores.
- Matching em camadas: `tvg-id`, nome normalizado, aliases conhecidos e prefixo somente quando a correspondência é inequívoca.
- Falha de uma fonte não interrompe as demais.
- Pluto TV usa correspondência por ID quando o canal contém `plu-<id>` ou o ID no logo.
- O EPG é somente enriquecimento: a lista `window.canaisM3U8` não é filtrada, substituída ou reduzida pelo serviço EPG.
- Cache local continua com TTL de 6 horas e janela de -6h/+3 dias.
- Nenhuma alteração foi feita no player, catálogo, séries ou carregamento de canais nesta etapa.

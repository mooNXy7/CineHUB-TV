# CineHUB TV 1.4 — Supabase / TMDB

## O que foi integrado
A versão TV reutiliza o mesmo endpoint configurado no CineHUB OFC:
- Supabase project URL: `https://qdheeiitzlvynshvkwcs.supabase.co`
- Edge Function: `cinehub-catalog`
- Endpoint: `https://qdheeiitzlvynshvkwcs.supabase.co/functions/v1/cinehub-catalog`
- Ações cliente: `search`, `details`, `trending`, `now_playing`
- Cache local: 6 horas (`cinehub_web_*`)

O navegador envia a chave publicável do Supabase no cabeçalho `apikey`. O token TMDB deve continuar no segredo backend `TMDB_TOKEN`, nunca no cliente.

## Arquivos alterados/adicionados
- `index.html`: carrega `js/webdata.js`, adiciona trilhos online na Home e janela de detalhes.
- `js/webdata.js`: camada de acesso ao endpoint, pesquisa, cache, feeds e normalização de dados.
- `js/app.js`: pesquisa online, carregamento de feeds, cards reais, hero dinâmico e detalhes.
- `css/styles.css`: estilos responsivos para metadados e janela de detalhes.
- `README.md`: documentação da integração.

## Contrato esperado da Edge Function
Requisição POST JSON, com `action` e parâmetros:
- `{ "action": "search", "type": "movie" | "tv", "query": "...", "page": 1 }`
- `{ "action": "details", "type": "movie" | "tv", "id": "TMDB_ID" }`
- `{ "action": "trending", "type": "movie" | "tv", "page": 1 }`
- `{ "action": "now_playing", "type": "movie" | "tv", "page": 1 }`

Resposta esperada: `{ "success": true, "data": ... }`. O pacote TV não altera o backend, porque o mobile já o utiliza. Se o contrato real tiver sido alterado, alinhar o cliente ao backend existente em vez de recriar a função.

## Testes realizados no pacote
- Verificação sintática de `js/app.js` e `js/webdata.js` com `node --check`.
- Inspeção de referências aos IDs usados pelo JavaScript.
- Não foi possível confirmar uma requisição real ao Supabase neste ambiente; teste após publicar e confira logs da Edge Function se falhar.

## Segurança
Não publicar service role keys ou token TMDB no frontend. A chave publicável já presente no cliente é a mesma configuração usada pelo mobile; proteção de dados e validação precisam ser garantidas no backend.

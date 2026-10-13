# CineHUB 2.3.3

- Programação: ingestão externa incremental, sem deduplicação O(n²) por lote.
- Programação: até 2 fontes são processadas em paralelo, sem reconstruir a grade a cada lote.
- Programação: atualização visual limitada durante a carga; montagem completa ao finalizar.
- Programação: canais recomendados fixados em Gloob, Nickelodeon Teen e Record.
- Programação: reprodução externa mantém candidatos HTTP/HTTPS.
- Programação: moldura das logos preparada para logos horizontais, sem recorte circular/deformação.
- Hero: destaque fixo de lançamentos 2026, com dados online de capa, sinopse, avaliação e ano.
- Hero: O Confronto dos Thundermans, Backrooms, Resident Evil e outros lançamentos de 2026.
- Player: removida referência residual a controles de copiar fonte no fallback.
- Service Worker: cache atualizado para 2.3.3.


## CineHUB TV 1.2.0 — Fase 2
- UI TV refinada para perfis 720p, 1080p e 4K.
- Hero, cards, Programação, EPG, menus e foco visual padronizados.
- Mantida a lógica da Fase 1 e o player existente.
- Performance mode recebe tratamento visual de baixo custo.

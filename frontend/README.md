# CineHUB TV 1.2.7

Atualização visual e de navegação baseada na versão 1.4 Supabase + TMDB.

## Principais mudanças
- Hero cinematográfico com imagem de fundo do título online, sinopse, ano e avaliação.
- Ações do hero: **Detalhes** e **Assistir**. Assistir abre a fonte quando o item já tem URL; se não houver fonte vinculada, avisa e abre os detalhes, sem inventar reprodução.
- Continue Assistindo abaixo do hero, oculto quando o histórico está vazio e mostrando até 12 conteúdos recentes quando há histórico local.
- Minha Lista disponível na navegação principal.
- Rádios renomeado e nova área Guia de programação.
- Cards, categorias, textos, fundo e estados visuais redesenhados na identidade preta/vermelha CineHUB.
- Telas de conteúdo reorganizadas com painel de categorias lateral e catálogo à direita, adaptando para telas menores.
- Revelação progressiva de seções e cards usando IntersectionObserver, respeitando `prefers-reduced-motion`.
- Mensagem compacta “Projeto em desenvolvimento”.
- Preservados os contratos existentes do Supabase/TMDB em `js/webdata.js`.

## Notas
- A integração usa o endpoint já configurado `cinehub-catalog`; não foi criado nem implantado backend novo.
- O guia exibe canais registrados no catálogo local. Dados reais de EPG dependem de fontes de programação já configuradas; nenhum horário foi inventado.
- Metadados TMDB não são fontes de vídeo. A ação Assistir precisa de uma URL de reprodução real para iniciar diretamente.
- O histórico e a lista são locais ao dispositivo/navegador.


## Versão 1.6
- Header translúcido sobre o hero, com ícone de configurações refinado.
- Guia removido da navegação principal e acessível dentro de Ao vivo.
- Hero sem slogans e sem símbolo grande; metadados, sinopse e imagem do catálogo online.
- Carrossel automático de filmes e séries do catálogo online, com troca a cada 8 segundos.
- Mantida a integração existente Supabase/TMDB e as funções de catálogo, favoritos, histórico e navegação.

## Versão 1.7
- Removidos os painéis promocionais de abertura das áreas de Filmes, Séries, Ao vivo, Rádios e Minha Lista.
- Pesquisa simplificada: os resultados só aparecem quando o usuário digita uma consulta; ao limpar, o catálogo categorizado volta.
- Layout de tela cheia com categorias à esquerda e conteúdo à direita, mantendo a identidade preta/vermelha.
- Sidebar de categorias esticada para acompanhar a altura útil da tela, com rolagem própria quando necessário.
- Guia de programação destacado de forma compacta somente na área Ao vivo.
- Preservada a integração Supabase/TMDB, favoritos, pesquisa online e navegação existente.


## Versão 1.2.7
- Interface redesenhada para televisores em paisagem 16:9, com navegação e foco adequados ao controle remoto.
- Teclado de pesquisa movido para a coluna esquerda, acima das categorias; coluna e catálogo acompanham a altura útil da tela.
- Grade de cards limpa e expandida para a área principal, com categorias/favoritos mantidos conforme a seção.
- Área Pesquisa simplificada: recomendações e resultados sem painéis de categorias/favoritos.
- Guia de programação apresentado em painel compacto à direita na área Ao vivo.
- Mantidas as integrações e chaves de armazenamento existentes.

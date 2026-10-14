# CineHUB TV — acompanhamento da reconstrução nativa

Branch de trabalho: `native-kotlin-foundation`

## Progresso geral

Estimativa de planejamento: **26% da reconstrução completa**. A base técnica já compila, mas as áreas de conteúdo ainda não estão completas e não houve validação em Android TV físico.

## Concluído

- [x] Projeto Android nativo em Kotlin criado em branch isolada da versão estável.
- [x] Dependências Media3/ExoPlayer adicionadas para reprodução.
- [x] Navegação inicial e modo imersivo implementados.
- [x] Catálogo público de canais ligado à seção Ao vivo.
- [x] Player recebe fontes alternativas e tenta a próxima quando uma falha.
- [x] Correção do botão Voltar aplicada.
- [x] Build de depuração concluído com sucesso no GitHub Actions.

## Em andamento / próximo ciclo\n\n- [x] Build confirmado após pesquisa global, favoritos, carregamento de logos e fallback visual.

- [ ] Aplicar os arquivos oficiais de identidade visual como recursos locais do app (logo, ícone e splash).
- [ ] Refinar foco, tamanhos e navegação por controle remoto em telas de TV.
- [x] Implementar pesquisa por nome/categoria na seção Ao vivo e conectar a tela Buscar ao catálogo de canais.
- [x] Exibir logos dos canais com carregamento assíncrono e fallback visual.
- [x] Melhorar mensagens de carregamento/erro e adicionar atualização manual do catálogo em Ajustes.

## Ainda falta

- [ ] Integrar catálogo real de filmes e séries com capas, sinopses e metadados.
- [ ] Separar temporadas e episódios para séries/animes/doramas.
- [x] Implementar Minha Lista com favoritos de canais persistidos localmente.\n- [ ] Implementar histórico real de Continue Assistindo.
- [ ] Integrar rádios e guia de programação/EPG.
- [ ] Validar reprodução real em diferentes fontes e formatos.
- [ ] Testar instalação, controle remoto, suspensão/retomada e desempenho em Android TV/TV Box físico.
- [ ] Preparar pacote de teste adequado para TV e, depois, lançamento assinado separado.

## Critério para enviar o próximo APK de teste

Não enviar a cada pequena alteração. Gerar um pacote quando identidade visual, navegação de TV e fluxo Ao vivo/player estiverem consolidados e a compilação passar. O APK debug atual comprova apenas a compilação; não é validação de funcionamento em TV real.

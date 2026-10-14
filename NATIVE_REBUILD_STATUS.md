# CineHUB TV — acompanhamento da reconstrução nativa

Branch de trabalho: `native-kotlin-foundation`

## Progresso geral

Estimativa de planejamento: **28% da reconstrução completa**. A base técnica já compila, mas as áreas de conteúdo ainda não estão completas e não houve validação em Android TV físico.

## Concluído

- [x] Projeto Android nativo em Kotlin criado em branch isolada da versão estável.
- [x] Dependências Media3/ExoPlayer adicionadas para reprodução.
- [x] Navegação inicial e modo imersivo implementados.
- [x] Catálogo público de canais ligado à seção Ao vivo.
- [x] Player recebe fontes alternativas e tenta a próxima quando uma falha.
- [x] Correção do botão Voltar aplicada.
- [x] Build de depuração concluído com sucesso no GitHub Actions.
- [x] Pesquisa por nome/categoria na seção Ao vivo e tela Buscar conectada ao catálogo de canais.
- [x] Logos dos canais com carregamento assíncrono e fallback visual.
- [x] Mensagens de carregamento/erro e atualização manual do catálogo em Ajustes.
- [x] Ícone vetorial nativo e tela inicial escura com a marca CineHUB adicionados; build confirmado no commit atual. Os PNGs oficiais ainda precisam ser integrados.

## Em andamento / próximo ciclo

- [ ] Refinar foco, tamanhos e navegação por controle remoto em telas de TV.
- [ ] Melhorar consistência visual entre as seções nativas.
- [ ] Confirmar se o fluxo de inicialização e a tela inicial se comportam bem em dispositivos reais.

## Ainda falta

- [ ] Integrar catálogo real de filmes e séries com capas, sinopses e metadados.
- [ ] Separar temporadas e episódios para séries/animes/doramas.
- [x] Implementar Minha Lista com favoritos de canais persistidos localmente.
- [ ] Implementar histórico real de Continue Assistindo.
- [ ] Integrar rádios e guia de programação/EPG.
- [ ] Validar reprodução real em diferentes fontes e formatos.
- [ ] Testar instalação, controle remoto, suspensão/retomada e desempenho em Android TV/TV Box físico.
- [ ] Preparar pacote de teste adequado para TV e, depois, lançamento assinado separado.

## Critério para enviar o próximo APK de teste

Não enviar a cada pequena alteração. Gerar um pacote quando identidade visual, navegação de TV e fluxo Ao vivo/player estiverem consolidados e a compilação passar. O APK debug atual comprova apenas a compilação; não é validação de funcionamento em TV real.

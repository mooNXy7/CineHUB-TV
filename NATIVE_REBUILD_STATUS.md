# CineHUB TV — acompanhamento da reconstrução nativa

Branch de trabalho: `native-kotlin-foundation`

## Progresso geral

Estimativa de planejamento: **22% da reconstrução completa**. A base técnica já compila, mas as áreas de conteúdo ainda não estão completas e não houve validação em Android TV físico.

## Concluído

- [x] Projeto Android nativo em Kotlin criado em branch isolada da versão estável.
- [x] Dependências Media3/ExoPlayer adicionadas para reprodução.
- [x] Navegação inicial e modo imersivo implementados.
- [x] Catálogo público de canais ligado à seção Ao vivo.
- [x] Player recebe fontes alternativas e tenta a próxima quando uma falha.
- [x] Correção do botão Voltar aplicada.
- [x] Build de depuração concluído com sucesso no GitHub Actions.

## Em andamento / próximo ciclo\n\n- [ ] Confirmar compilação após a integração da pesquisa global e da busca ao vivo.

- [ ] Aplicar os arquivos oficiais de identidade visual como recursos locais do app (logo, ícone e splash).
- [ ] Refinar foco, tamanhos e navegação por controle remoto em telas de TV.
- [x] Implementar pesquisa por nome/categoria na seção Ao vivo e conectar a tela Buscar ao catálogo de canais (aguardando confirmação do build desta revisão).
- [ ] Exibir logos dos canais com fallback e carregamento seguro.
- [ ] Melhorar mensagens de erro, carregamento e nova tentativa sem travar a interface.

## Ainda falta

- [ ] Integrar catálogo real de filmes e séries com capas, sinopses e metadados.
- [ ] Separar temporadas e episódios para séries/animes/doramas.
- [ ] Implementar Minha Lista e persistência de favoritos/Continue Assistindo.
- [ ] Integrar rádios e guia de programação/EPG.
- [ ] Validar reprodução real em diferentes fontes e formatos.
- [ ] Testar instalação, controle remoto, suspensão/retomada e desempenho em Android TV/TV Box físico.
- [ ] Preparar pacote de teste adequado para TV e, depois, lançamento assinado separado.

## Critério para enviar o próximo APK de teste

Não enviar a cada pequena alteração. Gerar um pacote quando identidade visual, navegação de TV e fluxo Ao vivo/player estiverem consolidados e a compilação passar. O APK debug atual comprova apenas a compilação; não é validação de funcionamento em TV real.

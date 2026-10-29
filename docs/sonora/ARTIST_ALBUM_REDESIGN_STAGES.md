# Sonora — Etapas de refinamento do perfil de artista e da página de álbum

**Estado:** implementação das etapas 1–6 aplicada no PR #2, **aguardando CI e validação visual**. Não publicar APK nem fazer merge antes do fechamento das pendências.  
**Data de criação:** 2026-10-29.  
**Branch de trabalho:** `feat/sonora-artist-album-neutral` (PR [#2](https://github.com/FernandoDevCode/N-STOP/pull/2), em draft).  
**Base preservada:** `checkpoint/sonora-local-20261029` (`d3cf66b83`).

## 1. Objetivo e diagnóstico

Concluir **todas** as correções visuais e funcionais destas páginas antes de **publicar outro APK de teste**. A primeira rodada adotou superfícies grafite e controles branco-frio, porém ainda não atingiu a composição editorial e compacta aprovada.

- **Perfil de artista:** está mais próximo do alvo. A fotografia imersiva do Megadeth funciona; a principal deficiência é a distribuição vertical abaixo da fotografia.
- **Página de álbum:** precisa de mais refinamento; o espaço entre capa e título é excessivo, o fundo atmosférico pouco perceptível e os botões ocupam área demais.
- **Listas:** continuam com cartões grandes, quando o objetivo são linhas musicais mais compactas.
- **Abas:** o indicador de seção ativa ficou incorreto em uma captura de rolagem.

**Referência externa enviada pelo usuário:** [Apple Music Artist Profile Explorations — Victor Poltorak (imagem)](https://images.openai.com/static-rsc-4/PATB7COvCvks4cNMM1QpXhbCFU91Vm61k26OUQVistx4LVl7Rfn4Jw9sk95MfR8dZLcwobUqpkdnz5wWIItMfxxrHPTjwCw8CsfUW0KlBx3mO-MrgxtTVOItEtEEC1dJKeGQ8tNQ4E01yxGRvL5v9H1rFL1ehR1lHpq9g4BabeE?purpose=inline). Link externo de apoio, não arquivo garantido do repositório. Usar também as capturas reais aprovadas no histórico do projeto; não copiar informações fictícias presentes em mockups.

**Meta de composição aproximada:** nas capturas iniciais, a seção **Popular** começa por volta de **74%** da altura da tela; na referência aprovada, perto de **52%**. Isso orienta a redução do espaço, **não** é uma regra rígida válida para todas as telas.

## 2. Etapas de implementação

Concluir e validar cada etapa antes de marcar `[x]`. A ordem pode mudar apenas se houver dependência técnica comprovada; não ampliar escopo.

### Etapa 1 — Perfil mais compacto e limpo

- [ ] Compactar o bloco de controles abaixo do hero, sem prejudicar área de toque, leitura ou acessibilidade.
- [ ] Antecipar visualmente a seção **Popular**, preservando a fotografia full-bleed e o degradê suave.
- [ ] Retirar a **fileira de redes sociais do topo**; migrar todos os links válidos para **Sobre**, sem perder funcionalidades nem inventar links.
- [ ] Preservar reproduzir artista, rádio, voltar, suporte e controles existentes; realocar ações secundárias quando necessário.
- [ ] Verificar conteúdo curto/longo, ausência de imagem e dispositivos de larguras diferentes.

**Aceite:** topo visualmente mais leve e início de Popular substancialmente mais alto; nenhuma ação funcional perdida.

### Etapa 2 — Logo/wordmark oficial do artista com fallback

- [ ] Mapear uma fonte **confiável, permitida e tecnicamente viável** de logos de artistas/bandas com fundo transparente e vínculo inequívoco ao artista correto.
- [ ] Implementar a preferência: **wordmark oficial válido > nome tipográfico** (fallback).
- [ ] Preservar proporções, legibilidade e área máxima; não esticar, cortar nem inserir imagem de baixa qualidade.
- [ ] Tratar carregamento, ausência de logo, erro de rede, cache e contraste (incluindo logos escuros em fotografia escura).
- [ ] Não gerar logos falsos por IA, não confundir capas/promocionais com wordmarks nem fazer scraping indiscriminado.
- [ ] Demonstrar casos com logo confiável e casos sem logo; se não houver fonte válida/uso permitido, documentar o bloqueio **antes de considerar a etapa concluída**.

**Aceite:** wordmark real aparece quando válido; em todos os demais casos o nome fica correto e legível. Não sacrificar estabilidade nem precisão de identidade.

### Etapa 3 — Listas editoriais de músicas

- [ ] Substituir os **cartões grandes e arredondados** por linhas compactas nas páginas de **perfil de artista** e **álbum**.
- [ ] Usar separadores discretos e hierarquia clara entre capa, título, artista, duração, download e menu.
- [ ] Manter estados de reprodução, loading, download, menu, acessibilidade e ações reais.
- [ ] Não alterar globalmente os componentes das listas de Busca, Home ou outras telas por conveniência.

**Aceite:** lista mais densa e legível, com toques/ações preservados; busca global visual e funcionalmente intacta.

### Etapa 4 — Página de álbum: capa e atmosfera

- [ ] Reduzir o espaço excessivo entre a capa e o título.
- [ ] Reforçar **de modo contido** a atmosfera extraída das cores reais da capa (sem acento roxo/lavanda fixo).
- [ ] Preservar a capa nítida e proporcional; evitar fundo excessivamente saturado, ruído visual e blur custoso.
- [ ] Garantir fallback neutro para capas ausentes, pretas, brancas ou muito saturadas.

**Aceite:** capa/título mais coesos e atmosfera perceptível sem perder a identidade grafite do Sonora.

### Etapa 5 — Abas do artista e estado durante rolagem

- [ ] Corrigir a aba ativa de acordo com a seção realmente visível (**Músicas / Álbuns / Singles / Sobre**).
- [ ] Tornar o sublinhado da aba ativa perceptível, discreto e consistente.
- [ ] Manter saltos de navegação, cabeçalho sticky e atualização de seleção após rolagem manual e toques.
- [ ] Testar seções vazias/ausentes e rolagens rápidas.

**Aceite:** ao exibir Álbuns, a aba selecionada é **Álbuns**, e assim para todas as seções presentes.

### Etapa 6 — Controles de álbum em uma composição compacta

- [ ] Evitar quebra isolada de **Baixar tudo** para uma segunda linha.
- [ ] Dar prioridade à reprodução; organizar fila, salvar, shuffle e download de forma mais contida (menu de ações secundárias se necessário).
- [ ] Preservar os estados habilitado/desabilitado e as operações reais.
- [ ] Conferir labels grandes, telas estreitas e ações condicionais (ex.: shuffle só quando disponível).

**Aceite:** controles sem quebra visual indesejada ou perda de ações; visual alinhado à referência editorial.

### Etapa 7 — Revisão conjunta e regressões

- [ ] Conferir as duas páginas lado a lado com a referência e capturas reais no Redmi Note 14.
- [ ] Revisar espaçamentos, gradientes, contraste, tipografia e estados de carregamento/vazio.
- [ ] Fazer varredura de roxo/lavanda/verde-oliva **nos componentes do app**, respeitando cores originais de imagens de conteúdo e marcas externas quando pertinentes.
- [ ] Compilar e executar os testes pertinentes; revisar testes falhos da CI e distinguir falhas preexistentes de regressões novas com evidências.
- [ ] Validar reprodução, fila, salvar, download, rádio, navegação, miniplayer e retorno das telas.
- [ ] Não alterar Media3, autenticação, fluxo de downloads, lógica de reprodução, Player V7/V8 ou o comportamento do mini-player por motivo exclusivamente visual.

**Aceite:** sem falha de compilação ou regressão relevante; pendências de testes explicitadas e resolvidas antes da aprovação; fidelidade visual julgada por screenshots, não apenas por build verde.

### Etapa 8 — Só então preparar e publicar o APK de teste

- [ ] Incrementar `versionCode` e identificar claramente a versão da prévia, para distinguir da instalada anteriormente.
- [ ] Confirmar `applicationId`, variante e assinatura compatível com o aplicativo de teste instalado; **não instruir desinstalação/limpeza de dados** para contornar incompatibilidade.
- [ ] Executar build final e verificar nome, tamanho, SHA-256 e procedência do APK.
- [ ] Publicar **um único pré-release identificável** no GitHub Releases com changelog, limitações e link direto.
- [ ] Instalar/testar no Redmi e aprovar capturas de artista (topo + rolagem) e álbum antes de qualquer merge.

**Regra de entrega:** podem existir compilações internas de validação durante as etapas, mas **não publicar outro APK para instalação do usuário antes da conclusão das etapas 1–7**.

## 3. Paleta e invariantes

| Token | Hex |
| --- | --- |
| Fundo | `#0E1115` |
| Superfície | `#191D23` |
| Superfície elevada | `#232831` |
| Seleção neutra | `#343944` |
| Texto principal / botão primário | `#F4F5F7` |
| Texto secundário | `#B4B8C4` |
| Iluminação / destaque claro | `#D4D8E0` |

- **Sem** roxo, lavanda ou verde-oliva como cor de UI; a arte do artista/álbum mantém suas próprias cores.
- Preservar a navegação inferior e o mini-player já aprovados.
- Preservar fontes de dados, ações reais, integrações e semântica de acessibilidade.
- Não introduzir conteúdo fictício, novos recursos não pedidos, novos serviços sem validação ou refatorações amplas.
- Manter o PR **#2 em draft**, sem merge antes de validação funcional e visual.
- O `AGENTS.md` continua sendo autoridade operacional geral; este documento é o **plano de produto/visual específico das páginas de artista e álbum**, não substitui regras de outros módulos (incluindo Player V8/Art Lab).

## 4. Implementação da segunda rodada — ainda não aprovada

Em 2026-10-29, foram enviados ao PR #2 os patches de código abaixo. **Aplicado ao repositório não significa validado em aparelho.**

| Etapa | Implementação no código | Pendência de aceite |
| --- | --- | --- |
| 1 — Perfil compacto | Hero de 370 dp para 315 dp; faixa de redes removida; links preservados em Sobre | Captura real, legibilidade e dimensões variadas |
| 2 — Wordmarks | Consulta experimental TheAudioDB por nome exato, render com fallback tipográfico, atribuição em Sobre e teste de identidade | Confirmar autenticidade visual, contraste e termos para publicação fora de ambiente de desenvolvimento |
| 3 — Linhas editoriais | `SongRow(editorial = true)` somente em Popular e faixas de álbum; separadores sutis | Capturas reais, clique/download/menu e regressão da Busca |
| 4 — Álbum | Header reduzido, capa maior em relação à área útil, atmosfera da arte reforçada e fallback neutro em Android antigo | Conferir contraste, custo de blur e tipos de capa |
| 5 — Abas | Seção ativa detectada próximo ao sticky; sublinhado reforçado; rolagem posiciona título abaixo da navegação | Validar rolagem manual, clique e seções ausentes no dispositivo |
| 6 — Controles | Reprodução circular, fila e salvar na faixa; shuffle e baixar tudo no menu secundário | Validar estados e tamanhos de fonte em telas estreitas |
| 7 — QA | Auditoria preliminar do diff sem adição de roxo nos trechos novos; testes adicionais de identidade | **CI final, regressões, screenshots e revisão funcional ainda pendentes** |
| 8 — APK | Não iniciado | Depende de fechamento do código e validação técnica; screenshot em Android ocorre após instalar o APK de teste e antes do merge |

**Fonte de wordmark — uso experimental:** [TheAudioDB API](https://www.theaudiodb.com/free_music_api) disponibiliza `strArtistLogo` e uma chave pública de desenvolvimento; [seus termos](https://www.theaudiodb.com/docs_terms_of_use.php) restringem publicação em loja sem plano pago. As imagens são em grande parte contribuições da comunidade; a correspondência exata do nome **não comprova** autorização oficial da marca. Antes de publicar comercialmente, confirmar licença, autenticidade e eventual necessidade de fornecedor pago ou curadoria autorizada. Não converter essa pendência em um logo fabricado.

**Sequência de liberação:** CI e regressões do código → preparação do APK de teste identificado por nova versão → instalação e capturas reais → aprovação visual → merge. A captura da implementação nova em um dispositivo requer um APK de teste; portanto, a validação *no aparelho* é posterior ao build, sem dispensar o gate técnico anterior.

## 5. Registro de execução

- [x] Checkpoint local sincronizado no GitHub: `d3cf66b83`.
- [x] Primeira rodada de paleta neutra e headers aplicada no PR #2.
- [x] Capturas reais de álbum e perfil (Megadeth) recebidas e avaliadas.
- [ ] Etapas 1–7 refinadas, verificadas e aprovadas. **Código das etapas 1–6 enviado; CI e evidência visual pendentes.**
- [ ] APK final da rodada publicado e validado (Etapa 8).

**Situação de CI no momento da criação:** `:app:assembleDebug` e compilação de testes passaram no PR #2. A suíte completa ficou vermelha em execuções distintas por falhas isoladas (`MirrorEngineTest` / `MiniPlayerRenderTest`), sem evidência suficiente para atribuí-las ao redesign; investigar e fechar a pendência antes de autorizar merge. **Nenhuma dessas etapas novas foi implementada ao criar este plano.**

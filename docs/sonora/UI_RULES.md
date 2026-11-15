# Sonora · Regras de UX/UI — superfícies de música

**Revisão:** 2026-11-13  
**Autoridade:** complementar a `AGENTS.md` e a `docs/sonora/SEARCH_REWORK_PLAN.md`. Não altera contratos de catálogo, identidade, algoritmo de busca ou reprodução.

## Direção

Inspirar-se na **eficiência da busca/lista do Spotify**, na **integração atmosférica da arte do Apple Music** e no **comportamento nativo do Android**, sem usar a paleta, ícones ou marca desses serviços como identidade Sonora.

O Sonora deve manter superfícies grafite/neutral, arte como fonte de atmosfera quando pertinente (principalmente cabeçalhos e player), contraste bem definido e ausência de bordas/cartões repetidos por faixa.

### 1. Linha de música — um padrão

| Contexto | Conteúdo | Ações |
|---|---|---|
| Busca | Capa quadrada ~52 dp, título, artista/versão, duração quando disponível | Toque reproduz; pressão longa abre menu |
| Álbum | Número, título, duração; não repetir a capa | Toque reproduz no contexto do álbum; pressão longa abre menu |
| Playlist, curtidas, biblioteca | Capa ~48 dp, título, artista, duração se couber | Toque reproduz na lista; pressão longa abre menu |
| Artista / Populares | Miniatura, título, álbum ou subtítulo, estado de reprodução | Toque reproduz; pressão longa abre menu |

- Altura alvo **54–72 dp**, respeitando área tátil confortável e textos ampliados; sem tamanhos fixos que cortem fontes acessíveis.
- Uma faixa **não é um card independente**. A lista estabelece ritmo com alinhamento, respiro e contraste, não 30 retângulos.
- Não mostrar permanentemente **download** nem **⋮** em toda linha. Mostrar só estado não acionável: tocando, baixado, baixando/pendente.
- A arte não fica coberta por controles permanentes. Na reprodução/carregamento, overlay mínimo e temporário.
- Duração opcional e neutra: nunca exibir `0:00` como metadado inventado.
- Texto: máximo 1 linha para título e artista, com ellipsis e expansão via tela/ações. Nunca substituir variantes ao exibir uma música.

### 2. Gestos e ações — contrato único

- **Toque:** ação primária do item (música reproduz; artista abre perfil; álbum abre detalhe).
- **Pressão longa:** feedback tátil e **bottom sheet contextual** da identidade pressionada.
- Bottom sheet: capa/título/artista no topo; ações explícitas conforme capacidade real, como baixar, salvar, reproduzir a seguir, fila e rádio; sem ações decorativas que não funcionam.
- **Seleção múltipla:** disponível como ação explícita na folha de opções em listas de biblioteca; depois, toques alternam a seleção e aparece a barra contextual existente.
- **Acessibilidade:** gesto longo exposto pelo Compose e ação alternativa no TalkBack/teclado; nenhum recurso deve depender exclusivamente de descobrir um gesto secreto. Mostrar dica discreta inicial em Busca.
- Sheet respeita navegação Voltar, dispensa ao tocar fora/arrastar; uma ação nunca abre dois menus simultaneamente.
- Opções do álbum e da playlist continuam sendo ações do **conjunto**, diferentes de opções de uma faixa.

### 3. Hierarquia por tela

- **Busca:** título + campo no topo. Sem engrenagem ou cabeçalho de marca redundante. Busca vazia simples, sem publicidade do provedor nem ícone gigante. Um resultado de faixa usa a mesma linha das demais; resultados de artista e álbum usam identidades próprias.
- **Álbum:** capa e atmosfera contextual são do cabeçalho; tracklist é neutra, numerada e escaneável. Não aplicar efeito da capa em cada faixa.
- **Playlist:** contexto e ações da coleção no cabeçalho; músicas seguem a mesma geometria da Biblioteca.
- **Home / Explorar / Rádio:** cards somente para descoberta/coleções, não para cada faixa. Seções devem comunicar algo diferente, não apenas repetir carrosséis.
- **Configurações e submenus:** componentes, tipografia, níveis de superfície e acentuação de Sonora; não reutilizar tokens visuais herdados sem revisão.

### 4. Cor, movimento e feedback

- Grafite, branco suave e variações neutras; cores da arte só em superfícies atmosféricas que possuem relação clara com ela.
- Não usar glow como enfeite universal nem roxo do projeto original.
- Transições de 150–260 ms apenas quando favorecem compreensão; não animar toda a tela durante entrada do teclado.
- Loading e download comunicam estado real. Mini-player e barra inferior seguem com identidade aprovada, sem reimplementação local.

### 4.1 Barra de status e arte edge-to-edge (regra global)

- A janela Android desenha atrás da barra de status e do recorte da câmera, sem uma faixa opaca independente.
- O fundo de **todas** as rotas cobre a tela até a borda superior. Heróis com foto, capa ou gradiente (artista, álbum online, coleções locais e player) também começam atrás dos ícones do sistema.
- **Controles e textos** continuam abaixo dos insets seguros. Componentes full-bleed aplicam `statusBarsPadding()` só nos botões sobrepostos; rotas sem herói recebem o padding seguro no host, sem duplicação.
- Um Scaffold interno de uma rota full-bleed não deve reintroduzir o inset superior. Manter suporte a recorte, teclado, navegação por gestos e seleção múltipla.
- Conferir visualmente em Android 16 com recorte de câmera, modo claro/escuro, scroll e mudanças de rota; CI verde não prova continuidade visual.

### 5. Governança de UI

Antes de aprovar alteração de frontend:

1. Mapear **rotas e composables reais**, não presumir que uma captura representa todas as telas.
2. Identificar componentes compartilhados e **todos os call sites**; verificar semânticas diferentes (ex.: pressão longa selecionando versus menu).
3. Validar modos escuro/claro, PT-BR/EN, fontes grandes, TalkBack, com/sem mini-player, consulta vazia/carregando/erro/parcial, downloaded/downloading.
4. Screenshots de dispositivo/emulador em Busca, álbum, playlist e favoritos; medir se a densidade e o teclado não regrediram.
5. Rodar CI e confirmar o commit exato do APK. Nunca declarar visual aprovado só por compilar.

**Cobertura integrada na v208 (UI):** SongRow na Busca, Populares e Álbum online; DetailTrackRow no Álbum, Playlist, Artista e Curtidas locais; TrackListItem em Biblioteca e listas de músicas compartilhadas. As faixas de álbum local são numeradas sem duplicar a capa do cabeçalho. As opções por pressão longa permanecem acessíveis no TalkBack, mesmo quando o botão ⋮ não é exibido. Ações por seleção múltipla e opções próprias da coleção continuam separadas.

**Submenus de Configurações:** SettingsScaffold e SettingsPageSurface/SettingsPageHeader compartilham fundo, cabeçalho, tipografia e cor nas rotas Reprodução, Qualidade, Contas, Biblioteca, Aparência, Sobre, Conexão web e também nas telas profundas Equalizador, Saúde da Biblioteca, Diagnósticos e Músicas Bloqueadas. Controles antigos de upgrade FLAC não devem reaparecer em Saúde da Biblioteca; arquivos FLAC existentes continuam listados normalmente.

**Validação que o CI não cobre:** capturas reais de cada rota em dark/light, PT-BR/EN, TalkBack, fontes grandes e Android 16; testes de gesto/contexto em aparelho. Esta cobertura de código não é homologação visual automática.

**Busca vigente na v210:** catálogo Spotify como guia principal e YouTube convencional como segunda guia. YTM não aparece como opção de Busca; integração de áudio interna segue independente do catálogo.


**Atualização de Busca (PR #86 em validação):** somente duas guias, Spotify inicial e YouTube convencional WEB; YouTube Music não aparece nem é consultado na Busca. Componentes internos de YTM necessários à reprodução permanecem intactos. Esta alteração não substitui validação de CI e dispositivo; consulte `SEARCH_REWORK_PLAN.md`.

## Correção de regressão visual — Spotify no PR #86 (2026-11-15)

Os detalhes Spotify **não podem** usar a linha genérica da Busca como página completa:
- **Artista:** reutilizar `ArtistHero`, `ArtistProfileBackdrop`, `ArtistSectionTabs`, `ArtistFeaturedReleaseCard` e `AlbumSquareCard`, conservando fotografia, atmosfera, destaque e seções. Guias que dependem de dados inexistentes (ex.: populares não consultados) não devem fingir resultados.
- **Álbum:** reutilizar `AlbumHero` e `SongRow(editorial=true, editorialAlbum=true)`: capa única no hero, número e duração nas faixas. Não repetir a capa do álbum em cada linha.
- Os dados continuam `CatalogSource.SPOTIFY`. **Nunca** encaminhar o Spotify ID como YTM browseId ou YouTube videoId, nem trocar uma versão da gravação sem confirmação.
- Desabilitar/ocultar ações que dependam de downloads ou filas que o novo contexto Spotify não implementa. Ações válidas: abrir lançamento/faixa no Spotify e reproduzir com matching conservador existente.
- A lógica de Spotify (`browseSpotify`, paginação, erro) permanece separada da apresentação; não duplicar telas genéricas nem sobrescrever as páginas YTM existentes.
- Validar no Redmi: Slipknot e Red Hot Chili Peppers (artista), Californication (álbum completo), volta álbum→artista→Busca, rolagem/paginação, play, menus, mini-player, modo escuro e tipografia ampliada. CI sem inspeção visual não aprova o design.

**Edge-to-edge preservado da v209:** a correção visual Spotify reutiliza `ArtistHero`, `ArtistProfileBackdrop` e `AlbumHero` na janela full-bleed. Botões/ações respeitam os insets de status sem reintroduzir faixa opaca.

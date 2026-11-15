# Sonora — auditoria funcional por área (2026-11-15)

## Autoridade e método

- Base: `FernandoDevCode/N-STOP`, branch canônica `nonstop`, SHA `d0ce0c60b10240f880116db1661ee2644a27dc10`, app v213.
- Comparadas as branches divergentes relevantes por `ahead/behind` e arquivos alterados; inspecionados código efetivo de `nonstop`, contratos, navegação e testes. Nenhuma branch divergente foi integrada.
- As 58 branches divergentes permanecem disponíveis. **Commit exclusivo não prova funcionalidade útil nem ausência dela na `nonstop`.**
- Esta entrega aplica somente **localização e acessibilidade do editor de seções da Home nas Configurações**, reescritas sobre o código vigente, sem cherry-pick. Outras lacunas exigem implementação dedicada.
- Suíte/CI comprovam compilação e regressões automatizadas, **não** homologação real no Redmi, disponibilidade de catálogos externos ou fluidez do movimento.
- A v214 só pode ser considerada publicada após merge e workflow canônico do SHA integrado.

## 1. Busca e motor Spotify / YouTube — prioridade P0

### Integrado à `nonstop` v213

| Elemento | Evidência | Situação |
| --- | --- | --- |
| Busca operacional do app | `feature/search/.../SearchScreen.kt` e `SearchViewModel.kt` | **YTM somente**, resultado `SearchResultSection` em seções, via `YTMusicApiClient.searchAll(query)` |
| Spotify cliente | `data/spotify/.../SpotifyApiClient.kt` | Consulta de catálogo e biblioteca utilizada em outras áreas; não conectada à tela de Busca universal |
| YouTube WEB | `data/ytmusic/.../YouTubeWebSearchClient.kt` e teste | Consulta isolada InnerTube WEB, vídeos e IDs originais, paginação, 429/timeout/cancelamento simulados; sem tela integrada |
| Contrato universal | `feature/search/.../contract/SearchContractModels.kt` | `CatalogSource` Spotify, YouTube e YTM; seleção padrão Spotify+YouTube; estados por fonte e tipos definidos |
| Agregador | `feature/search/.../engine/UniversalSearchEngine.kt` | Puro, ordena/deduplica; **não executa** provedores nem persiste seleção |
| Adaptador YouTube | `feature/search/.../engine/YouTubeWebSearchAdapter.kt` | Apenas VIDEO; ações executáveis vazias até provar fronteira de reprodução |

**Bloqueadores efetivos:** falta orquestrador real multi-fonte, persistência do alfinete e filtros temporários, retries por fonte, tratamento de Spotify sem autenticação, calibração de ranking, UI única mista e testes de integração com dados reais/Android. A Busca atual ainda faz prefetch por scroll e não deve ser simplesmente transplantada para a universal sem revisar a regra de nenhum áudio durante digitação/scroll.

### Branches e reaproveitamento

- `feat/sonora-search-spotify-first-20261114` (83 commits exclusivos na auditoria): **não mesclar**. Contém `LiveSearchSources`, `SpotifyCatalogSearch`, telas de catálogo e tentativa de playback, porém `SonoraSearchViewModel` seleciona uma fonte de cada vez, só Spotify/YouTube; a UI agrupa resultados por tipos, e YTM é proibido. Contradiz o plano vigente (três filtros independentes, lista mista).
- `feat/sonora-search-motor`: conceito de engine/YouTube já tem equivalente integrado; não reimportar.
- `codex/search-checkpoint-0`: bootstrap de contrato já tem equivalente integrado.
- `feat/sonora-consolidate-search-edge-v210-20261115` e outras experiências: comparar apenas regressões e testes específicos; nunca copiar navegação antiga ou sobrescrever Search v213.
- Ideias reaproveitáveis mediante revisão isolada: parser multi-tipo Spotify e testes de catálogo de `SpotifyCatalogSearch.kt`; persistência DataStore do alfinete, redesenhada para **conjunto de fontes** e sem exclusão de YTM; padrões de status/erros, sem herdar viewmodel/tela de fonte única.

### Aceite de próximo patch

Contrato da `docs/sonora/SEARCH_REWORK_PLAN.md` prevalece. Implementar em nova branch derivada da `nonstop` em escopo fechado, preservar IDs separados de catálogo/áudio, versão de vídeo, ações reais, cancelamento de request, retry parcial e estado sem sessão Spotify. Confirmar datas, 401/429, primeira linha, jank e comportamento no Redmi. Não alterar Media3, player, downloads e fila durante o rework.

## 2. Identidade visual / Configurações — prioridade P1

### Já consolidado

- `SettingsHubScreen` possui identidade Sonora, busca e seções organizadas.
- `SettingsScaffold`, `SettingsPageSurface`, `SettingsPageHeader` e `SonoraSettingsCard` são usados em rotas de Configurações; qualidade de áudio não promete aquisição de FLAC.
- `docs/sonora/UI_RULES.md` governa as listas, long press, tokens, temas e inspeção real.
- `tools/check_sonora_identity.py` e `docs/sonora/IDENTITY_PROVIDERS_GATE.md` validam parte dos textos, não todos os composables.

### Lacunas encontradas

- `SettingsAppearanceScreen.kt` trazia rótulos das seções da Home em inglês e sem recursos de idioma, além das descrições de acessibilidade `Move ... up/down`. **Corrigidos nesta entrega** com `stringResource` EN/PT-BR e descrições traduzidas, sem alterar ordenação/armazenamento/UX.
- `core/ui/.../visual/VisualScreens.kt` mantém cores roxas em `VisualMiniPlayer`, `VisualCover`, `LikedArtwork` e outros componentes. Busca de chamadas não comprovou que `VisualMiniPlayer` seja o mini-player de produção. **Não editar cores sem mapear os call sites reais; identificar rota antes do patch.**
- `SettingsLibraryStorageScreen` preserva texto/código de exportação/importação web; exigir auditoria de acesso/flags e provedores externos antes de esconder ou remover componentes — preservar backup local e licenças.
- Outras strings e comentários `Stash` e `Qobuz` incluem IDs de compatibilidade que não devem ser renomeados globalmente. Gate estático não substitui validação em PT-BR/EN, dark/light e Android 16.

### Branch histórica #79

`feat/sonora-brand-settings-cleanup-20261113` contém 42 commits exclusivos relativos à v213 (baseline de auditoria). Trazia reformas extensas em mais de 30 arquivos, vários posteriormente modificados. **Não realizar merge/cherry-pick.** As melhorias válidas de hub, páginas neutras e simplificação de áudio já existem na `nonstop`; tratar residual de aparência por componente atual.

### Aceite da correção desta entrega

Aparência → Organização da Home: rótulos respeitam locale, ação de subir/descer anuncia o rótulo no TalkBack, sem novas seções e sem regressão de switches/ordenação. Compilar recursos EN e PT-BR, executar `feature:settings:testDebugUnitTest`, `assembleDebug`, identidade; validação visual de aparelho continua pendente.

## 3. Home e descoberta — prioridade P1/P2

### Consolidado e conectado à UI

- `HomeScreen` coleciona `weeklyDiscovery`, apresenta `SonoraWeeklyHero` e rails de lançamentos, afinidade recente e redescoberta; também conserva Daily Discover, quick mixes e outros trilhos.
- `SonoraWeeklyDiscoveryEngine` constrói candidatos de Spotify, cruza identidade no YouTube Music e evita candidatos já conhecidos quando possível; usa biblioteca, tags, histórico registrado, Spotify seguindo (quando acessível), cache local e rejeição persistida em `SonoraDiscoveryFeedbackStore`.
- `HomeFeaturedReleaseLookup` existe com prioridade a Spotify e fallback YTM, mas `HomeViewModel.featuredRelease` é declarado com `SharingStarted.Lazily` **sem coletor encontrado em `HomeScreen`**. Não contabilizar esse caminho separado como hero ativo; hoje a UI usa a lista `weeklyDiscovery.releases`.
- `HomeScreen` filtra `NEW_RELEASES`, `QOBUZ_PLAYLISTS`, `TOP_ALBUMS` e `COMMUNITY` do fluxo visível; `HomeViewModel` não deve trazer vitrines Qobuz de volta. Enums/ramificações legados continuam presentes por compatibilidade, não são autorização para UI de Qobuz.

### Ideias ainda não consolidadas

- `feat/sonora-home-v3-follow-artists` (PR #55): `FollowedArtistsStore` local por ID e gesto de seguir/desseguir no perfil **não aparecem na `nonstop`**; candidato de produto para futura implementação, separado do estado de seguir do Spotify.
- `feat/sonora-home-v3-editorial` (PR #56): parte editorial já tem implementação diferente, mas a antiga extração de lançamentos Qobuz e seus cards foram descartados; **não reutilizar**.
- `feat/sonora-weekly-discovery-engine-20261104`: conceitos de taste, feedback, cache e seções já existem no produto atual. Branch divergente não deve substituir o motor atual sem testes diferenciais.
- `feat/nonstop-home-adaptive-v1`, `feat/sonora-home-discovery-rediscovery`, `feat/sonora-home-v3-curation-20261104`: experimentos históricos de layout/curadoria; não importar composição antiga.
- `fix/sonora-home-discovery-fallback-no-qobuz-20261104`: verificar apenas casos de cold start/estados vazios não cobertos hoje; sem reaproveitamento automático.

### Pendências de qualidade

Ausente evidência de recomendação real consistente por gosto e disponibilidade para o usuário: medir cold start, carga de rede, identidade e precisão de faixas por gênero/artista em uso real. Não chamar a heurística de IA ou de classificação musical infalível. Decidir se o seguimento local de artista precisa ser criado; caso sim, projeto novo por ID estável e testes de persistência, sem depender de Qobuz.

## 4. Fotos de artistas, álbum e animações — prioridade P2 para código, P1 para validação

### Fotos já integradas

- `SpotifyArtistPhotoLookup` identifica o Spotify por busca autenticada única ou MusicBrainz com vínculo oficial + oEmbed, e usa ID fixado em revisitas.
- `ArtistPhotoResolutionFinder` busca candidatos Deezer/Wikimedia e `ArtistPhotoPixelMatch` só permite mesma fotografia com resolução efetivamente superior; rede falha mantém fonte anterior.
- `SelectedArtistPhotoRepository` persiste foto por browse ID e expõe `StateFlow` para perfil e Busca; `ArtistProfileViewModel` revalida apenas ao abrir perfil, com override manual por link/arquivo e restauração automática.
- `ArtistHero` usa enquadramento proporcional e fade de fotografia; `ArtistFeaturedAlbum` usa vidro translúcido com tonalidade extraída da arte do álbum em `FeaturedReleaseArtworkTint` — distinto da cor fixa azul-grafite da **Busca**.
- Branches `feat/sonora-artist-image-sync-20261112`, `feat/sonora-spotify-photo-complete-20261112`, `feat/sonora-rated-artist-photos` e experimentos Last.fm são históricos ou caminhos anteriores à política oficial: **não importar** seletor por rating para substituir foto oficial.

### Animações já integradas tecnicamente

- `StashAudioOutput` faz observação limitada e não destrutiva em `PlaybackVisualEnergy`; `MusicFeatureAnalyzer` produz tendências de áudio; `V8PlayerSurface` assina/desassina, usa `MotionProfileCache`, fornece `MusicalMotionSource` a `V8Renderer` / `OrganicMotionField`.
- `NowPlayingScreen` escolhe `V8PlayerSurface` quando há faixa, sem modo clássico/leitura/grupo; mantém fallback V7. Não afirmar que V8 é exibido em 100% das condições.
- A timeline óptica está integrada com seek, progresso e modo de movimento reduzido.
- `SonoraColdStartIntro` já prepara camadas bitmap fora do desenho por frame, mas **FPS real e travamentos no Redmi não foram medidos**. Não reimportar a branch antiga `fix/sonora-startup-animation-frame-pacing` por SHA.
- `SonoraSearchAtmosphere` v213: azul `0xFF19475B` independente da foto; fundo desenhado na raiz abaixo da status bar; percurso suave sem loop e atualização até ~30fps. Validar visualmente no dispositivo; teste de trajetória não é teste de legibilidade ou FPS.

### Aceite de homologação

No Redmi: inspecionar mesma foto Spotify no perfil/Busca, tentativa de maior resolução sem trocar pose, foto manual/restauração, tint translúcido no card do álbum, corte/fade em U e status bar. Para animações: medir FPS e drops na abertura, player e Busca, com 3 músicas de referência e pelo menos 6 desconhecidas, headphones/BT, redução de movimento, pause/background, energia e sem perda de áudio.

## Decisões de triagem das branches divergentes

| Família | Tratamento |
| --- | --- |
| Busca Spotify-first / sonoras de motor | **Aproveitar ideias/testes somente após adaptação ao contrato universal vigente** |
| Identidade e settings (#79) | **Base mais recente prevalece; não mesclar** |
| Home V3 / Qobuz (#55/#56/#61) | **Não trazer UI/serviços antigos**; seguir artista local por ID é candidato separado |
| Weekly discovery | **Já há implementação ativa diferente**; validar qualidade real antes de mudar |
| Fotos Spotify | **Contrato atual prevalece**; descartar seletor estético/rating como autoridade |
| Art Lab / animações / intro | **Código integrado parcialmente ou em caminho opt-in**; validação por dispositivo antes de alteração profunda |
| Branches `research/*` | Experimentos históricos: não integrar automaticamente |
| `release/*`, `fix/sonora-v*`, `feat/sonora-v4*` | Estados visuais/release antigos: não reverter versões novas |

Nenhuma branch divergente foi removida nesta auditoria. Reclassificar e excluir apenas após decisão de recuperação/descarte e referência preservada em registro, nunca com exclusão automática de todo o grupo.

## Sequência recomendada

1. **P0:** conectar motor multi-fonte à Busca universal sob contrato atual; preservar IDs e provas de reprodução, autenticação, retry/timeout, regressão de UX e desempenho.
2. **P1:** corrigir rótulos PT-BR/EN e a11y do editor da Home (**neste PR**); auditar apenas o roxo que está em call site produtivo e eventuais links legados acionáveis.
3. **P1:** validar Home semanal na prática e decidir feature local de seguir artistas por ID, separada do Spotify; eliminar confusão entre materializado e apenas codificado.
4. **P2:** homologar foto, card do álbum, frame pacing e motion em aparelhos, com métricas; só então mexer em fórmulas ou camadas.
5. **Higiene:** após recuperação de código ainda útil e aprovação, fechar definitivamente branches divergentes obsoletas; manter `nonstop`, tags, releases e histórico.

## Critérios de não regressão

Sem reintrodução Stash/NØNSTOP/Qobuz na UI, sem alterar identidade/IDs de catálogo e áudio, sem alteração de reprodução, Media3, downloads, dados locais e assinatura por conveniência; CI e APK sempre do SHA integrado. Não declarar aprovação visual sem captura/teste real no Redmi.

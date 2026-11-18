# Sonora — inventário operacional antirretrabalho (2026-11-18)

**Objetivo:** evitar que planos, PRs encerrados e branches antigas sejam interpretados como funcionalidades ainda não implementadas. Este documento é um **snapshot auditado**, não substitui a verificação do código na `nonstop` antes de cada mudança.

- Repositório: `FernandoDevCode/N-STOP`.
- **Base auditada:** `nonstop`, SHA `33e6b03df562945eafc03627478eff7c30222a2f` (merge do PR #98).
- **PRs em aberto na data do inventário, antes deste registro:** zero.
- **Último APK canônico publicado:** `sonora-preview-v214-30838d8949c8`, SHA `30838d8949c823900e07269d69e7807512302c8e` (inclui #97, **não** inclui #98).
- **PR #98:** integrado, 10 checks não ignorados aprovados no SHA de origem, incluindo testes modulares, compile, debug e identidade. O push do merge aprovou o gate de identidade. **Nenhum APK do SHA pós-#98 foi publicado nesta data.**
- **Decisão de trabalho atual:** continuar integrando com testes automáticos; adiar a homologação física e a nova pré-release até a rodada final consolidada. Isso não permite chamar UX, FPS ou reprodução real de homologados.

## Critérios de leitura

- **Integrado:** código encontrado na branch canônica e caminho ativo identificado. Não implica teste em aparelho.
- **Parcial / limitação conhecida:** solução ativa com limite funcional documentado.
- **Pendente real:** ausência ou inconsistência confirmada no código em uso, com escopo de correção identificável.
- **Somente validação:** código integrado; só alterar após evidência prática de desvio.
- **Ideia não aprovada / histórico:** PR fechado sem merge ou experimento divergente; não abrir tarefa automaticamente.
- Ordem normativa: `AGENTS.md`, contratos específicos (`docs/sonora/SEARCH_REWORK_PLAN.md`, `docs/sonora/UI_RULES.md`, `docs/player-v8/...`) e, para **estado**, este inventário revalidado. A auditoria de 15/11 é histórica, pois contém lacunas já encerradas.

## 1. Matriz de entregas efetivas

| Área / objetivo | Estado em `nonstop` | Evidência / limite |
| --- | --- | --- |
| Busca universal, lista única, filtros independentes, preferências/alfinete, histórico, estados e retry por fonte | **Integrado** | PRs [#94](https://github.com/FernandoDevCode/N-STOP/pull/94) e [#95](https://github.com/FernandoDevCode/N-STOP/pull/95); `SearchScreen`, `SearchViewModel`, `UniversalSearchSession`, `SearchSourcePreferences` |
| Spotify como filtro musical visual; YouTube convencional e YouTube Music; uso de metadados/capas Spotify | **Integrado conforme contrato, não é streaming Spotify** | PR [#96](https://github.com/FernandoDevCode/N-STOP/pull/96). Spotify musical é roteamento simbólico para catálogo YTM, não um provedor independente de arquivo de áudio |
| Toque para reprodução exata de TRACK/VIDEO aptos na Busca, preservando `videoId` e versão | **Integrado com limite** | PR [#97](https://github.com/FernandoDevCode/N-STOP/pull/97); `SearchViewModel` → `PlayerRepository.playExactSearchResult`. **Retomada após morte do processo** ainda reconstitui a fila pela identidade geral da biblioteca; a garantia de exatidão nesse cold restart não foi estendida |
| Linha compacta de músicas, gestos e long press nas superfícies já reformadas | **Integrado em código** | PRs [#83](https://github.com/FernandoDevCode/N-STOP/pull/83) e [#85](https://github.com/FernandoDevCode/N-STOP/pull/85); `SongRow`, `DetailTrackRow`, `TrackListItem`. Gestos e legibilidade reais continuam sem homologação |
| Identidade Sonora nos principais submenus e traduções da Aparência/Equalizador/Diagnósticos/Saúde da Biblioteca | **Integrado em código** | PRs [#82](https://github.com/FernandoDevCode/N-STOP/pull/82), [#85](https://github.com/FernandoDevCode/N-STOP/pull/85), [#93](https://github.com/FernandoDevCode/N-STOP/pull/93), [#98](https://github.com/FernandoDevCode/N-STOP/pull/98). Não considerar cada string ou rota visual homologada |
| Home: descoberta semanal, faixas atuais/fora do radar, lançamentos, feedback e cache | **Integrado e conectado à Home** | `HomeScreen.weeklyDiscovery`, `SonoraWeeklyDiscoveryEngine`, `SonoraDiscoveryFeedbackStore`; o PR [#60](https://github.com/FernandoDevCode/N-STOP/pull/60) foi **merged** em 04/11; o código vigente evoluiu depois |
| Home: artistas seguidos no Spotify como candidatos de descoberta | **Integrado como insumo** | `SonoraWeeklyDiscoveryEngine` usa `spotify.getFollowedArtistNames(12)` mais histórico/biblioteca. Não prova que todo artista seguido terá lançamento em destaque |
| Home: componente separado `HomeFeaturedReleaseLookup` | **Parcial / caminho não consumido** | `HomeViewModel.featuredRelease` é `SharingStarted.Lazily`; nenhuma coleta correspondente foi encontrada em `HomeScreen`. O hero ativo provém de `weeklyDiscovery`, não desse fluxo |
| Seguir/desseguir artistas **localmente** no Sonora | **Não integrado / decisão de produto não consolidada** | PR [#55](https://github.com/FernandoDevCode/N-STOP/pull/55) fechado sem merge. Não confundir com o conjunto de artistas seguidos no Spotify |
| Fotografia Spotify como autoridade, maior resolução somente da mesma fotografia, seleção persistente perfil/Busca, edição por link/arquivo | **Integrado em código** | `SpotifyArtistPhotoLookup`, `ArtistPhotoResolutionFinder`, `ArtistPhotoPixelMatch`, `SelectedArtistPhotoRepository`, `ArtistProfileViewModel`; verificar fotos reais e falhas de fonte no Android |
| Card de álbum em vidro translúcido com tint da capa, hero de artista/fade | **Integrado em código** | `ArtistFeaturedAlbum`, `FeaturedReleaseArtworkTint`, `ArtistHero`. Não alterar sem evidência visual |
| Art Lab V8/movimento orgânico de áudio, timeline, intro cinematográfica, atmosfera da Busca | **Integrado tecnicamente** | `V8PlayerSurface`, `PlaybackVisualEnergy`, `MotionProfileCache`, `OrganicMotionField`, `SonoraColdStartIntro`, `SearchAtmosphere`. V8 mantém fallback; FPS, fidelidade e fluidez não foram medidos no celular |
| Exclusão de vitrines Qobuz e sync web antigo da experiência ativa | **Barreiras em código verificadas** | `HomeViewModel.discoveryFlow` retorna dados vazios e filtra seções; `HomeScreen` também filtra; `WEB_LINK_ENABLED=false` em debug/release e o NavHost oculta o link. Símbolos internos legados não equivalem a produto exposto |
| GitHub/release canônica | **Integrado** | `nonstop` é fonte de verdade; workflow de preview só publica APK do commit integrado. Novo APK pós-#98 está propositalmente adiado |

## 2. Pendências reais e próximas ações

| ID | Prioridade | Problema/limite comprovado | Escopo aceitável para a próxima tarefa |
| --- | --- | --- | --- |
| **S-01** | P1 | `SettingsLibraryStorageScreen.kt`: rótulos e descrições das opções de exportação/importação, confirmações destrutivas e mensagens de reorganização ainda são strings literais em inglês, exibidas por diálogos e estados ativos | Localizar EN/PT-BR, preservar enums `BackupExportScope`/`BackupImportScope`, caminhos, nomes de arquivo, decisões destrutivas, permissões e comportamento do backup. Testar strings + Settings + identidade |
| **S-02** | P1 | `SettingsAppearanceScreen.kt`: descrição TalkBack do toggle da Home ainda contém `Show $label on Home` literal | Migrar apenas descrição semântica para recurso EN/PT-BR, preservando estado e ordenação |
| **S-03** | P1 | `ArtistProfileScreen.kt`: diálogos de edição manual de fotografia ainda têm textos fixos em português; `HomeScreen.kt`: `Na sua frequência`/`Fora do radar` são textos fixos | Localizar os textos, sem tocar na escolha/persistência de fotos ou no algoritmo/ordem dos rails |
| **S-04** | P2, condicionada a validação do call site | `VisualScreens.kt:VisualCover` mantém fundos lilases; `V8PlayerSurface` chama `VisualCover(null)` como fallback sem capa. `VisualMiniPlayer` usa cores legadas, mas só foi encontrado como chamada interna de `VisualScreens`, não como mini-player produtivo | Auditar visual em rotas efetivas; se necessário, neutralizar exclusivamente fallback produtivo e testar dark/light/AMOLED. Não fazer substituição global em `VisualScreens` nem renomear tokens `Stash*` por aparência de código |
| **S-05** | P2, decisão funcional adicional | Retomada fria da Busca não persiste contrato de resolução exata | **Não é bug novo de #97:** somente implementar se o produto exigir exatidão também após morte do processo; requer especificação de persistência, queue restore e testes cross-process. Não ampliar patch de UI para media |
| **S-06** | Ideia aguardando decisão, não tarefa ativa | Seguimento local de artistas e hero dedicado de `HomeFeaturedReleaseLookup` não são o caminho atualmente usado pela Home | Avaliar necessidade real depois de validar a Home atual. Não mesclar #55/#56; evitar uma segunda descoberta semanal ou substituir `weeklyDiscovery` |
| **S-07** | Higiene, sem prioridade funcional | Arquivos/comentários, strings de compatibilidade e branches históricas mantêm nomes Stash/Qobuz; algumas rotas estão desativadas | Só agir diante de referência **visível e alcançável**, destino externo acionável ou risco de serviço ativo; não fazer remoção/rename global de identidades persistidas |

## 3. Apenas validação física — não são implementações ausentes

- **Busca/áudio:** verificar vídeo original em demos, lives, covers, remixes; alternância rápida e mini-player; comportamento de rede, Wi-Fi/BT, pausa/retomada; sem aceitar troca silenciosa de gravação.
- **Home:** qualidade da curadoria semanal, primeiros resultados, vazios sem sessão Spotify, afinidade, lançamento de seguido, rejeição persistida, latência e capa real.
- **Artista/álbum:** retrato Spotify e maior resolução da mesma foto; sincronismo com Busca, override/restauração, crop e fade em U; card translúcido.
- **Animações:** medir frame pacing/FPS na intro, Busca, player V8 e mini-player com faixas variadas, pausa, seek e redução de movimento; não interpretar sucesso unitário como prova de fluidez.
- **Geral:** Android real (Redmi/HyperOS), dark/light, EN/PT-BR, TalkBack, fontes grandes, status bar e teclado.
- **CI intermitente conhecido:** o teste Robolectric `MiniPlayerRenderTest.renderProductionMiniPlayerWithStationaryPlaybackPosition` falhou uma vez no primeiro preview do SHA `30838d89` e passou na repetição, permitindo a release. Não alterar animação/teste por hipótese sem reprodução/diagnóstico.

## 4. Branches/PRs que NÃO devem voltar para a fila automaticamente

- **Aproveitados/reimplementados de outra forma:** PRs #94–#98; não refazer motor, UI da Busca nem a reprodução exata.
- **Fechados sem merge:** [#55](https://github.com/FernandoDevCode/N-STOP/pull/55) (seguir localmente), [#56](https://github.com/FernandoDevCode/N-STOP/pull/56) (Home editorial antiga), [#61](https://github.com/FernandoDevCode/N-STOP/pull/61) (fallback antigo), [#79](https://github.com/FernandoDevCode/N-STOP/pull/79) (settings divergente) e [#86](https://github.com/FernandoDevCode/N-STOP/pull/86) (Busca fonte única). Fechamento não é integração nem evidência de pendência atual.
- **PR #60:** foi merged; a branch divergente posterior não deve sobrescrever os componentes semanais atuais.
- O registro histórico de divergências está em `docs/sonora/GITHUB_CLEANUP_AUDIT_2026-11-15.md`. Não fazer cherry-pick/merge em massa, reset ou exclusão de branches por inferência.

## 5. Fila operacional e prevenção de duplicação

1. Tratar **S-01** em um patch fechado, com recurso localizado, testes, gate de identidade e merge na `nonstop`; **sem APK intermediário**.
2. Tratar **S-02/S-03** como correções localizadas de recursos, sem alterações funcionais; revisar gestos, acesso e strings em EN/PT-BR no CI.
3. Inspecionar **S-04** apenas em call sites produtivos. Não alterar mini-player, fotos, player V8 ou paleta global preventivamente.
4. **S-05/S-06** não são decisões fechadas; não delegar à implementação sem escopo de produto aprovado.
5. Manter **uma única pré-release de homologação ao final**; QA física identifica regressões reais, e correções posteriores exigem novo PR/testes.
6. Antes de abrir uma nova tarefa: verificar SHA de `nonstop`, PRs em aberto, commits recentes, testes e call sites. Reclassificar um item deste snapshot quando uma mudança posterior já o tiver resolvido; o mesmo objetivo não pode virar novo PR só porque ainda consta em plano histórico.
7. Não duplicar regras de arquitetura/UX deste documento: para implementações consultar `AGENTS.md`, `SEARCH_REWORK_PLAN.md`, `UI_RULES.md` e documentos específicos do V8.

## 6. Fontes principais da verificação

- [Histórico de commits `nonstop`](https://github.com/FernandoDevCode/N-STOP/commits/nonstop/) e [PR #98](https://github.com/FernandoDevCode/N-STOP/pull/98).
- [Busca](https://github.com/FernandoDevCode/N-STOP/tree/nonstop/feature/search), [Home](https://github.com/FernandoDevCode/N-STOP/tree/nonstop/feature/home), [Settings](https://github.com/FernandoDevCode/N-STOP/tree/nonstop/feature/settings), [Now Playing](https://github.com/FernandoDevCode/N-STOP/tree/nonstop/feature/nowplaying).
- [Gate de identidade](https://github.com/FernandoDevCode/N-STOP/blob/nonstop/docs/sonora/IDENTITY_PROVIDERS_GATE.md) e [última pré-release v214](https://github.com/FernandoDevCode/N-STOP/releases/tag/sonora-preview-v214-30838d8949c8).

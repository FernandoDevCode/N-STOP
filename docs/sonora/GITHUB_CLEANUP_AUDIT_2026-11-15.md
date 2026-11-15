# Sonora — auditoria de organização do GitHub (2026-11-15)

## Estado oficial

- Repositório: `FernandoDevCode/N-STOP`; branch padrão e única integração: `nonstop`.
- Ponto de auditoria: `20a7f37a9e3f779a579e5b87721da08c3049736b` — Sonora v213, APK Debug canônico.
- Nenhum reset/force-push, merge em massa ou exclusão de tags/releases integra esta limpeza.
- A v208 permanece referência para rollback visual; v211/v212/v213 e releases anteriores continuam arquivadas e imutáveis.
- O clone local Windows foi sincronizado com v213; alterações anteriores estão em stash, que **não** deve ser aplicado automaticamente.
- Conteúdo de branches divergentes **não é equivalente a funcionalidade pendente**. É necessário revisar código atual e escolhas de produto antes de qualquer reaproveitamento.

## Auditoria de branches

Comparação de cada uma das 101 branches não padrão contra o SHA v213 usando GitHub Compare (ahead/behind):

- 43 branches com `ahead_by=0` e `status=behind`: commits já ancestrais da `nonstop`; **referências remotas candidatas a eliminação**, sem perda de commits da linha canônica.
- 58 branches com `status=diverged` e commits exclusivos: **preservar** até decidir explicitamente por integração seletiva ou descarte.
- 1 `nonstop` — nunca excluir ou reescrever.
- Excluir branch não significa remover seus commits ou tags automaticamente, mas commits exclusivos sem referência podem se tornar difíceis de recuperar; portanto é proibida a deleção em massa das 58 divergentes.

### 43 candidatas à remoção segura de referência

Verificadas como ancestrais de `20a7f37a9e3f` no momento da auditoria; **revalidar contra `origin/nonstop` imediatamente antes de deletar**:

- `checkpoint/sonora-local-20261029`
- `chore/sonora-consolidacao-20261101`
- `codex/sonora-artista-layout`
- `feat/nonstop-checkpoint-1`
- `feat/nonstop-checkpoint-2`
- `feat/nonstop-v8-art-lab`
- `feat/player-v7-art-direction`
- `feat/sonora-apple-music-wordmarks`
- `feat/sonora-artist-adaptive-atmosphere`
- `feat/sonora-artist-album-neutral`
- `feat/sonora-artist-smoked-glass-spacing`
- `feat/sonora-artist-v2-ambient-fade`
- `feat/sonora-artist-wordmark-sources`
- `feat/sonora-audiobooks-youtube-chapters`
- `feat/sonora-identity-providers-unification-20261113`
- `feat/sonora-integrated-identity-search-ime-20261113`
- `feat/sonora-optical-timeline-v2-20261112`
- `feat/sonora-photo-visual-ranker`
- `feat/sonora-search-atmosphere-v212`
- `feat/sonora-search-track-actions-v1-20261113`
- `feat/sonora-spotify-artist-photo-overrides`
- `feat/sonora-v16-integration-chrome-photos`
- `feat/sonora-v17-ptbr-dates-copy`
- `feat/sonora-v18-transparent-statusbar`
- `feat/sonora-v19-iconic-artist-identity`
- `feat/sonora-v198-rhythmic-optical-ridge`
- `feat/sonora-v208-unified-rows-settings-20261113`
- `fix/sonora-artist-v2-ambient-scroll-fade`
- `fix/sonora-artist-v2-device-visual`
- `fix/sonora-search-atmosphere-v213`
- `fix/sonora-search-ime-jank-20261113`
- `fix/sonora-v20-artist-identity-statusbar`
- `fix/sonora-v21-artist-hero-statusbar`
- `fix/sonora-v22-2-photo-hero-u-fade`
- `fix/sonora-v22-iconic-artist-hero`
- `fix/sonora-v23-artist-profile-fidelity`
- `fix/sonora-v24-clear-artwork-cache`
- `fix/sonora-v197-optical-artwork-continuity`
- `fix/sonora-v199-native-ridge-compositing`
- `fix/sonora-v204-featured-glass-color-strength`
- `integration/sonora-v195-motion-v8-20261112`
- `recovery/sonora-v208-baseline-20261115`
- `release/sonora-spotify-photo-v181`

### 58 branches divergentes — preservar até revisão

- `chore/sonora-v202-artist-glass-apk`
- `ci/sonora-experimental-releases`
- `codex/search-checkpoint-0`
- `docs/sonora-roadmap-migracao-1-0-20261104`
- `feat/nonstop-home-adaptive-v1`
- `feat/sonora-artist-edge-continuation`
- `feat/sonora-artist-editorial-finish`
- `feat/sonora-artist-image-sync-20261112`
- `feat/sonora-artist-organic-atmosphere`
- `feat/sonora-artist-v2-navigation`
- `feat/sonora-brand-settings-cleanup-20261113`
- `feat/sonora-branding-icon-v182`
- `feat/sonora-cinematic-startup`
- `feat/sonora-consolidate-search-edge-v210-20261115`
- `feat/sonora-exact-icon-and-apk`
- `feat/sonora-featured-artwork-tinted-glass-v203`
- `feat/sonora-final-launcher-artwork`
- `feat/sonora-home-discovery-rediscovery`
- `feat/sonora-home-v3-curation-20261104`
- `feat/sonora-home-v3-editorial`
- `feat/sonora-home-v3-editorial-20261103`
- `feat/sonora-home-v3-follow-artists`
- `feat/sonora-rated-artist-photos`
- `feat/sonora-search-motor`
- `feat/sonora-search-spotify-first-20261114`
- `feat/sonora-spotify-photo-complete-20261112`
- `feat/sonora-v3-after-refactor`
- `feat/sonora-v4-4-artist-destinations-ui`
- `feat/sonora-v4-photo-backdrop`
- `feat/sonora-v4-photographic-canvas`
- `feat/sonora-weekly-discovery-engine-20261104`
- `fix/sonora-approved-icon-fidelity-v183`
- `fix/sonora-artist-featured-photo-tint`
- `fix/sonora-artist-tabs-scroll-with-content`
- `fix/sonora-artist-v3-audit-fade-anchor`
- `fix/sonora-artist-v3-chromatic-canvas`
- `fix/sonora-global-status-edge-to-edge-20261115`
- `fix/sonora-home-discovery-fallback-no-qobuz-20261104`
- `fix/sonora-home-v3-editorial-fallback-20261104`
- `fix/sonora-icon-assets-v184`
- `fix/sonora-preview-idempotent-release`
- `fix/sonora-startup-animation-frame-pacing`
- `fix/sonora-v4-1-hero-u-seam`
- `fix/sonora-v4-2-floating-tabs-scrim`
- `fix/sonora-v4-3-pinned-tabs-readable`
- `fix/sonora-v4-photo-diffusion-sticky-tabs`
- `fix/sonora-v18-statusbar-continuity`
- `refactor/sonora-artist-sections`
- `refactor/sonora-artist-sections-clean`
- `release/sonora-0-9-142-consolidated`
- `release/sonora-v4-4-consolidated`
- `research/sonora-apple-artist-assets`
- `research/sonora-lastfm-gallery-probe`
- `research/sonora-lastfm-retry-probe`
- `research/sonora-lastfm-ten-artist-audit`
- `research/sonora-lastfm-top5-preview`
- `research/sonora-pinterest-probe`
- `research/sonora-podbox-art-fetch-probe`

## PRs abertos encontrados antes do arquivamento

Foram identificados cinco PRs antigos, todos divergentes de `nonstop`. O fechamento administrativo **não constitui merge ou aprovação**. A discussão, commits e diff permanecem disponíveis pelo PR e respectivas branches.

| PR | Trabalho original | Situação em v213 e próximo tratamento |
|---|---|---|
| [#79](https://github.com/FernandoDevCode/N-STOP/pull/79) | Remoção de Stash visível, identidade Sonora e reestruturação das Configurações | **Parcialmente reaproveitado em outros commits, ainda há diferenças exclusivas (42 commits à frente).** Reavaliar na `nonstop` por UX/funcionalidade antes de qualquer reimplementação; não mesclar patch antigo |
| [#62](https://github.com/FernandoDevCode/N-STOP/pull/62) | `docs/SONORA_ROADMAP_MIGRACAO_1_0.md` (F0–F5) | Documento original não integrado; fonte histórica consultável no PR, não trata de estado atual v213 |
| [#61](https://github.com/FernandoDevCode/N-STOP/pull/61) | Home fallback e retirada de vitrines Qobuz | Design da Home evoluiu; conferir as lacunas reais em `nonstop` antes de reaplicar qualquer código; não retornar vitrines Qobuz |
| [#56](https://github.com/FernandoDevCode/N-STOP/pull/56) | Home V3 editorial / releases de seguidos | Depende de #55; contém integrações e seções Qobuz incompatíveis com direcionamento posterior; somente referência histórica |
| [#55](https://github.com/FernandoDevCode/N-STOP/pull/55) | Seguir/desseguir artistas por ID | Funcionalidade potencialmente recuperável, mas implementação antiga não aprovada nem integrada; precisa escopo atualizado e avaliação isolada |

Ao arquivar, registrar comentário com motivo e link para esta auditoria, **sem excluir a branch de origem**. Se surgir necessidade, implementar nova alteração com base na `nonstop` atual, em PR independente e com testes.

## Triagem prioritária das divergências

1. `feat/sonora-search-spotify-first-20261114` (83 commits exclusivos): alto risco de trazer de volta a regressão da Busca v210; nunca mesclar automaticamente.
2. `feat/sonora-brand-settings-cleanup-20261113` (PR #79): ver se sobrou algum submenu com marca antiga, sem trocar motor ou rotas.
3. `feat/sonora-artist-image-sync-20261112` e `feat/sonora-spotify-photo-complete-20261112`: comparar com contrato de foto da v213 antes de considerar patches.
4. `feat/sonora-weekly-discovery-engine-20261104`, `feat/sonora-home-v3-follow-artists`, `feat/sonora-home-v3-editorial`: curadoria/seguir artistas ainda requer revisão funcional e das fontes.
5. `fix/sonora-startup-animation-frame-pacing` e `feat/sonora-featured-artwork-tinted-glass-v203`: verificações visuais futuras, não integradas automaticamente.

## Limpeza operacional segura

1. Preferir branch curta de trabalho criada do HEAD atual da `nonstop`; PR direto para ela; excluir branch de trabalho **após merge e verificação**.
2. Não gerar APK de branch experimental como se fosse release consolidada. Workflow canônico gera APK do SHA integrado; testes de CI não substituem homologação no Redmi.
3. Para remover 43 branches integradas: rodar script local que valida `origin/nonstop`, `git merge-base --is-ancestor`, recusa proteção/open PR e solicita ação explícita; nunca `git push --mirror`, `git branch -D` em massa nem `reset --hard`.
4. Não executar `git stash pop` do backup anterior. Revisar antes o conteúdo do stash com `git stash show --stat 'stash@{0}'`.
5. Revisar periodicamente este registro após limpeza; contagens acima são um **snapshot** e diminuem após remoções.

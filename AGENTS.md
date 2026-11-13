# Sonora — instruções de contexto para agentes

## Tarefas do Player v8 / Art Lab
- Leia PRIMEIRO `docs/player-v8/README.md` e depois `docs/player-v8/PLAYER_V8_ART_LAB_SPEC.md`.
- A única especificação operacional de v8 é `docs/player-v8/PLAYER_V8_ART_LAB_SPEC.md`. Não misture instruções de v7, checkpoints anteriores ou `docs/superpowers/plans`.
- Referências VISUAIS aprovadas são EXCLUSIVAMENTE `docs/player-v8/references/MASTER_MEGADETH.png` e `MASTER_PINK_FLOYD.png`, conforme `references/manifest.json`; verifique hash e existência. **Se faltarem, pare a construção visual e informe a ausência.**
- As imagens em `docs/visual-reviews/checkpoint-2/` são capturas da implementação antiga: úteis para regressão, NUNCA como imagens-mestras.
- Não tocar em Media3, reprodução, autenticação, downloads, mini-player, PlayerV7 ou navegação quando a tarefa for v8. Trabalhar em módulo/laboratório isolado; sem commit/push/merge sem aprovação específica.
- Nunca criar exceções por álbum/artista para ajustar o motor de arte. Primeiro provar com Pink Floyd e Megadeth; depois testar generalização.
- Não prometer 1:1 nem alegar IA ativa sem inspeção/medição real.

## Outras tarefas do repositório
Estas instruções de v8 não substituem as regras técnicas gerais do projeto nem autorizam refatorações fora do escopo pedido.
- Para tarefas da Busca Sonora, `docs/sonora/SEARCH_REWORK_PLAN.md` é a especificação vigente de produto e do contrato técnico.

## Sonora — fluxo canônico (desde 2026-11-01)
- Repositório `FernandoDevCode/N-STOP`: a branch padrão e única fonte integrada para trabalho futuro é `nonstop`. Não presuma `main`; confirme a branch padrão antes de atuar.
- A partir do checkpoint V22.2, toda tarefa nova deve ramificar de `nonstop`, testar, abrir PR **para `nonstop`** e integrá-lo antes de chamar o trabalho de concluído. Não encadear PRs entre branches de pré-release.
- As branches `feat/*`, `fix/sonora-v*`, `checkpoint/*`, `research/*` anteriores ao checkpoint são históricas. Não extrair código nem fazer merges automáticos dessas branches sem auditar alterações e decisões de produto posteriores.
- Publicar APK de validação usando o commit que foi integrado ao fluxo canônico; release/pré-release são artefatos e **não** significam que a branch de origem tenha sido integrada.
- Preserve os IDs de aplicativo, a assinatura, os dados do usuário e a distinção Debug/Release. Não reverter decisões mais novas por commits antigos.
- O estado consolidado, testes e exceções estão documentados em `docs/SONORA_CONSOLIDATION.md`. O teste visual das fotos/fade no Redmi é uma pendência distinta do sucesso do CI.

## Identidade e provedores em qualquer alteração de UI
- Execute `python3 tools/check_sonora_identity.py` e revise `docs/sonora/IDENTITY_PROVIDERS_GATE.md` antes do merge; não permita nova referência Stash/NØNSTOP/Qobuz na experiência visível.
- Não relacione fonte de catálogo ao provedor do áudio; preservar os contratos da Busca. Não chamar YouTube de FLAC nem substituir endpoints legados por domínios inventados.
- Verificação estática não substitui inspeção visual em aparelho. Preservar licenças GPL e dados internos.

## Sonora — regras de frontend
- Em toda mudança de Compose/UX, ler `docs/sonora/UI_RULES.md`: semântica de toque/pressão longa, tokens compartilhados e validações por rota; o documento não altera contratos de busca nem Player v8.

# NØNSTOP — Player v8 / Art Lab

**Esta é a porta de entrada única do novo trabalho visual.** Branch: `feat/nonstop-v8-art-lab`.

## Documentos vigentes
- **Especificação normativa única:** [PLAYER_V8_ART_LAB_SPEC.md](PLAYER_V8_ART_LAB_SPEC.md)
- **Referências mestras:** [references/README.md](references/README.md) + [references/manifest.json](references/manifest.json)
- **Importador local das imagens:** [IMPORT_REFERENCES_V8.ps1](IMPORT_REFERENCES_V8.ps1)
- **Política para agentes:** [../../AGENTS.md](../../AGENTS.md)

## Regra sem ambiguidade
1. As referências mestras de Megadeth e Pink Floyd são os PNGs originais aprovados do pacote `NONSTOP_checkpoint_2_player_v7_referencias.zip`, NÃO fotos da tela, screenshots do APK nem ilustrações buscadas pela web.
2. A norma vigente está **somente aqui**. `docs/PLAYER_V7_VISUAL_FIDELITY.md`, `docs/player-v7-visual-spec.md` e `docs/visual-reviews/checkpoint-2/` são históricos para comparar **implementação antiga**. Não adicionar seus requisitos estéticos ao v8.
3. Não iniciar reconstrução da UI sem **verificar a existência e SHA-256** das duas referências mestras. Não inventar substitutos.
4. A geometria busca aproximar as referências 1:1; em capas desconhecidas o mesmo renderer/engine deve generalizar, sem `if (artist == ...)`.
5. Projeto experimental Desktop/Compose Multiplatform primeiro; Android apenas após aprovação, player atual preservado.

## Estrutura
```text
docs/player-v8/
  README.md
  PLAYER_V8_ART_LAB_SPEC.md
  IMPORT_REFERENCES_V8.ps1
  references/
    README.md
    manifest.json
    MASTER_MEGADETH.png         # após importação no PC e publicação
    MASTER_PINK_FLOYD.png      # após importação no PC e publicação
```

### Por que os PNGs não aparecem imediatamente?
Os binários originais estavam no material local `02_CHECKPOINT_2/`, que não havia sido versionado no GitHub. Para não substituir os mestres por JPEGs recompactados ou capturas de tela, use o importador, que verifica hash e copia **os PNGs exatos**. Pode opcionalmente publicar apenas essas duas imagens, sem arrastar arquivos locais irrelevantes.

## Histórico preservado (não normativo)
- `docs/PLAYER_V7_VISUAL_FIDELITY.md` — fidelidade do checkpoint anterior.
- `docs/player-v7-visual-spec.md` — decisões anteriores, inclusive adendos contraditórios.
- `docs/visual-reviews/checkpoint-2/` — capturas reais de Android v7.
- `docs/superpowers/plans/` — documentação histórica do fork.
- `01_CHECKPOINT_1/`, `02_CHECKPOINT_2/` — pastas locais de material, quando existirem (não presumir que estão no GitHub).

**Nenhum desses materiais foi apagado.** O arquivo de especificação v8 anteriormente em `docs/PLAYER_V8_ART_LAB_SPEC.md` foi realocado para esta pasta para impedir duplicidade.

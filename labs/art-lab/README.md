# NØNSTOP Art Lab v8 — Fase 1B

Laboratório Kotlin/Compose Multiplatform isolado. A norma permanece
`../../docs/player-v8/PLAYER_V8_ART_LAB_SPEC.md` (1.1).
**Análise clássica de pixels, sem modelo neural. Aceite visual pendente / NOT PASSED.**

## Windows

Na raiz do repositório, com JDK 21 no `JAVA_HOME`:

```powershell
.\gradlew.bat -p labs/art-lab run
.\gradlew.bat -p labs/art-lab desktopTest
.\gradlew.bat -p labs/art-lab createDistributable
```

Executável com runtime Java incluído:

```powershell
Push-Location labs/art-lab
& .\build\compose\binaries\main\app\NonstopArtLab\NonstopArtLab.exe
Pop-Location
```

Copiar toda a pasta `NonstopArtLab`, incluindo `app` e `runtime`, se mudar o executável de lugar.
O primeiro build resolve dependências via rede; o app e o motor funcionam sem rede, conta ou áudio.
Não há instalador MSI ou assinatura. Nenhum módulo Android/produção é dependência deste build.

## Benchmark restrito a duas capas

Pink Floyd e Megadeth usam capas completas **1200×1200**, verificadas no catálogo Apple Music/iTunes.
URL da edição, endpoint, copyright e SHA-256 estão em `cover-sources.json`. São artes das edições
catalogadas, não escaneamentos lossless nem uma licença de redistribuição irrestrita.
As imagens dos mockups são diferentes e continuam como mestres de composição. Os recortes
históricos não são entrada de análise nem de exibição do motor 1B.

As outras dez entradas continuam `missing`, sem novas capturas/análises nesta rodada.
Carregar PNG/JPEG local continua funcionando e aplica a mesma política de análise; a entrada
local não altera as 12 entradas do catálogo fixo.

```powershell
# Capturar o renderer real somente nas duas referências (a janela permanece aberta)
.\gradlew.bat -p labs/art-lab run "--args=--capture-suite --cycle=3"
# Após aparecer a indicação de conclusão na janela:
.\labs\art-lab\validation\Compare-Phase1B.ps1 -Cycle 3
```

Foram executados três ciclos. `build/captures-1b/cycle-N` contém capturas do GraphicsLayer real
antes da escala de apresentação, receitas, features e timings. O script preserva as evidências
em `docs/player-v8/validation/phase-1b/cycle-N`, cria comparações sem redimensionar mestre/captura
e verifica uma coordenada fixa da barra em 25/50/100%. Não existe um renderer de PNG paralelo.

## Pipeline implementado

- Decode limitado a 40 MP, PNG/JPEG, análise reduzida a até 256 px mantendo proporção.
- Luminância linear Rec.709; faixas baixa/média/alta configuráveis; saturação HSV;
  grade 8×8 de cor/luminância/saturação/bordas/contraste/área negativa/importância.
- Paleta de grupos de matiz/neutros com cobertura, centroide, dispersão e OKLab;
  área quase preta separada antes de acumular acentos para evitar que ruído JPEG os dilua.
- Scores heurísticos e mistura de até dois tratamentos, com cinco sinais explicativos.
  Não são probabilidades, reconhecimento de objetos ou inferência MobileCLIP.
- Diretor recebe apenas `ImageFeatures` e hash opaco de origem. Não recebe metadados do álbum.
  O hash identifica/cacheia conteúdo; não dirige cores ou geometria.
- Textura da imagem **inteira**, até 192 px, com blur gaussiano separável e bordas replicadas.
  O raio da receita é convertido pela largura lógica base 432; não há recorte central 48×48.
- Receita v2: canvas/scrims/textura, luzes elípticas com queda, material/reflexo/halo/rim do play,
  progresso de coordenadas fixas, favorito e letras. `motionAmplitude` foi removido; não há
  movimento decorativo sem implementação. Metadata e scores são consumidos pelo diagnóstico.
- Cache em memória e disco por SHA-256 dos bytes + versão do pipeline + política serializada.
  Cache inválido é recalculado. Não há inferência neural ou cache de embeddings.

`commonMain`: catálogo, features, contratos de inferência, direção e renderer. `desktopMain`:
arquivos, ImageIO, cache, precompute, picker, janela e exportação. O renderer não analisa pixels
nem lê arquivos. Android ainda exigirá adaptadores/lifecycle/insets e não foi compilado nesta fase.

## Controles e diagnóstico

Player e galeria preservam play/pause/favorito/shuffle/repeat simulados, seek por clique/arraste
e presets, anterior/próxima, recolher/reabrir, letras demo, capa local, fontes 100/150%, viewports
originais/1080×2340/720×1280 e preview em 100% com scroll. Letras usam apenas linhas de teste.
Transição suave pode ser ativada, mas redução de movimento vem ligada e a desativa.

Diagnóstico fora do player: origem oficial/local, cache MISS/MEMORY/DISK, timings por etapa
(elapsed wall e CPU de thread quando disponível), top-3 scores, cinco sinais, paleta e seus
contrastes contra canvas, mapa de importância 8×8, parâmetros das luzes e exportação JSON.
Modos de depuração: arte/base, luzes, scrims, botão e composição final.

## Testes e limites

12 testes direcionados: catálogo; round-trip/determinismo; fallback/NaN; thresholds;
preto/branco/monocromático; acentos periféricos; capas reais com receitas diferentes;
contraste ≥4.5:1 no material inclusive gradiente/reflexo; cache memória/disco/invalidação/reparo;
PNG/JPEG/decode corrompido; preservação de bordas e efeito do raio do blur; seek fixo.
Relatório HTML: `build/reports/tests/desktopTest/index.html`.
Métricas das duas entradas: `build/reports/classical/`.

O benchmark valida só estas duas capas. Não foi medido FPS/jank/RAM ou desempenho Android.
A luz branca continua difusa, a superfície espectral difere do mestre, a textura densa tem
regiões claras/quentes demais e há diferenças de fonte/geometria e das próprias artes.
Os três ciclos e medidas estão registrados na seção 18 da especificação e em
`docs/player-v8/reference-measurements.md`. Nenhuma aprovação estética foi declarada.

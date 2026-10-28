# NØNSTOP Art Lab v8 — Procedural Art Director

**Atualização 28/10/2026:** o pedido posterior de integração Android autorizou o
compartilhamento de `commonMain` com `core:art` e o host de reprodução principal.
Estado, testes e limites: [integração Android](../../docs/SONORA_ANDROID_INTEGRATION.md).
As descrições de isolamento abaixo registram o funcionamento próprio do laboratório;
a aprovação artística continua pendente.

Laboratório independente Kotlin/Compose Multiplatform. Norma única:
`../../docs/player-v8/PLAYER_V8_ART_LAB_SPEC.md` (1.4, decisões §20–21).
**Análise clássica/procedural, sem IA. Aprovação visual do usuário pendente.**

## Executar no Windows

Na raiz do repositório, com JDK 21 em `JAVA_HOME`:

```powershell
.\gradlew.bat -p labs/art-lab run
.\gradlew.bat -p labs/art-lab desktopTest
.\gradlew.bat -p labs/art-lab createDistributable
```

Distribuição com runtime Java incluído (copiar a pasta inteira, não apenas o exe):

```powershell
Push-Location labs/art-lab
& .\build\compose\binaries\main\app\NonstopArtLab\NonstopArtLab.exe
Pop-Location
```

Sem rede, conta ou áudio durante o uso. O build inicial pode resolver dependências.
Nenhum módulo de produção ou Android é dependência do laboratório.

## Validação procedural sem área de trabalho

### PLAYER_MICRO_INTERACTIONS_V1

```powershell
.\gradlew.bat -p labs/art-lab run "--args=--validate-micro"
```

Saída: `docs/player-v8/validation/micro-v1/`. Seis capas (Pink Floyd, Megadeth,
Nirvana, Michael Jackson, Abbey Road, Daft Punk), PNGs completos, filmstrips, GIFs de
recortes dos controles e comparação
de custo CPU com efeitos ligados/desligados. Eventos enviados somente à cena Compose,
sem janela, mouse/teclado do sistema ou captura da área de trabalho. Não recompile
durante a validação; comandos sequenciais ou `.exe` já empacotado.

Na interface, microinterações ficam ligadas e redução de movimento desligada por
padrão. O diagnóstico permite desligar efeitos/reduzir movimento, carregar WAV ou
iniciar fixture PCM de teste de oito segundos. O áudio é analisado em worker e a
prévia visual é **silenciosa**. Não há sincronismo com as faixas dos álbuns da galeria,
captura de áudio do sistema ou ligação com Media3. Sem fonte, o botão não pulsa.
Play/pausa controlam o relógio visual do envelope e seek o reposiciona; carregar
uma fonte reinicia em zero. O progresso simulado não avança sozinho com esse relógio.

Glow 6→10 dp, alpha .18→.34, tween 180 ms; trail de oito partículas/36 ms, vida
220–280 ms, pool 80. Resposta RMS suavizada: attack 180 ms/release 360 ms, aro até
+1.5 dp, halo até +6 dp/+ .12 alpha, corpo até 1.02x. Ganhos por família, cores
estáveis. dp seguem a base lógica de 432 dp do renderer. Sem shader customizado.

### Motor procedural estático

```powershell
.\gradlew.bat -p labs/art-lab run "--args=--validate-procedural --cycle=3"
.\gradlew.bat -p labs/art-lab run "--args=--cache-probe --cycle=3"
```

Execute os comandos sequencialmente; não recompile as classes enquanto a JVM de
validação ainda estiver executando. A segunda execução é uma nova JVM: exige 20 hits
em disco, nenhuma análise/geração e receitas/pixels iguais à primeira. O cache fica
em `labs/art-lab/build/validation-cache`; o cache da interface fica em `build/art-cache`
relativo ao diretório de execução. Não apague esses diretórios entre reinicializações
se quiser medir cache. Uma versão do motor/política diferente invalida as entradas.

Repetir a validação no mesmo ciclo reutiliza o cache: geração/features/direção
podem ficar em zero. Para repetir a medição **fria**, renomeie antes a pasta
`build/validation-cache/procedural-4-cycle-N` dentro do laboratório, preservando-a.
Depois execute a validação e o probe sequencialmente. A preparação total inclui
a conversão final para bitmaps Compose; a geração procedural tem cronômetro separado.

Evidência final: `docs/player-v8/validation/procedural/cycle-3`. Capturas reais das 12 capas
e oito fixtures, seek 0/25/50/100%, favorito contorno/preenchido, botão isolado, comparação
com os mestres, fonte 150%, letras demo, 1080×2340 e fallback. `metrics.json` separa
decode, features, direção, geração, preparação da textura e 30 rasterizações CPU
aquecidas por imagem; `cache-restart.json` prova reutilização entre processos.
Contato das capturas: `catalog-contact-sheet.png` e `synthetic-contact-sheet.png`.
O ciclo 1 é preliminar incompleto; o ciclo 2 identificou a falha sistêmica de centro
prismático claro em entradas com menos área negativa. O ciclo 3 impõe centro escuro
em toda a família, verificado por luminância nas 12 capas. Nenhuma regra por identidade.

## Validação histórica da Fase 1B.2

```powershell
.\gradlew.bat -p labs/art-lab run "--args=--validate-offscreen --cycle=3"
```

`--cycle=1`, `2` ou `3` identifica a pasta de evidência. Reexecutar o mesmo ciclo
sobrescreve suas capturas; não constitui um novo ajuste estético. `--capture-suite`
é um alias do caminho offscreen, sem abrir a janela antiga de automação.

A suíte cria **ImageComposeScene/Skia real**, sem Window/Robot/foco/entrada global.
Termina sozinha. Usa o mesmo `V8Renderer` da janela, os recursos/fontes empacotados
e a mesma receita. Nenhuma UI PNG paralela. As comparações apenas colocam o mestre
à esquerda e os pixels Compose à direita, sem redimensionamento.

Saída histórica: `docs/player-v8/validation/phase-1b2/cycle-N`. Inclui as duas resoluções
originais, ambos os modos, receita/features, tempos, seek 0/25/50/100%, pausa,
seek interno preservando pausa, fontes 150%, letras demo, iluminação e botão.
Este comando antigo visita somente Pink Floyd e Megadeth. Para a rodada atual,
use `--validate-procedural`, que visita as 12 capas adquiridas.

## Exibição e análise

- **PRODUÇÃO**: capa oficial completa, Apple Music/iTunes, manifesto `cover-sources.json`.
- **FIDELIDADE**: recorte da arte dos mestres aprovados, verificado no build.
- **Ambos analisam sempre a capa oficial**. O modo de exibição não entra no cache/diretor.
- PNG/JPEG local continua disponível, seguindo a mesma análise. Sem recorte aprovado,
  a entrada mantém sua imagem original. As 12 entradas agora têm capas verificadas.

## Compositor e compartilhamento

`commonMain`: features, direção, receita v4, pré-cálculo e renderer. `desktopMain`:
decode, cache persistente, janela, picker e validação offscreen.

Quatro componentes: ambiente profundo, iluminação difusa, acentos localizados e
detalhes direcionais. O pré-cálculo **não recebe o bitmap**, apenas a receita.
Não há cópia desfocada de textos/rostos/formas da capa. As quatro máscaras 192×342
são preservadas para diagnóstico e combinadas uma vez numa textura opaca persistente,
reduzindo quatro desenhos de tela inteira para um na composição final.

Features mantêm a imagem completa reduzida a 256 px: grade 8×8, Rec.709/HSV, área
negativa, contraste, paleta/OKLab e importância. Covariância de grupos cromáticos,
tensores de gradientes RGB por célula e histograma axial fornecem orientação e
coerência numérica. Não reconhecem semanticamente prismas, pessoas ou cenários.
Evidência fraca produz detalhes discretos ou ausentes. Sem shader customizado.

Seis famílias se misturam por evidências (máximo duas), com fallback neutro. Cores
ambiental/interação/acento têm seletores distintos por cobertura/dispersão/contraste/
saturação. Três materiais: prismático com centro escuro e aro cromático, preenchido
fosco sem aro, editorial escuro com aro fino. Sem reflexo especular/metálico.
Seed de hash + versão, ruído fractal multiescala, máscaras orgânicas, névoa, curvas,
gradientes espaciais e vinheta. Parâmetros e cinco motivos aparecem no diagnóstico.
Nenhuma identidade de álbum entra no diretor; hash só afeta seed/cache.

Cache por bytes + versão do pipeline + política; reparo de JSON/máscaras inválidos,
inclusive PNG válido com checksum divergente. Receitas antigas são invalidadas.
Limite de três assets completos em memória, thumbnails até 128 px sem análise;
seleções substituídas descartam trabalho pendente. Sem inferência ou embeddings.
Cinco texturas RGBA 192×342 correspondem a ~1,25 MiB brutos por capa, excluindo arte,
objetos/runtime e memória temporária. Isso é orçamento calculado, não pico de RAM.

Player preserva controles simulados, seek clicável/arrastável e presets, letras demo,
galeria, próxima/anterior, favorito, fonte 150%, viewports e preview em pixels reais.
Transições respeitam redução de movimento. Diagnóstico/exportação permanecem fora do player.

## Verificação e limites

Testes direcionados: determinismo/JSON/NaN/contraste; orientação com fixtures
rotacionadas; efeito das quatro camadas; continuidade/bordas transparentes de máscaras;
capas reais distintas; cache memória/disco/invalidação/reparo; PNG/JPEG corrompido;
nomes/hashes alterados; acentos periféricos; catálogo completo e seek de coordenadas
fixas; fallback sem imagem no raster real; seis famílias e três materiais em fixtures;
oito extremos sintéticos, efeito dos parâmetros, LRU e cancelamento de seleção.
Relatório: `build/reports/tests/desktopTest/index.html`.

A suíte offscreen acrescenta verificações dos pixels da barra, quatro cantos externos
do halo e entrada real interna à cena. Mede 12 frames CPU raster aquecidos por modo.
Não mede GPU, FPS sustentado, pico de RAM ou Android e não prova 60 FPS.

Na Fase 1B.2 foram feitos três ciclos. Estrutura triangular de luz de Pink Floyd, textura/profundidade
de Megadeth, distribuição do halo e tipografia ainda diferem dos mestres. A fonte é
Inter fallback; as artes oficiais também diferem das estilizadas. Medições, evidências
e limites concretos: `docs/player-v8/reference-measurements.md`, seção Fase 1B.2.
Nenhuma aprovação estética, integração de produção, commit, push ou merge.

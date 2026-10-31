# Medições das referências e execução das Fases 0 / 1A

Registro de implementação de 2026-10-17. A especificação normativa permanece
`PLAYER_V8_ART_LAB_SPEC.md`; este arquivo documenta evidência e valores provisórios.

## Fase 0 — auditoria

- Branch verificada: `feat/nonstop-v8-art-lab`. Árvore inicialmente limpa, sem diferenças.
- `AGENTS.md`, `README.md` e especificação lidos integralmente antes de construir a UI.
- Ambos os PNGs originais foram abertos e examinados; SHA-256 conferidos com `references/manifest.json`:
  - Megadeth, **864×1536**: `3547184210edca28399a0945dc28192e65341ebd1545be070873593004e3d68d`.
  - Pink Floyd, **941×1672**: `6b7cbd4da0fa601379b2d8d790d70aac2b532d22bb35739370fc3958fec430f0`.
- Windows 11 amd64, Temurin JDK **21.0.12.1**, Gradle **8.12** verificados por execução.
  Kotlin **2.3.0**, Compose Multiplatform **1.10.0** e Skiko Windows resolvidos e executados.
  A primeira tentativa no sandbox não pôde escrever o lock do cache Gradle; a execução
  autorizada fora desse limite funcionou. Não foi necessário alterar o app Android.
- Build independente em `labs/art-lab`. Nenhum arquivo de produção modificado.
- Compartilhamento: componentes/estado/receita/fontes em `commonMain`; ImageIO/AWT, janela,
  picker e exportação confinados a `desktopMain`. Android exigirá adaptadores de assets,
  decode/precompute e lifecycle/insets. Ainda não há compilação Android ou medição no Redmi.
- O custo de blur é limitado a uma textura reduzida por hash, fora da UI thread. Cache em memória;
  persistência, features, análise espacial, classificação e orçamento de frames são Fase 1B+.

### Auditoria preliminar do modelo — sem instalação

Consultados em 2026-10-17: [repositório oficial Apple](https://github.com/apple/ml-mobileclip),
[LICENSE_MODELS](https://github.com/apple/ml-mobileclip/blob/main/LICENSE_MODELS) e
[lista de arquivos oficial S0](https://huggingface.co/api/models/apple/MobileCLIP2-S0?blobs=true).
O código é MIT; os pesos usam **Apple ML Research Model License Agreement**, cujo texto limita
a Research Purposes e exclui explicitamente “product development”. Uso pessoal não resolve
automaticamente essa restrição. Reavaliar adequação/termos do artefato exato antes da Fase 1C.

`mobileclip2_s0.pt`: **299.864.852 bytes** segundo metadados do repositório oficial, sem baixar
o arquivo. O README oficial oferece PyTorch/OpenCLIP, não um backend Kotlin pronto.
Exportação compatível e runtime local JVM/Android (por exemplo ONNX Runtime) precisam de spike
e validação de operadores, tokenizer, normalização e paridade numérica. Não há benchmark local,
memória/pesos instalados, licença de integração aprovada ou prova de inferência nesta entrega.
`VisualEncoder` e `NoModelEncoder` são apenas pontos de extensão; o renderer nunca chama IA.

## Método e incerteza

Retângulos identificados por inspeção dos PNGs originais, usando as bordas de alto contraste.
Formato: **x, y, largura, altura**. Normalizados: **x/W, y/H, largura/W, altura/H**.
As casas decimais resultam da divisão, não de medição subpixel. Tolerância inicial:
arte/barra/botão **±2–4 px**, texto **±4–8 px**, limites de blur/halo **±8–24 px**.
Texto refere-se ao envelope de tinta percebido, não à caixa tipográfica/baseline original.
Não foi possível identificar com certeza a fonte nem a densidade lógica usada no mockup.

| Referência | Elemento | Retângulo px | Retângulo normalizado |
|---|---|---|---|
| Megadeth | Capa | 70,154,724,718 | .0810,.1003,.8380,.4674 |
| Megadeth | Fechar | 35,42,76,76 | .0405,.0273,.0880,.0495 |
| Megadeth | Menu | 753,42,76,76 | .8715,.0273,.0880,.0495 |
| Megadeth | Progresso | 70,919,724,8 | .0810,.5983,.8380,.0052 |
| Megadeth | Título | 70,1014,636,45 | .0810,.6602,.7361,.0293 |
| Megadeth | Artista/álbum | 70,1075,600,38 | .0810,.6999,.6944,.0247 |
| Megadeth | Coração | 756,1034,38,35 | .8750,.6732,.0440,.0228 |
| Megadeth | Play (superfície) | 364,1187,136,136 | .4213,.7728,.1574,.0885 |
| Megadeth | LETRAS (superfície) | 21,1398,822,138 | .0243,.9102,.9514,.0898 |
| Pink Floyd | Capa | 81,175,779,757 | .0861,.1047,.8278,.4528 |
| Pink Floyd | Fechar | 38,47,84,84 | .0404,.0281,.0893,.0502 |
| Pink Floyd | Menu | 819,47,84,84 | .8704,.0281,.0893,.0502 |
| Pink Floyd | Progresso | 78,984,785,8 | .0829,.5885,.8342,.0048 |
| Pink Floyd | Título | 78,1085,250,60 | .0829,.6489,.2657,.0359 |
| Pink Floyd | Artista/álbum | 78,1155,650,38 | .0829,.6908,.6908,.0227 |
| Pink Floyd | Coração | 819,1112,44,40 | .8704,.6651,.0468,.0239 |
| Pink Floyd | Play (superfície) | 391,1250,160,160 | .4155,.7476,.1700,.0957 |
| Pink Floyd | LETRAS (superfície) | 17,1493,908,179 | .0181,.8929,.9649,.1071 |

Outras medidas visuais (estimadas, sujeitas às tolerâncias acima):

| Elemento | Megadeth | Pink Floyd |
|---|---|---|
| Raio do canto da arte | ~28 px | ~24 px |
| Centro/raio do thumb | (392,922),11 px | (411,988),12 px |
| Centro do play | (432,1255) | (471,1330) |
| Previous, envelope do ícone | (246,1233,44,44) | (270,1310,44,47) |
| Next, envelope do ícone | (574,1233,44,44) | (625,1310,44,47) |
| Shuffle, envelope do ícone | (93,1238,45,38) | (99,1313,49,40) |
| Repeat, envelope do ícone | (723,1238,46,38) | (792,1312,49,41) |
| Alça LETRAS (seta) | (417,1424,30,13) | (458,1523,25,15) |
| LETRAS, envelope de texto | (385,1459,93,20) | (419,1561,104,23) |
| Distância arte → centro da barra | 50 px | 56 px |
| Distância barra → título | ~92 px | ~97 px |
| Tamanho tipográfico inicial estimado (título) | ~42 px | ~58 px |
| Tamanho tipográfico inicial estimado (secundário) | ~32 px | ~36 px |

Os envelopes de arte dos mestres **não são exatamente quadrados**: razão 1.008 e 1.029.
O renderer preserva imagem sem distorção e usa caixa quadrada com `ContentScale.Crop`, conforme
especificação. Não é possível igualar simultaneamente esses dois retângulos a um quadrado.
As referências têm razão ~9:16, enquanto 1080×2340 tem ~9:19.5. O viewport alto é teste
de responsividade, não uma alegação de reprodução do mestre na mesma escala.

## Baseline de geometria compartilhado

Um conjunto inicial comum, sem condição por álbum: margem lateral .083W; arte .834W;
topo da arte .102H; centro da barra .057W abaixo da arte; título 43 unidades depois da barra;
centro do transporte .805H (com proteção baseada nas alturas medidas do texto); LETRAS .901H.
Unidade tipográfica base: W/432. Inter regular/semibold dos assets existentes é o fallback
explícito; não foi alegado que seja a fonte dos originais. Títulos longos usam tamanho menor
e até duas linhas, por comprimento do texto, nunca por identidade do álbum.

Em fonte ampliada, a arte é reduzida proporcionalmente (até 18% a 150%) e centralizada,
liberando espaço antes da barra/títulos. Metadados e transporte respeitam as alturas
calculadas pelo text measurer; isso evita sobreposição ou recorte da alça LETRAS no painel curto.
Essa adaptação de acessibilidade não pretende reproduzir o mockup de fonte normal.

Caixas clicáveis de transporte/topo/favorito têm no mínimo 48 unidades da largura lógica
base 432. O preview de apresentação pode reduzi-las; isso não constitui um teste de toque
no Android. O modo 100% desliga a redução e permite scroll, preservando os pixels do viewport.

## Proveniência das capas

Somente o retângulo da arte foi recortado pelo task `prepareReferenceAssets`, que confere
SHA-256 dos mestres antes de ler. **Nenhum controle, título ou fundo do mockup é usado como UI.**

- Pink Floyd: recorte (81,175,779,757), SHA-256
  `d851be1a9f4a67fa2dcb5644bf7884c45230d08e53144725b435c7812a698a24`.
- Megadeth: recorte (70,154,724,718), SHA-256
  `c91e93e707f52f6b698b64f7f3641071cbd1b0b290b5ba406209fe5153e6adec`.

Esses derivados retêm material/textura/cantos da arte no mockup; **não são capas originais
de produção**. As dez demais imagens não estão disponíveis em fonte local verificável.
Galeria e manifesto as registram como `missing`, sem faixas/durações inventadas.
Arquivos locais adicionais são aceitos como entradas separadas, sem alterar o benchmark fixo.

## Verificação executada

Comandos na raiz: `gradlew.bat -p labs/art-lab desktopTest`, `run --args=--capture-suite`
e `createDistributable`. Build, cinco testes e distribuição Windows concluídos.

Os testes verificam: catálogo fixo/disponibilidade honesta; determinismo e round-trip JSON;
fallback de receitas inválidas/NaN e JSON malformado; coordenadas fixas do progresso em
0/25/50/100%; PNG/JPEG, decode corrompido e reaproveitamento de textura por hash.
A verificação adicional de pixels confirma a mesma cor em (200,1010) nas capturas 25/50/100%,
junto à inspeção visual do progresso.
Não são testes de aprovação estética nem prova de desempenho Android.

A janela Windows foi inspecionada com Computer Use; cliques reais confirmaram alternância
play/pause, próxima capa, seek 50% e abertura de LETRAS. A suite percorre as 12 entradas
na aplicação aberta e captura o `GraphicsLayer` usado pelo renderer, antes de reduzir
o preview. Não há implementação paralela que desenhe screenshots de apresentação.

Capturas examinadas: duas referências na resolução original; dez placeholders do catálogo;
progresso 0/25/50/100%; LETRAS demo; viewport 1080×2340; fonte 150%, incluindo título longo.
Nos primeiros ciclos foram corrigidos contraste do painel, escala de ícones e entrelinha.
Os PNGs de comparação colocam o mestre e a captura real lado a lado sem redimensionamento.

Timings de uma execução observada: **165,12 ms** Pink Floyd e **59,23 ms** Megadeth para
decode + textura (elapsed wall time, inclui alocação/overhead; não é CPU time ou inferência).
Ao voltar às capas o painel mostrou cache hit. Não foram medidos FPS/jank/RAM, cold/warm
de modelo ou desempenho em Android. Não há IA, escores ou confiança calculados.

## Diferenças observadas / aceite pendente

- Megadeth: caixa da arte do app ~ (72,157,721,721), versus (70,154,724,718).
  Barra em y≈926 versus centro y≈922. Transporte ajustado conforme altura real do texto,
  próximo de y≈1255; tolerância depende das métricas de fonte.
- Pink Floyd: caixa do app ~ (78,171,785,785), versus (81,175,779,757).
  O quadrado estende a base ~23 px e desloca barra/textos para baixo (~21–30 px).
  Transporte em y≈1346 versus y≈1330. LETRAS começa ~1506 versus 1493.
- O título de Megadeth quebra em duas linhas; o mestre o mantém numa. Inter e métricas
  de kerning, envelopes tipográficos, símbolos de transporte e raios ainda diferem.
- O play usa material e halo neutros compartilhados. Faltam preenchimento ciano e atmosfera
  azul profunda da referência densa, assim como borda/halo espectral e reflexos localizados
  da referência prismática. Não se aplicaram exceções por artista para simular esse resultado.
- O fundo é uma textura genérica desfocada com luzes/scrims estáticos. Não reproduz as regiões
  luminosas ou a estrutura cinematográfica dos mestres; essas decisões são Fase 1B/1D.
- A barra é neutra nesta fase; não há seleção espacial de clusters cromáticos. A seção
  preenchida revela coordenadas fixas e não reescala o gradiente.
- Tempo mostrado deriva da porcentagem demonstrativa (43% inicial), não do “1:42” dos mockups,
  que não corresponde à posição do thumb. Não foi inventada reprodução real para conciliá-los.
- Troca de capa não reinicia o app e decodifica fora da UI; ainda não faz crossfade.
  Animação permanece desativada e redução de movimento ativa, explicitamente informadas.
- Dez placeholders não validam generalização visual. São necessárias as capas originais
  ou uma fonte verificável para o benchmark artístico futuro.

**Estado: Fases 0 e 1A funcionais; visual NOT PASSED / aguardando validação do usuário.**
Não foi integrado Android, nem alterados PlayerV7, Media3, navegação, autenticação, downloads
ou serviços. Sem commit, push ou merge. Execução e estrutura de arquivos estão no README do lab.

## Atualização Fase 1B — benchmark de duas capas

Registro operacional e decisões na seção 18 da especificação normativa, versão 1.1.
Partida: `4cf612ce1`. As medições originais acima permanecem; o registro 1A é histórico.
As outras dez entradas não foram capturadas, analisadas ou refinadas nesta rodada.

Capas completas de 1200×1200 do catálogo oficial Apple Music/iTunes substituem os recortes
como entrada de análise e de exibição. Fontes/copyright/edições estão em
`../../labs/art-lab/cover-sources.json`:

| Entrada | Coleção Apple | SHA-256 |
|---|---|---|
| Pink Floyd — The Dark Side of the Moon | 1065973699 | `7b5ad4cdeed66d53928f485667e8d5bf764529d4d7c45fb620f1ea31913445cb` |
| Megadeth — Rust In Peace | 724648893 | `3a1ef3872b81e1dbccbc60673f1e5f20db80e222b7d3414b2c85be3b6a227c6e` |

Não são imagens sintetizadas nem recortes do player. Tampouco são a mesma arte estilizada
dos mestres: o prisma oficial é menor e fosco, sem a textura riscada; em Megadeth a composição,
tipografia da capa, iluminação e cores também diferem. Isso impede atribuir toda a diferença
pixel a pixel ao compositor. Nenhuma das imagens mestras foi alterada.

O renderer agora recebe receitas distintas derivadas da imagem inteira. No motor final,
Pink Floyd tem canvas `#040607`, tratamento espectral e 8 luzes; Megadeth tem canvas `#080C15`,
tratamento frio/denso e 3 regiões luminosas. O play denso começa em `#6ACDF5` com tinta escura;
o espectral usa superfície escura, tinta clara e stops cromáticos medidos na capa.
São resultados desta amostra, não valores escolhidos por identidade do álbum.

Capturas reais e dados por ciclo: `validation/phase-1b/cycle-1`, `cycle-2` e `cycle-3`.
Em cada comparação, **mestre à esquerda / aplicativo à direita**, com dimensões originais
iguais (o visualizador pode reduzir o conjunto para caber na tela). Os arquivos `*-recipe.json`
e `*-features.json` correspondem às entradas renderizadas. `reference-*-timings.txt` registra
uma amostra medida sem cache em processo JVM já aquecido; `*-timings.txt` das capturas registra
o estado de cache do carregamento exibido. Não confundir zero análise em cache hit com análise
instantânea. Relatório detalhado e métricas: `labs/art-lab/build/reports/classical/`.

Uma amostra do teste final com cache vazio para cada capa, em JVM já aquecida:

| Medida | Pink Floyd | Megadeth |
|---|---:|---:|
| Área escura (luminância <.035) | 95,05% | 40,93% |
| Pixels altamente saturados com luz suficiente | 2,87% | 87,26% |
| Cobertura fria | 1,63% | 88,10% |
| Decode (wall) | 21,52 ms | 45,47 ms |
| Features (wall) | 24,15 ms | 24,61 ms |
| Direção (wall) | 0,72 ms | 1,52 ms |
| Textura/cache/exportação (wall) | 44,53 ms | 36,47 ms |
| Total (wall) | 94,88 ms | 113,22 ms |
| CPU da thread (total) | 62,50 ms | 62,50 ms |

São observações de uma execução, sem promessa de cold-start, FPS ou desempenho Android.
Os resultados de determinismo excluem timings; nomes alterados não mudaram a receita.

Foram examinados os pares, luzes isoladas, play isolado, LETRAS demo, fontes ampliadas e
estados de seek. A checagem adicional de pixels, em (200,1010) para Pink Floyd e (200,926)
para Megadeth, confirmou cor idêntica em 25/50/100%, sem reescalar o gradiente preenchido.
Os 12 testes direcionados passaram; não houve benchmark FPS/jank/RAM ou Android.
No executável final, cliques reais verificaram pausa seguida de seek sem perder o estado
pausado e troca para a receita de Megadeth. O diagnóstico real foi inspecionado com seu mapa,
paleta e contrastes. Evidências adicionais: `cycle-3/desktop-paused-seek.png`,
`desktop-window.png` e `desktop-diagnostics.png`.

Diferenças restantes após três ciclos:

- Pink Floyd: arte oficial/prisma não reproduz o acabamento do mockup. Luz branca superior
  é uma região difusa, não o feixe/estrutura triangular visível no mestre. A distribuição
  espectral ainda é mais concentrada e suave. O favorito sai alaranjado pela paleta, enquanto
  o mestre usa rosa. A barra usa os acentos da imagem original, com outra distribuição.
- Pink Floyd: caixa quadrada estende a base da arte em ~23 px; barra/textos continuam
  ~21–30 px abaixo do mestre; centro do play ~1346 contra ~1330. Diâmetro espectral
  derivado da receita fica próximo de 159 px, contra ~160 px do mestre.
- Megadeth: texto cabe numa linha em fonte normal, mas fonte/kerning/altura ainda diferem.
  Capa do app ~ (72,157,721,721), contra (70,154,724,718); barra ~926 contra centro ~922;
  play em ~1236 contra ~1255. Fonte ampliada mantém até duas linhas e controles sem sobreposição.
- Megadeth: ambiente é frio e espacial, mas o blur ainda preserva ecos claros/quentes da
  tipografia/rostos; falta a mesma profundidade azul e a variação de luz dos mestres. O halo
  ciano é mais pálido/difuso. A superfície LETRAS difere em transparência/curvatura.
- Ambos: glyphs de shuffle/repeat, tempos demonstrativos e espaçamentos não são equivalentes
  ao mockup. Redução de movimento vem ativa; transição só anima quando ela é desativada.

**Fase 1B funcional; visual NOT PASSED / pendente do usuário.** Nenhum peso/modelo neural
foi baixado ou executado. A arquitetura permanece compartilhável; produção ficou intocada.


## Fase 1B.2 — compositor abstrato, três ciclos encerrados

Partida verificada: `408f5f668fcbb02589e2d378c81eb8ae48f43f25`, branch
`feat/nonstop-v8-art-lab`. Os arquivos de validação anteriores já estavam não rastreados
na chegada e foram preservados. Alterações restritas ao laboratório e à documentação v8.
Mestres novamente verificados pelos SHA-256 do manifesto. Sem modelo neural.

### Arquitetura efetivamente executada

`ImageFeatures` v3 mede covariância espacial/anisotropia dos grupos cromáticos,
histograma de orientação axial de gradientes RGB e tensor de estrutura por célula 8×8.
A orientação é numérica; não localiza semanticamente prisma, rosto, arquitetura ou cenário.

`ArtRecipe` v3 separa ambiente profundo (`environment`), iluminação difusa (`lights`),
acentos (`accents`) e detalhes direcionais (`directionalDetails`). O pré-cálculo comum
recebe somente essa receita. A antiga função de blur da imagem inteira foi removida.
Sem acesso ao bitmap, o compositor não pode copiar textos/rostos/formas para o fundo.
Regiões profundas deformadas e granulação analítica substituem a textura literal;
segmentos alongados usam orientação e coerência medidas. Nenhum shader personalizado.

As quatro máscaras RGBA 192×342 são guardadas separadamente em disco. A composição
opaca é combinada uma vez em memória: a UI final faz um desenho de fundo, enquanto o
modo LIGHTS continua permitindo examinar os componentes luminosos. A receita consome
centros, raios, cores, intensidades, falloff, grain/warp, orientação/extensão/largura/
coerência. As propriedades de blur/opacidade antigas não existem no schema novo.
Cinco texturas equivalem a 1.313.280 bytes (~1,25 MiB) brutos por capa, sem incluir
arte, runtime e temporários; não é medição de pico de RAM.

A amostra final contém:

| Componentes | Pink Floyd | Megadeth |
|---|---:|---:|
| Regiões profundas | 5 | 5 |
| Luzes difusas | 1 | 5 |
| Acentos localizados | 7 | 2 |
| Detalhes direcionais | 8 | 3 |

Contagens resultam dos pixels/limiares. O diretor não recebe artista, álbum, índice,
ID de referência nem modo de exibição; hash é identidade opaca de origem/cache.

### Modos e evidências reais

- **FIDELIDADE:** recorte da arte dos mestres, extraído no build após verificar hashes.
- **PRODUÇÃO:** JPEG oficial 1200×1200, com checksum conferido pelo carregador.
- Em ambos, receita/features vêm da mesma capa oficial. Alternar exibição não executa
  análise do mockup nem muda a identidade do cache. A associação de arquivos é apenas
  aquisição de arte de comparação, não direção visual.

Três ciclos retidos em `validation/phase-1b2/`. Cada pasta contém os dois modos nas
resoluções originais. No ciclo 3, foram inspecionados os quatro pares `compare-*.png`,
as fontes ampliadas e, adicionalmente, luzes, botão, progresso e letras demo.
Comparações mantêm os PNGs em escala 1:1: mestre à esquerda, renderer à direita.
O visualizador do chat pode reduzir o par inteiro para caber.

Capturas geradas por **ImageComposeScene/Skia raster real**, com o mesmo `V8Renderer`
e fontes empacotadas da janela. Nenhuma Window, captura de monitor, mouse/teclado global,
troca de foco ou interação com outro programa. O processo termina automaticamente.
A distribuição Windows foi construída e seu próprio `NonstopArtLab.exe` executou a
mesma suíte offscreen, com exit code 0; logs em `cycle-3/native-execution.txt` e `native-final-execution.txt`.
A janela interativa não foi aberta nesta etapa. A reexecução da distribuição final
produziu os mesmos 28 PNGs byte a byte (SHA-256), inclusive após a correção
funcional do fallback; registro em `cycle-3/rerun-determinism.json`.

Resumo dos ajustes por ciclo:

1. Separação em quatro camadas; remoção de ecos literais; reflexos orientados por pixels.
   A inspeção mostrou iluminação densa tímida e bandas espectrais excessivamente finas.
2. Profundidade/saturação regional, reflexos neutros por covariância, grupos de matiz de
   30° preservando vermelho, halo menor e geometria/tipografia compartilhadas corrigidas.
   Pré-composição reduziu overdraw. Persistiam pouca textura densa e luz inferior fraca.
3. Tensor local por célula para reflexos frios conservadores e distribuição inferior dos
   acentos esparsos. Encerrou-se a calibração; diferenças abaixo não foram escondidas
   por ajustes adicionais ou regras por identidade.

### Geometria e tipografia finais

Envelope comum medido/compromissado: largura .834W, topo .1035H e altura limitada a
.4597H (quadrado em telas mais altas). `ContentScale.Crop` não distorce as imagens.
Progresso tem âncora própria .5957H. Transporte usa .795H mais espaçamento óptico
proporcional à redução tipográfica medida do título. Removida escolha de fonte por
comprimento do texto; qualquer título segue a mesma medição de largura.
Inter semibold/regular continua fallback; não foi encontrada/provada a fonte do mockup.

| Elemento, pixels aproximados | Mestre Megadeth | Renderer Megadeth | Mestre Pink Floyd | Renderer Pink Floyd |
|---|---|---|---|---|
| Capa x,y,w,h | 70,154,724,718 | 72,159,720,706 | 81,175,779,757 | 78,173,785,769 |
| Centro da barra y | 922 | 915 | 988 | 996 |
| Centro do play y | 1255 | ~1255 | 1330 | ~1330 |
| Diâmetro do play | 136 | ~136 | 160 | ~159 |

Envelope da capa deriva dos tokens e rasterização arredondada; tolerância ±1–2 px.
Barra verificada nos pixels: faixas centrais em y=912..917 (Megadeth) e 993..999
(Pink Floyd). Material/aro observado em y≈1191..1319 e 1254..1407, respectivamente;
essas faixas cromáticas não incluem toda a borda antialias. Centros com tolerância ±3 px.
A redução de erros não elimina a diferença entre as proporções dos dois mestres.

Fontes 150% examinadas: título longo em duas linhas e metadata de Pink Floyd em duas
linhas, com controles acessíveis sem sobreposição nas capturas nativas. Os glyphs
shuffle/repeat continuam distintos. LETRAS ainda tem superfície e espaçamento diferentes.

### Testes, pixels e desempenho

`desktopTest`: **14 testes finais, zero falhas/erros**, após corrigir a asserção antiga de
acento periférico para verificar a nova camada `accents`. O primeiro ciclo revelou
essa asserção obsoleta; os ciclos 2 e 3 passaram. Teste não foi retirado.
Após os três ciclos, uma correção funcional restaurou as duas luzes do fallback sem
textura; um teste ImageComposeScene real confirma que o decode inválido não achata
o fundo. Isso não muda o visual das duas referências nem constitui nova calibração.

Cobertura: determinismo por recomputação/JSON/máscaras; alteração de metadados sem
alterar receita; receitas reais diferentes; cache memória/disco/invalidação/reparo;
PNG/JPEG/decodificação inválida; thresholds, preto/branco/monocromático, NaN e contraste;
fixtures horizontal/vertical que rotacionam orientação/coerência; efeito de cada camada;
continuidade de alpha e bordas externas transparentes em máscaras isoladas.

A suíte real verifica cor fixa da barra em 25/50/100%, pausa via ação semântica Compose,
e seek via evento de ponteiro **apenas na cena offscreen**, preservando o estado pausado.
Clique em 60% da largura da viewport resultou em ~61,99%/61,91% do track (inset descontado).
Também verifica os quatro cantos externos do halo isolado contra a cor do canvas.
Esses checks e a inspeção não detectaram retângulos de clipping nos estados testados;
não constituem prova universal para toda receita futura.

Contraste nos pixels finais, em 36 amostras do material a 70% do raio, afastadas de
ícone/aro: **mínimo ~14,98:1 Pink Floyd e ~8,61:1 Megadeth** contra a tinta da receita.
Os testes de contrato também verificam gradiente/reflexo ≥4,5:1. Registro em
`cycle-3/pixel-checks.json`; aprovação estética não decorre desse número.

Uma execução Gradle do ciclo 3, cache vazio (JVM já em uso):

| Wall ms | Pink Floyd | Megadeth |
|---|---:|---:|
| Decode | 112,81 | 27,05 |
| Features | 204,27 | 326,40 |
| Direção | 36,38 | 0,63 |
| Máscaras/composição/exportação | 425,69 | 135,39 |
| Total | 895,66 | 493,99 |

Na verificação final pelo **exe com runtime incluído**, cache de disco:

| Medida | Pink Floyd | Megadeth |
|---|---:|---:|
| Total disk hit, wall ms | 596,77 | 86,57 |
| Features / direção no hit | 0 / 0 | 0 / 0 |
| Memory hit, wall ms | 3,73 | 3,24 |
| Mediana raster FIDELIDADE, ms/frame | 33,67 | 25,08 |
| Máximo raster FIDELIDADE, ms/frame | 44,08 | 27,24 |
| Mediana raster PRODUÇÃO, ms/frame | 37,01 | 27,41 |
| Máximo raster PRODUÇÃO, ms/frame | 45,42 | 29,55 |

12 frames aquecidos por modo/resolução, sem encoding PNG dentro do intervalo medido.
Raster usa CPU e inclui custo de snapshot da cena; não mede apresentação de janela/GPU.
No ciclo 1, medianas ficaram ~88–104 ms, com quatro sobreposições integrais; a composição
pré-calculada reduziu esse custo. Comparação entre execuções também sofre JIT/cache/ruído.
**60 FPS não foi demonstrado**; não há benchmark Android, pico de RAM ou FPS sustentado.
Disk hit ainda decodifica a arte/carrega máscaras; custo inicial de classes/recursos existe.

### Diferenças e limites concretos após o terceiro ciclo

- **Pink Floyd:** feixe neutro e faixas cromáticas são orientados e organizados, mas não
  recuperam a estrutura triangular de luz do mestre. Covariância não fornece vértices
  nem conectividade semântica. Bandas superiores são mais horizontais e separadas;
  reflexão inferior é mais discreta e posicionada de outro modo. Não inventar um detector
  de prisma para justificar a diferença. Material do play está mais claro/esverdeado,
  halo/rim têm outra distribuição angular; favorito é vermelho, enquanto o mestre usa rosa.
- **Megadeth:** cinco regiões frias independentes mantêm variação azul e sombras, mas o
  ambiente ainda é mais liso, menos contrastado e menos rico em detalhes do que o mestre.
  Três reflexos locais são conservadores; não recompõem a arquitetura do cenário.
  Pequenos reflexos verdes do mestre não estão equivalentes. Play está mais ciano e com
  aro/reflexo mais evidente; falta o mesmo caráter azul e a mesma distribuição do halo.
- **Ambos:** diferenças de capa/enquadramento, fontes/kerning, brilho do progresso,
  glyphs e LETRAS persistem. O compromisso de envelope deixa altura ±12 px nas capas
  e barra ~7–8 px afastada. Não há regras de coordenadas por álbum para eliminá-las.
- **PRODUÇÃO:** artes oficiais diferem das artes estilizadas do mockup (prisma menor/matte,
  títulos/crop/cores diferentes em Megadeth). FIDELIDADE isola essa diferença de exibição;
  o fundo permanece calculado pela arte oficial em ambos.
- O estado inicial usa progresso real .43; os tempos derivados são 2:45/2:49, enquanto
  o mockup marca 1:42 com thumb perto de 43%. A simulação mantém coerência entre tempo
  e seek, sem falsificar duração/tempo para imitar a inconsistência gráfica.
- O atlas usa proporção retrato 192×342; outras proporções esticam a atmosfera abstrata.
  Capa e controles continuam responsivos; eventual projeção por aspecto para Android
  precisa de adaptação e medição futuras, sem afirmar equivalência ainda.

**Três ciclos concluídos. Visual NOT PASSED / aprovação do usuário pendente.**
Nenhuma alteração de produção, IA neural, automação da área de trabalho, commit, push ou merge.

## 2026-10-18 — Procedural Art Director / receita v4

### Implementação e escopo

Evolução sobre a Fase 1B.2, sem reiniciar o laboratório. `FamilyDirector` adiciona
seis famílias combináveis e fallback neutro, selecionados por área negativa,
diversidade OKLab, cobertura saturada, distribuição espacial, bordas e faixa tonal.
Os escores são heurísticos e os limiares são hipóteses versionadas, não classificação
semântica validada. Até dois tratamentos combinam parâmetros procedurais.

`ColorRoles` separa ambiente (cobertura × dispersão), interação (cobertura × saturação
× contraste) e acento local (contraste/saturação, penalizando cobertura ampla).
`ProceduralPrimitives` implementa ruído interpolado, fBm até cinco oitavas, máscara
orgânica, névoa, curva abstrata, vinheta e zonas tranquilas. `AtmospherePrecomputer`
mantém campos suaves e feixes orientados da Fase 1B.2, sem receber pixels da capa.
Seed deriva de hash + versão; não altera família, paleta ou material. Sem IA.

Materiais realmente distintos: prismático com centro escuro/aro cromático/halo
espectral sustentado pela paleta; fosco preenchido sem aro; editorial escuro/aro
fino/halo contido. Campos especulares removidos. Favorito com preenchimento e
contorno vetoriais; halo pequeno na agulha; gradiente de progresso fixo.
Geometria, modos PRODUÇÃO/FIDELIDADE, controles simulados, letras demo, carregamento
local, fonte ampliada e separação commonMain/Desktop preservados.

Cache persistente contém receita/features e **cinco PNGs 192×342**, incluindo a
composição final. Hashes dos PNGs detectam inclusive substituição por PNG válido.
Versão/política invalidam entradas antigas; cache em memória limita assets completos
a três. Thumbnails até 128 px não geram fundos. A seleção mostra fallback imediato
e a preparação usa `Dispatchers.IO`, com descarte de trabalho substituído.

### Validação e evidências

- **20 testes automatizados passaram** (`desktopTest`), cobrindo extremos, determinismo,
  JSON, finitude/contraste, separação hash/estilo, famílias combinadas, efeito real dos
  parâmetros, orientação, fontes verificadas, corrupção/invalidação, LRU, cancelamento
  e fallback raster. Famílias/material não são selecionados por identidade.
- `createDistributable` compilou a distribuição Windows com runtime incluído.
  O próprio `NonstopArtLab.exe` executa `--validate-procedural --cycle=3` em headless,
  com `ImageComposeScene`/Skia real, sem janela, foco ou entrada global.
- As 12 capas originais foram adquiridas/verificadas. Manifesto `cover-sources.json`
  contém URLs, edições, checksums, disponibilidade e copyright; respostas oficiais das
  dez novas consultas e script de aquisição estão em `labs/art-lab/validation/`.
- Oito fixtures: preta, branca, vermelho intenso, monocromática, multicolorida,
  gradiente, proxy fotográfico claro e baixo contraste. O proxy é sintético; fotografias
  reais entram também pelas capas oficiais. Não se testa reconhecimento de objetos.
- Evidência final: `validation/procedural/cycle-3/`: capturas das 20 entradas, quatro
  estados de seek, coração em contorno, materiais isolados, 150%, letras, comparações
  com os mestres e 1080×2340. Mesma composição visita as 12 capas sem reiniciar; fallback
  sem textura também é capturado. `metrics.json` separa fases e 30 frames raster por
  entrada; `cache-restart.json` compara receita e pixels em uma segunda JVM.
- Ciclo 1: coleta preliminar incompleta, invalidada por recompilação concorrente das
  classes antes de salvar o relatório. Ciclo 2: coleta completa mostrou preenchimento
  claro em uma saída prismática. Ciclo 3: regra **geral da família** corrigida para centro
  escuro; teste de luminância <.025 para todo material prismático do catálogo.

### Limites e estado de aceite

Não há inferência de IA, reprodução real ou integração Android. Não foram medidos
GPU, jank, 60 FPS, Redmi Note 14 ou pico total de RAM. A estimativa RGBA exclui runtime,
objetos e temporários de decode/geração; não é uma medição de heap/RSS.

As artes oficiais diferem dos mockups. Continuam diferenças na estrutura de luz,
textura/profundidade, tipografia Inter e desenho de ícones; os tempos da simulação
permanecem coerentes com seek/duração, em vez de copiar o tempo inconsistente dos
mestres. Dez entradas não têm faixa/duração fornecidas, identificadas como tal no lab.
Proporções muito altas esticam a textura abstrata retrato; o motor não reconstrói
objetos da arte e não garante tratamento ideal para toda imagem desconhecida.

**Aceite estético: NOT PASSED / decisão do usuário pendente.** Não equivale a um
bloqueio de implementação funcional. Produção/v7/Media3 preservados; sem commit/push/merge.

### Medicoes reais do ciclo 3 / motor 4.2

Windows, JDK 21.0.12, Kotlin 2.3.0 / Compose 1.10.0. Execucao do .exe empacotado, headless.

Cada linha mede uma geracao fria; raster e mediana de 30 frames aquecidos do V8Renderer.
Numeros atualizados pela reexecucao final de 2026-10-18, com 20 MISS. O cache anterior
foi renomeado e preservado dentro de `labs/art-lab/build/validation-cache`, sem apagar dados.
Essa repeticao verifica o mesmo tratamento artistico; nao e um novo ciclo de calibracao.

Pink Floyd: 941x1672; demais: 864x1536. Sem galeria/painel da janela Desktop na medicao.

| Entrada | Familia | Geracao (ms) | Raster CPU mediano (ms) | Reload disco total (ms) |
|---|---|---:|---:|---:|
| PINK_FLOYD | PRISMATIC | 148,89 | 31,80 | 423,08 |
| MEGADETH | DENSE_CHROMATIC | 84,77 | 25,82 | 113,44 |
| NIRVANA | DENSE_CHROMATIC | 69,14 | 25,95 | 112,57 |
| THRILLER | SELECTIVE_LUMINOUS | 76,64 | 25,78 | 104,94 |
| ABBEY_ROAD | DENSE_CHROMATIC | 69,84 | 25,93 | 98,55 |
| DAFT_PUNK | PRISMATIC | 80,34 | 26,91 | 89,30 |
| JOY_DIVISION | DRAMATIC_CONTRAST | 52,87 | 26,01 | 90,25 |
| KENDRICK | EDITORIAL_NATURAL | 74,22 | 25,89 | 98,41 |
| ASTROWORLD | DENSE_CHROMATIC | 73,11 | 25,86 | 109,09 |
| BLONDE | EDITORIAL_NATURAL | 77,15 | 25,92 | 89,50 |
| METALLICA | SELECTIVE_LUMINOUS | 66,44 | 25,90 | 91,82 |
| GRADUATION | DENSE_CHROMATIC | 73,63 | 25,55 | 89,09 |
| synthetic-black | MINIMAL_MONOCHROME | 58,79 | 27,13 | 29,59 |
| synthetic-white | MINIMAL_MONOCHROME | 60,63 | 27,44 | 29,85 |
| synthetic-intense-red | MINIMAL_MONOCHROME | 62,65 | 27,26 | 31,92 |
| synthetic-monochrome | MINIMAL_MONOCHROME | 77,53 | 26,23 | 32,26 |
| synthetic-multicolor | DENSE_CHROMATIC | 78,25 | 25,94 | 35,86 |
| synthetic-smooth-gradient | MINIMAL_MONOCHROME | 77,67 | 25,83 | 32,88 |
| synthetic-bright-photo-surrogate | MINIMAL_MONOCHROME | 76,67 | 25,77 | 37,37 |
| synthetic-low-contrast | MINIMAL_MONOCHROME | 75,31 | 25,54 | 33,34 |

Geracao: 52,87-148,89 ms, mediana do conjunto 74,76 ms. A primeira entrada inclui aquecimento da JVM.
Preparacao fria completa (decode + features + direcao + textura/IO + bitmaps Compose): 113,75-997,95 ms.
O total e CPU agora incluem a conversao nativa dos bitmaps, antes da publicacao do asset.
Raster CPU: medianas por entrada 25,54-31,80 ms; p95 por entrada 26,44-33,25 ms.
Raster CPU nao e benchmark de GPU/60 FPS, nem medida da janela completa ou do Redmi.
RGBA retido calculado: 6,75 MiB por capa 1200x1200 (inclui arte + cinco texturas + mapa), 1,32 MiB por fixture 128x128. Somente as cinco texturas: 1,25 MiB; tres assets oficiais no LRU: ~20,24 MiB. Exclui overhead e temporarios.

Segundo processo: **20/20 DISK**, zero analises e geracoes, mesmos hashes de receita e pixels. Tempos de generation/features/direction iguais a zero. O reload ainda decodifica a capa e le PNGs; nao representa custo zero.

Revalidacao final: 20 testes passaram novamente e a distribuicao Windows foi recompilada.
O executavel terminou ambas as execucoes com codigo 0. A captura do favorito agora aguarda
a recomposicao apos restaurar 43% de seek antes de invocar a acao semantica; uma assercao
confirma que a acao preserva pausa e seek. Antes, o callback antigo podia recapturar 100%.
Essa correcao e da rotina de validacao, sem excecao de imagem ou alteracao artistica.
Os 12 checksums oficiais e as edicoes nas dez novas respostas de origem foram reconferidos.

Inspecao final: catalog-contact-sheet.png (12), synthetic-contact-sheet.png (8), prismatismo corrigido nas duas saidas dessa familia, materiais isolados, contorno do favorito, viewport alto e fallback. Sem bordas retangulares no halo ou alteracao do gradiente durante seek pelas verificacoes de pixels.
A captura auxiliar 1080x2340 usa SansSerif do probe; as 20 capturas principais usam Inter empacotada. Essa captura verifica geometria/troca/fallback; nao e comparacao tipografica com o mestre.

Comandos, na raiz (executar sequencialmente):

```powershell
.\gradlew.bat -p labs/art-lab desktopTest
.\gradlew.bat -p labs/art-lab createDistributable
.\gradlew.bat -p labs/art-lab run "--args=--validate-procedural --cycle=3"
.\gradlew.bat -p labs/art-lab run "--args=--cache-probe --cycle=3"
.\gradlew.bat -p labs/art-lab run
```

`git diff --check`: passou. Aceite funcional desta rodada: PASS. Aceite estetico: NOT PASSED / pendente do usuario.

## 2026-10-18 — PLAYER_MICRO_INTERACTIONS_V1

### Arquivos desta etapa

- `labs/art-lab/src/commonMain/kotlin/nonstop/artlab/renderer/PlayerMicroInteractions.kt`:
  controlador, pool de partículas, perfis por família, porta de energia, RMS e clock.
- `labs/art-lab/src/commonMain/kotlin/nonstop/artlab/renderer/V8Renderer.kt`: glow,
  drag horizontal, hover/foco/pressão/teclado, partículas e resposta localizada do play.
- `labs/art-lab/src/desktopMain/kotlin/nonstop/artlab/DesktopAudioEnergy.kt`: leitura
  streaming de WAV local em worker e fixture PCM identificada, sem dispositivo de áudio.
- `labs/art-lab/src/desktopMain/kotlin/nonstop/artlab/Main.kt`: diagnóstico/entrada WAV,
  controles de conforto, suspensão minimizada e gravação de GraphicsLayer sob demanda.
- `labs/art-lab/src/desktopMain/kotlin/nonstop/artlab/MicroValidation.kt`: cenas reais,
  entrada local, sequência temporal e comparação de custo. Exporta GIF por leitura de
  pixels, com recortes apenas de controles, evitando comprimir a capa a cada frame.
- `labs/art-lab/src/commonTest/kotlin/nonstop/artlab/validation/PlayerMicroInteractionsTest.kt`
  e `labs/art-lab/src/desktopTest/kotlin/nonstop/artlab/DesktopAudioEnergyTest.kt`:
  testes de envelope, entrada inválida, fase estéreo, limites, dissipação e conforto.
- Esta seção, `PLAYER_V8_ART_LAB_SPEC.md` §21 e `labs/art-lab/README.md`: contrato,
  parâmetros, execução, evidências e limites. Receitas/diretor/cache 4.2 preservados.

### Comportamento e parâmetros finais

dp seguem a base lógica 432 dp existente, escalando com viewport/densidade.

| Efeito | Parâmetros |
|---|---|
| Agulha idle | Raio 6 dp, alpha .18, cor amostrada da barra na posição atual |
| Hover / foco / pressão | Intensidades .55 / .65 / .85 do intervalo idle→drag |
| Agulha drag | Raio 10 dp, alpha .34, escala até 1.08; smoothstep 180 ms |
| Trail | 8 partículas / 36 ms nominais; pool 80; vida 220–280 ms |
| Partícula | 2–6 dp (menor em famílias discretas), alpha inicial .10–.18, fade quadrático |
| Região do trail | Barra ±8 dp, sem tocar timestamps/texto; fim de emissão no release/cancel |
| Energia | RMS AC por canal em janelas de 20 ms; p95 global, gate .003 |
| Suavização do play | Attack τ=180 ms, release τ=360 ms, delta visual limitado a 48 ms |
| Modulação máxima | Aro existente +1.5 dp; halo +6 dp / +.12 alpha; corpo 1.02x |

Raios/alpha/escala ficam limitados; cor, fill e material não mudam com a energia.
Materiais preenchidos foscos mantêm ausência de aro; o halo concentra a resposta.
Ganhos por família são combinados pelos pesos da receita: prismático 1.0; denso .70;
editorial .35; dramático .70 com expansão menor; luminoso .60; minimalista .20;
fallback .25. Sem chave por artista/álbum e sem alterar o motor de arte.

Redução de movimento desliga trail, pulso e escala, mantendo indicação estática de
foco/pressão e glow. Minimizando/ocultando controles, efeitos param e partículas são
limpas. Sem fonte, não há pulso nem clock contínuo. Silêncio/energia estável não
invalidam desenho. Mudanças dinâmicas são lidas em draw/layer; o fundo fica estático.
O pool usa ~2,81 KiB em arrays; envelope de 8 s usa ~1,56 KiB de floats (exclui objetos
e temporários). Não foi medido pico de RAM. A captura manual grava o player somente
nos frames solicitados; não mantém gravação integral a cada atualização do efeito.

### Áudio e limites da integração

O laboratório continua uma simulação. WAV local é analisado em worker; play/pausa
avançam/congelam o relógio visual, e seek o reposiciona. A prévia é **silenciosa**:
não captura áudio do sistema, não toca o arquivo e não está associada aos álbuns.
O progresso simulado não avança sozinho com esse relógio. A mesma fixture PCM de
8 s (silêncio/tons com amplitudes distintas) alimenta as seis validações visuais.
Não se afirma sincronismo com gravações de Pink Floyd, Megadeth etc.

WAV até 256 MiB e 30 minutos, 1–8 canais, 8–192 kHz, limitado aos codecs que o
Java Sound consegue converter para PCM 16-bit. MP3/AAC/streaming não foram adicionados.
Erro de arquivo é identificado no diagnóstico; nenhuma ligação com Media3/produção.
Não há IA, shader customizado, blur dinâmico ou animação do fundo.

Attack/release são constantes de tempo exponenciais, não o instante de assentamento
total; ~63% da mudança após uma constante de tempo. Emissão é limitada pelo frame
clock e não acumula bursts após atraso. GIFs a 20 fps/432×304 são recortes de pixels
reais, para prévia temporal; a cor/layout devem ser avaliados nos PNGs 864×1536.

### Verificação

26 testes passaram, incluindo regressões anteriores, finitude/contraste, limites,
RMS sem cancelamento de fase, silêncio/DC, attack/release, foco/pressão, dissipação,
redução de movimento e suspensão. Distribuição Windows compilada e executada via
`--validate-micro`, sem janela nem entrada global. A simulação de mouse/foco acontece
somente dentro da cena Compose. Aprovação estética permanece do usuário.

### Resultado visual e impacto medido

Inspecionados os seis filmstrips e PNGs de drag/energia alta. Glow e partículas ficam restritos à barra, sem cobrir timestamps/textos; cores/fill permanecem estáveis. O play fosco mantém halo difuso, e os prismáticos respondem com aro mais presente. Não há fundo animado nem aparência metálica no botão. A aprovação estética final continua do usuário.

Validação final: seis execuções de drag/foco/seek pausado passaram; todas as partículas desapareceram após soltar; redução de movimento limpou o efeito. Seis GIFs válidos, 72 frames de 50 ms (3,6 s cada), gerados dos pixels reais. 61 partículas no pico observado, abaixo dos 80 slots.

Comparação no próprio executável empacotado, ImageComposeScene/Skia CPU a 864×1536, Inter, sem Window. Três rodadas alternadas × 20 frames por modo (60 amostras/modo/capa). Os dois modos de seek recebem os mesmos avanços de progresso; o modo ativo acrescenta partículas e energia PCM. PNG/GIF/IO e análise PCM ficam fora do cronômetro de raster.

| Capa | Idle desligado / ligado (ms) | Seek base / trail+áudio (ms) | Δ ativo (ms) |
|---|---:|---:|---:|
| PINK_FLOYD | 31.05 / 32.74 | 36.50 / 36.73 | +0.23 |
| MEGADETH | 30.30 / 31.15 | 33.47 / 33.58 | +0.11 |
| NIRVANA | 31.11 / 30.98 | 31.63 / 33.45 | +1.82 |
| THRILLER | 30.56 / 30.40 | 31.52 / 32.62 | +1.10 |
| ABBEY_ROAD | 30.14 / 32.15 | 31.80 / 34.36 | +2.56 |
| DAFT_PUNK | 32.36 / 33.65 | 35.99 / 33.29 | -2.70 |

Mediana dos deltas entre capas: **+0.66 ms** em seek/áudio e **+1.07 ms** em idle forçado. Maior delta ativo mediano: **+2.56 ms**. Valores negativos refletem variação do ensaio; não significam aceleração causada pelo efeito.
Nos 60 frames apenas de energia, **zero recomposições do player externo nas seis capas**. Sem entrada/fonte, não há loop contínuo de animação; em silêncio estável não há invalidação de draw. A medição de idle acima força rasterizações que normalmente não ocorreriam com a tela parada.
A primeira coleta encontrou ~+6 ms de CPU em Megadeth com um gradiente por partícula. Substituição por dois discos antialiasados tênues reduziu o delta desse caso para +0,11 ms na coleta final. Coleta preliminar parcial preservada em `pre-optimization/`; somente `performance.json` da raiz representa a versão final.
Os p95 e máximos estão em `performance.json`. Há variação/GC no raster CPU; **não é medição de GPU, jank, FPS sustentado ou Redmi Note 14**. O ensaio demonstra custo local limitado e ausência de recomposição externa por áudio; a ausência de perda perceptível em hardware Android ainda precisa de perfil no dispositivo. Nenhum desempenho de 60 FPS foi declarado.

Artefatos finais: `validation/micro-v1/` (60 PNGs completos/filmstrips, seis GIFs de controles, `performance.json`, `complete.txt`). Fixture PCM local, sem áudio das gravações dos álbuns. `desktopTest`: 26/26; `createDistributable`: passou; exe `--validate-micro`: exit 0; `git diff --check`: passou.

```powershell
.\gradlew.bat -p labs/art-lab run
.\gradlew.bat -p labs/art-lab desktopTest
.\gradlew.bat -p labs/art-lab run "--args=--validate-micro"
```

Estado: implementação funcional da etapa concluída no laboratório; aceite estético pendente. App principal, Media3 e PlayerV7 intocados. Sem commit, push ou merge.

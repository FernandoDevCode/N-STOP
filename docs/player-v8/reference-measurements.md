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

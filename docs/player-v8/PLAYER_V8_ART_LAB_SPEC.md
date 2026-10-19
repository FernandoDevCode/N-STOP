# NØNSTOP — PLAYER V8 ART LAB — ESPECIFICAÇÃO NORMATIVA
**Versão:** 1.4 · **Data:** 2026-10-18 · **Branch:** `feat/nonstop-v8-art-lab` · **Estado:** laboratório procedural e PLAYER_MICRO_INTERACTIONS_V1 autorizados; NÃO aprova integração ao app principal.

## 0. Contrato de interpretação
1. **Fonte de verdade única para v8:** este documento. `docs/PLAYER_V7_VISUAL_FIDELITY.md` e demais documentos v7 servem SOMENTE como histórico e referência das funcionalidades intocáveis; em conflitos, este documento prevalece.
2. **Referências visuais mestras:** duas imagens aprovadas pelo usuário, uma do player com *Megadeth — Rust in Peace*, outra com *Pink Floyd — The Dark Side of the Moon*. Seus arquivos devem ser identificados/confirmados antes da implementação. **Não usar screenshots da implementação v7 como referência estética mestra.**
3. Não inventar dimensões extraídas de imagem. Se as referências mestras não estiverem disponíveis em arquivo legível localmente, **PARAR antes de construir a geometria** e pedir localização; não substituir por imagens da internet ou pelas capturas v7. Não tomar mockup fictício como capa de produção.
4. O usuário solicita aproximação **1:1 da aparência das duas referências**, não “melhorar” ou reinterpretar. A reprodução pixel a pixel só pode ser avaliada quando imagem de referência e render tiverem mesmo tamanho, densidade e fontes; discrepâncias desses fatores devem ser registradas. “1:1” é **meta de fidelidade**, não garantia técnica.
5. Onde não houver medida fundamentada, usar valor inicial **provisório explicitamente identificado** e calibrá-lo por comparação. Proibido chamar uma estimativa de pixel-perfect comprovado.
6. Tudo que for visualmente subjetivo requer **aprovação explícita do usuário**. O Codex não declara PASS artístico sozinho, nem altera a composição por suposta melhoria.
7. **Nunca criar exceções por nome de artista, álbum, faixa, hash conhecido, ID da imagem ou ordem da galeria.** Referências Megadeth/Pink Floyd são amostras, não comandos de cor hardcoded.
8. **Sem commits, pushes ou merges automáticos pelo Codex** até autorização separada; alterações de documentos feitas no GitHub pelo assistente têm seus próprios commits.

## 1. Objetivo, saída e limite da Fase 1
Construir **NØNSTOP Art Lab**, aplicativo experimental de desenvolvimento com **código de interface compartilhável entre Windows Desktop e Android** via Kotlin + Compose Multiplatform. Em vez de gerar PNGs estilizados como produto final, **o efeito tem de ser renderizado em tempo real por componentes reais do app**.
- A interface de laboratório exibirá galeria de 12 capas, player navegável e painel de diagnóstico de IA/receita.
- A mesma receita visual dirige o renderer Desktop e o Android, sem manter versão React paralela.
- Motor híbrido: **análise clássica de pixels + modelo visual local tipo MobileCLIP2-S0 + compositor de camadas leves**.
- **Fase 1** só constrói aplicativo laboratório Desktop funcional + baseline visual e infraestrutura/testes de análise; Android experimental é Fase 2, após o laboratório ser revisado. Arquitetura deve permitir Fase 2 sem reescrever o renderer. Caso dependências tornem Compose Desktop inviável, relatar bloqueio com evidência e alternativa, não migrar para React por conta própria.
- Não integrar no `feature/nowplaying` de produção, não substituir PlayerV7, não mudar Media3, biblioteca, Cast, downloads, fila, banco, autenticação, letras reais, playback nem checkpoint 1.
- Controles do laboratório são **simulados** e identificados como tal; não requisitam conta nem streaming. Nenhuma letra inventada é apresentada como letra real.

## 2. Definições exatas
- **Cover**: imagem bitmap original do álbum, sem coloração aplicada.
- **Features**: vetor numérico medido por análise clássica, por exemplo cobertura escura e centroide de luminosidade.
- **Embedding visual**: vetor da IA para a capa; não é descrição livre nem mapa de pixels.
- **Archetype/arquétipo**: classe estética inferida com escore e confiança; pode ter distribuição/mistura, não decisão categórica obrigatória.
- **Recipe/receita**: parâmetros tipados e serializáveis, determinados pela capa. Não é imagem.
- **Renderer**: UI Compose que consome Cover + Recipe e produz pixels em tempo real.
- **Precomputation/pré-processamento**: gerar uma textura/atlas leve uma vez por capa, fora da UI; permitido.
- **PASS funcional**: testes automatizados e execução sem regressão. **PASS visual da rodada inicial**: somente aceite do usuário após comparar Pink Floyd e Megadeth. **PASS visual total**: exige posteriormente o benchmark de generalização com as outras dez capas.
- **Fallback premium**: receita conservadora, ainda com profundidade real e material de controle; jamais tela chapada de uma só cor.

## 3. Arquitetura e separação estrita
```
Bitmap → ImageFeatures (CPU/downsample)
       → VisualEmbedding (IA opcional, off UI thread)
       → ArchetypeScores + Confidence
       → ArtRecipe (serializável, determinística)
       → PrecomputedAtmosphere (opcional; uma vez por hash/receita)
       → V8Renderer (Compose Multiplatform)
       → Desktop preview / Android preview (futuro)
```
Módulos ou pacotes separados: `features`, `inference`, `direction`, `renderer`, `catalog`, `validation`. O renderer **não** chama IA e **não** calcula histograma a cada recomposição/frame. O mesmo `ArtRecipe` deve produzir a mesma composição lógica.
Dependência do modelo tem adaptador substituível `VisualEncoder` e implementação `NoModelEncoder`/fallback. Se o modelo não puder rodar na Fase 1, pipeline e interface continuam utilizáveis, mas informar claramente **“IA indisponível — fallback”**, sem afirmar ter IA funcionando.

## 4. Escolha e requisitos da IA
Modelo candidato inicial: **MobileCLIP2-S0**. Antes de baixar, verificar fonte oficial, termos dos pesos, tamanho e backend viável para desktop; uso é pessoal, mas **não assumir automaticamente licença irrestrita**.
- Não treinar modelo, não criar API paga, não enviar capas para serviços externos e não depender de inferência em nuvem.
- CLIP-like permite **pontuar semelhança entre imagem e conjuntos fixos de descrições** (zero-shot). Não afirmar que identifica objetos icônicos ou localiza um prisma com precisão espacial.
- Descrições das classes devem ser equilibradas em quantidade, linguagem e estrutura; guardar arquivo versionado com as descrições; registrar escore bruto, diferença entre classes e confiança calibrada apenas após benchmark. Não chamar softmax sozinho de “probabilidade de estar certo”.
- Executar **uma vez por hash da capa + versão do modelo + versão do classificador**; cache em disco e memória. Erro de inferência → fallback clássico, sem bloquear UI.
- Decoder de imagem reduz a dimensão para análise; suportar PNG/JPEG e testar imagem malformada/corrompida. Não carregar múltiplas cópias em resolução total.
- Sem IA generativa criando wallpapers/previews. O motor produz parâmetros e camadas renderizáveis.

## 5. Features espaciais obrigatórias
Calcular sobre imagem reduzida e normalizada, com coordenadas [0,1], mantendo proporção:
- Fração de luminância baixa, média e alta (faixas **configuráveis**, registradas e testadas).
- Distribuição de luminância/saturação em grade **4×4** ou mais refinada se comprovada necessidade.
- Percentual e posição (centroide) de pixels altamente saturados, condicionados à luminância.
- Paleta: grupos cromáticos com cobertura relativa, distância perceptual (Lab/OKLab quando viável), contraste contra canvas, e região em que cada grupo aparece.
- Distinção entre cor de fundo/área negativa e pequenos sinais de alto contraste.
- Densidade de bordas/linhas, distribuição de detalhes e estimativa de complexidade; **não** chamar isso de reconhecimento semântico de um objeto.
- Mapas de importância espacial: combinar contraste local, saturação e bordas. Produzir e visualizar mapa de diagnóstico, sem afirmar saliência humana perfeita.
- Explicabilidade por capa: registrar os **cinco sinais mais relevantes** para escolha da receita.
Normalizar esses sinais para não dominar capas muito escuras, claras ou monocromáticas. Valores default são hipóteses versionadas, não limites universais.

## 6. Arquétipos iniciais (escores, sem regras por álbum)
| ID | Evidência visual | Direção de composição |
|---|---|---|
| DARK_SYMBOLIC | muita área negativa escura + foco central/local de contraste | canvas escuro; luz focal localizada e material de controle contido |
| PRISMATIC_ACCENTS | base escura + clusters saturados pequenos e espacialmente separados/lineares | acentos espectrais seletivos, sem inundar canvas |
| CINEMATIC_DENSE | grande cobertura cromática + detalhes distribuídos + variação de luz | ambiente profundo derivado de regiões da capa |
| COOL_DEPTH | predominância fria + contraste em regiões distintas | luzes frias assimétricas com variação tonal |
| WARM_DIFFUSE | paleta quente + luminosidade ampla/transições graduais | reflexos quentes difusos, tinta legível |
| MONOCHROME_EDITORIAL | saturação baixa, formas de alto contraste | material neutro, profundidade por luminância |
| PHOTO_PORTRAIT | evidência forte de estilo retrato/fotografia, se IA sustentar | luz respeitando área central e identidade fotográfica |
| MULTICOLOR_GRAPHIC | cobertura cromática extensa e equilibrada | seleção de 1–2 acentos, fundo unificador profundo |
| PREMIUM_FALLBACK | evidência inconclusiva | canvas escuro com duas luzes sutis, textura abstrata, botão refinado |
Os arquétipos **não** devem virar um `when` rígido exclusivo: o diretor pode misturar no máximo dois tratamentos compatíveis, com pesos registrados. Exigir limites de saturação, luminância e legibilidade no resultado. Se houver conflito entre IA e features, usar estratégia conservadora e mostrar divergência no painel diagnóstico.

## 7. Receita tipada: contrato mínimo
Representar em data classes Kotlin serializáveis, sem strings mágicas:
- `schemaVersion`, `sourceImageHash`, `modelVersion`, `confidence`, `archetypeScores`
- Receita v3: `canvasColor`, `scrimTop`, `scrimBottom`; `environment` (regiões profundas, grain, warp); `accents[]`; `directionalDetails[]` (posição, orientação axial, extensão, largura, cor, intensidade, coerência). `artTextureOpacity` e `artBlurRadiusDp` foram removidos na Fase 1B.2.
- `lights[]` (iluminação difusa) e regiões/acentos com `x`, `y`, `radiusX`, `radiusY`, `color`, `intensity`, `falloff` (posições relativas). `motionAmplitude` permanece removido; não há parâmetro de movimento sem comportamento.
- `controlSurface`: fill base/gradient stops, specular highlight, outer halo color/intensity/radius, ink color
- `progress`: fill/track, até N paradas cromáticas com localização espacial fixa [0,1], thumb/halo
- `lyrics`: foreground active/future, alpha progression e surface treatment (mesmo sem letras reais no laboratório)
- `analysisMetadata`: features resumidas, origem das decisões, fallback reason
Saneamento **obrigatório** de todos os valores, checagem de contraste/NaN, limites de luz/alpha; rejeitar receita inválida com fallback estável. JSON deve ser salvável/recarregável e versionado.

## 8. Renderização: fidelidade antes de técnica
- **Objetivo:** emulação perceptiva fiel das referências, não cópia literal dos shaders imaginados no mockup.
- **Proibido como padrão:** efeitos custosos refeitos a cada frame, blur de bitmap integral contínuo, IA na thread UI, backgrounds de cor chapada, glow retangular ou bounds visíveis, bordas cinza artificiais, rainbow decorativo sem suporte na capa.
- Compositor inicial **sem shader customizado obrigatório**: máscaras/texturas abstratas reduzidas geradas uma vez a partir da receita, camadas Compose com gradientes radiais/elípticos, scrims, imagens em cache, gradientes e efeitos estáticos/movimentação lenta dos offsets.
- Cada fonte luminosa deve ter radius e centro independentes, com caída até alpha zero e sem clipping quadrado. Cor/posição partem da `ArtRecipe`.
- Luz principal e secundária não devem lavar informações nem transformar as artes em gradiente chapado. Fundo escuro preserva detalhes de luminância; capa continua protagonista.
- Botão play circular: preenchimento material (gradiente/reflexo interno), ícone contrastante centrado, **halo externo realmente visível e contínuo**, sem quadrado. Dimensionar glow à aparência referência; checar captura antes de ajustar.
- Pink Floyd **não** deve receber tratamento especial por nome; para artes do padrão PRISMATIC_ACCENTS: canvas quase preto, luz branca/fria superior quando features suportarem, reflexos coloridos localizados e discretos, barra espectral se clusters e confiança sustentarem; não fabricar arco-íris em qualquer capa preta.
- Megadeth **não** recebe tratamento por nome; para artes COOL_DEPTH/CINEMATIC_DENSE: base azul-noturna e 2+ regiões de luz independentes/variadas, com sensação de profundidade, não wash azul homogêneo.
- Barra de progresso usa coordenadas cromáticas fixas pelo comprimento total; playback revela a seção, **nunca reescala o gradiente conforme percentual preenchido**.
- Letras de demonstração: usar rótulos como “Linha de teste 1/2” ou geometria abstrata, claramente marcados como simulação; nunca atribuir versos falsos a músicas reais.
- Animação: opacidade/offset devagar, respeitar redução de movimento; pausada quando janela invisível. Sem flashes, flicker ou blending com clipping.
- Só introduzir shader SkSL se houver evidência de que a alternativa sem shader não atende fidelidade ou orçamento de frame; documentar decisão e benchmark.

## 9. Geometria mestra e responsividade
**As duas referências definem o layout**, não capturas v7. Durante o primeiro sprint criar arquivo `reference-measurements.md` com largura/altura real dos dois PNGs, retângulos de capa/barra/bloco título/controles/LETRAS, tamanhos e offsets em pixels, normalizados para [0,1]. Exigir inspeção visual e, quando houver incerteza, documentar tolerância em vez de inventar precisão.
- Layout principal: topo mínimo (fechar/esquerda, menu/direita), capa grande quadrada, progresso imediatamente abaixo da arte, informação editorial e coração independente, transporte com play dominante, alça LETRAS integrada à base.
- Capa sem distorção, com `ContentScale.Crop`. Na Fase 1B.2, envelope comum concilia os retângulos levemente não quadrados medidos nos mestres (largura .834W, altura limitada a .4597H); em viewports altos a capa permanece quadrada. Mesma geometria nos dois modos e sem ajustes por identidade.
- Tipografia semibold/editorial; título até duas linhas; artista/álbum legíveis; sem marquee no cabeçalho compacto das letras.
- Áreas de toque >=48dp; gestos não interceptam seek/scroll.
- Player escala com insets, densidade e dimensão do painel, mantendo hierarquia; nunca fazer sobreposição em fontes aumentadas.
- Desktop deve incluir `device frame` simulado ou viewport ajustável para **1080×2340**, e preview a 100% dos pixels renderizados. Verificar orientação retrato e um segundo tamanho de tela.
- Sem forçar fontes do mockup não disponíveis; escolha e fallback tipográfico devem ser explicitados no relatório de medidas.

## 10. Catálogo de validação — 12 capas
**Referências obrigatórias:**
1. Pink Floyd — *The Dark Side of the Moon*
2. Megadeth — *Rust in Peace*

**Generalização — outras 10:**
3. Nirvana — *Nevermind*
4. Michael Jackson — *Thriller*
5. The Beatles — *Abbey Road*
6. Daft Punk — *Random Access Memories*
7. Joy Division — *Unknown Pleasures*
8. Kendrick Lamar — *To Pimp a Butterfly*
9. Travis Scott — *Astroworld*
10. Frank Ocean — *Blonde*
11. Metallica — *Master of Puppets*
12. Kanye West — *Graduation*

A lista é **fixa no primeiro benchmark**; mudanças exigem revisão do documento. A aquisição das capas precisa respeitar fontes/licenças; para uso no laboratório local, Codex solicita imagens ao usuário caso fontes verificáveis não estejam disponíveis, sem incorporar credenciais ou scraping instável. Guardar manifesto com artista, álbum, URL/fonte (se conhecida), checksum e status `available/missing`. Não inventar artwork nem substituir por capa distinta silenciosamente. Imagens de mockups e capas originais podem ser diferentes; referenciar qual serve a cada teste.

**Ordem obrigatória do benchmark (revisão 1.1):** a Fase 1B implementa e calibra o motor
exclusivamente com as capas completas de Pink Floyd e Megadeth e seus dois PNGs mestres.
As outras dez entradas permanecem placeholders no catálogo; não capturar, analisar nem refinar
suas telas nesta rodada. A generalização para elas ocorre somente depois da validação das duas
referências. Essa restrição de benchmark não permite regras específicas por identidade no motor.

## 11. UI do laboratório
- Grade de 12 capas com nome do álbum, estado de disponibilidade e score resumido.
- Selecionar uma capa abre **player renderizado de verdade** com layout v8 e a mesma receita que deverá ir ao Android.
- Navegação próxima/anterior capa; controles play/pause de simulação, seek em 0, 25, 50 e 100%; LETRAS expandidas de demonstração; toggles de animação e redução de movimento.
- Painel diagnóstico ocultável **fora da área de 1080×2340**: features, top-3 arquétipos e scores, confidence, modelo/carregamento, cache hit/miss, CPU time, receita exportável, parâmetros das luzes, divergências de fallback.
- Modos de depuração: arte + base, luzes isoladas, scrim isolado, botão isolado e composição final. Nunca deixar modo debug ligado por padrão no player.
- Opção de carregar **capa local adicional** sem reconstruir projeto; receita calculada sob mesma política. Não criar botão que não funcione.
- Alternar capa deve trocar receita suavemente, sem recriar a aplicação e sem congelar input.
- Comando de execução via terminal documentado e simples (uma vez instaladas dependências); objetivo experiência próxima de `npm run dev`, não prometer hot reload idêntico a React.

## 12. Performance e orçamento de engenharia
Metas iniciais **a medir**, não resultados declarados:
- UI responsiva no PC do usuário: i5-9400F, RTX 5060, 16 GB RAM; não exigir Android Studio aberto.
- Em Android: alvo 60 FPS na tela do player após estabilização, medindo frame times/jank no Redmi Note 14; sem prometer antes de testar.
- Uma análise de capa por hash/versão; cache persistente e política de invalidação.
- Renderização sem trabalho pesado por frame; limitar número de texturas e resoluções efetivas conforme viewport.
- Modelo só carregado durante análise quando viável; informar pico real de RAM, tamanho dos pesos, inferência cold/warm e tamanho adicional do APK ao integrar.
- Degradação controlada: em dispositivo lento reduzir layers/blur de maneira definida e verificável, não transformar em fundo chapado.
- Sem rede durante inferência após instalar pesos localmente. Download inicial do modelo apenas se explicitamente aceito e documentado.
- Diagnóstico inclui timings por fase, contagem de recomposições quando útil e uso aproximado de memória. Sem alegar benchmarks não executados.

## 13. Critérios de aceite
### Critérios objetivos automatizáveis
A. Projeto compila e inicia no Desktop sem modificar módulos de produção. Galeria abre 12 entradas (com placeholders identificados só quando imagens ausentes).
B. Mesma entrada+versão gera mesma receita serializada (descontadas métricas de timing). Cache hit não executa inferência novamente.
C. Modelo indisponível/decode inválido não causa crash nem UI travada; mostra fallback e causa.
D. Progresso não reescala gradient em 0, 25, 50, 100%. Nenhum halo revela borda retangular nos estados testados.
E. Controles clicáveis no lab, 2 viewports, modo LETRAS demo, troca rápida de capas, redução de movimento.
F. `git diff --check`, testes direcionados, execução real com captura e **inspeção das screenshots**.
G. Comparação inicial com as duas referências; depois expandir para as outras dez. Registrar configuração, receita e capturas reais do aplicativo, nunca substituir por PNGs gerados separadamente.

### Aceite visual exclusivamente humano
- **Referências Pink Floyd e Megadeth:** buscar correspondência 1:1 em geometria/composição/aparência do mockup, usando imagens originais e comparações side-by-side na mesma escala. Não aceitar apenas “ficou mais bonito”; não autorizar modificações estéticas deliberadas para “melhorar”.
- **Outras 10:** cada resultado deve ser específico da capa, não uma mesma atmosfera recolorida; o usuário decide se o acabamento é compatível com as duas referências. Números de score não substituem aprovação.
- **PASS total** somente depois do usuário aprovar as duas referências e o lote geral. Se qualquer referência falhar, status **NOT PASSED**, com diferenças registradas, sem afirmar sucesso.

## 14. Plano de execução e checkpoints do Codex
**Fase 0: auditoria (sem UI).** Confirmar branch, status/diff, referências mestras legíveis, JVM/JDK/Gradle e dependências Compose. Reportar riscos de compartilhamento Desktop/Android e licença do modelo. Não tocar em produção.
**Fase 1A: esqueleto Desktop.** Criar módulo/lab isolado, galeria, player estático Compose com dimensões mensuradas e alternância de capa. Tirar screenshots reais. Não mascarar ausência de IA.
**Fase 1B: motor clássico + recipes.** Pipeline de features, arquétipos, cache, fallback, componentes visuais e painel diagnóstico. Render em tempo real; comparar exclusivamente Pink Floyd e Megadeth em até três ciclos. As demais dez capas ficam como placeholders, fora desta rodada.
**Fase 1C: IA.** Integrar MobileCLIP2-S0 caso compatível, com validação do download/licença e inferência local. Testar comparação **com vs. sem IA** na mesma amostra e receita. Se modelo não viável, propor alternativa com dados; não bloquear Fase 1B nem afirmar que IA está ativa.
**Fase 1D: calibração.** Iterar luz/forma/posição nas referências, sem exceções por álbum. Depois validar o conjunto de dez capas sem ajustar manualmente por caso. Até três ciclos por sprint; reportar mudanças e pedir decisão quando meta visual não for alcançada.
**Fase 2 (somente após PASS do laboratório):** adicionar alvo Android experimental/compartilhado; medir Redmi. O app principal continua inalterado.
**Fase 3 (somente após PASS Android):** integrar o renderer compartilhado no player principal atrás de feature flag, preservando v7 de fallback.

## 15. Regras de reporte
Ao final de cada fase informar:
1. Arquivos alterados e motivação.
2. O que de fato rodou no Desktop/Android e screenshots reais analisadas.
3. Se modelo IA está ativo, versão e prova de inferência; se não, dizer claramente.
4. Tempo de análise, cache e limitações, com números apenas quando medidos.
5. Diferenças concretas para a referência; sem adjetivos vazios como “premium” sem apontar evidência.
6. O que falta, testes executados, riscos, como executar com comando simples.
7. Nunca dizer que está 1:1 sem avaliação na mesma escala e aprovação do usuário.

## 16. Proibições
- Não reescrever o app NØNSTOP, não modificar PlayerV7 funcional, Media3/serviços/backends.
- Não criar mockups gerados por IA no lugar de implementar renderização.
- Não trocar automaticamente para React, WebView/Capacitor ou shaders complexos.
- Não hardcodar artista, álbum, cor, coordenadas ou comportamento específico para os dois casos de teste.
- Não buscar capas de terceiros em fontes não verificadas nem registrar chaves de API.
- Não declarar sucesso por `BUILD SUCCESSFUL`, nem aprovar o visual sozinho.
- Não criar uma pilha indefinida de documentação: esta é a **única especificação operacional da v8**; novos aprendizados devem ser anexados com seção de decisão versionada e justificativa.

## 17. Registro de execução — decisão 2026-10-17 / Fases 0 e 1A

- Laboratório independente em `labs/art-lab`, Kotlin 2.3.0 / Compose Multiplatform 1.10.0,
  com renderer em `commonMain` e adaptadores Desktop separados. Justificativa: executar
  e medir a UI sem configurar/modificar os módulos Android de produção.
- Evidência, medidas e limitações: [reference-measurements.md](reference-measurements.md).
  Esse registro e o README do laboratório não constituem outra especificação operacional.
- Mestres verificados por SHA-256; arte disponível apenas por recortes identificados dos
  mockups. Dez capas ausentes permanecem `missing`. Justificativa: não incorporar arte
  substituta ou de fonte não verificável.
- Baseline estático neutro, sem classificação ou IA. Fonte Inter existente usada como
  fallback explícito. Geometria compartilhada inicialmente concilia as duas amostras;
  caixas quadradas diferem dos retângulos levemente não quadrados dos mestres.
- Risco preliminar para a Fase 1C: termos oficiais dos pesos MobileCLIP restringem Research
  Purposes e excluem product development. Nenhum peso foi baixado; reavaliar antes de integrar.
- Aceite artístico continua **NOT PASSED / pendente do usuário**. Este registro não autoriza
  integração Android/produção, commit, push ou merge.

## 18. Registro de execução — decisão 2026-10-17 / Fase 1B

- Partida verificada: HEAD `4cf612ce145e44170d9fe38c09863abd56d85b4f`, branch
  `feat/nonstop-v8-art-lab`. Evidências 1A preexistentes preservadas. Produção intocada.
- Capas completas obtidas do catálogo oficial Apple Music/iTunes: Pink Floyd, coleção
  `1065973699`; Megadeth, coleção `724648893`. Imagens JPEG 1200×1200, fontes, edição,
  copyright e checksums em `labs/art-lab/cover-sources.json`. A imagem oficial é usada tanto
  para análise quanto para exibição, sem depender dos recortes dos mockups. Disponibilidade
  para validação local não significa licença irrestrita de redistribuição dessas artes.
- Motor comum `features` → `ClassicalDirector` → `ArtRecipe` v2 → renderer Compose existente.
  O diretor não recebe artista, álbum, ordem ou identidade conhecida. Hash é usado somente
  como identidade opaca de origem/cache. Não foi instalado ou executado modelo neural.
- Política clássica versionada/configurável: Rec.709 linear; baixa luminância <.035, alta ≥.32;
  saturação HSV ≥.5 condicionada a V≥.18 e luminância ≥.004; grade 8×8. A grade oferece
  distribuição mais fina que 4×4 sem afirmar localização semântica do prisma ou de pessoas.
- Paleta: 24 grupos de matiz e neutros, separando antes a região quase preta (V<.12), com
  cobertura, centroide, dispersão, OKLab e contraste contra o canvas. Acentos de saturação
  moderada também são mantidos na paleta; não precisam satisfazer o limiar de alta saturação.
  Importância local combina contraste, saturação ponderada por luminância e bordas; não é
  saliência humana validada. Cinco sinais e scores heurísticos são expostos no diagnóstico.
- Área negativa + pequenos grupos separados gera tratamento espectral localizado; cobertura
  fria/distribuída gera canvas azul profundo, textura e luzes frias regionais. Até dois
  tratamentos têm pesos registrados. Todos são hipóteses clássicas, sem confiança neural ou
  interpretação dos scores como probabilidades.
- Textura usa a capa inteira reduzida a até 192 px, sem recorte central. Blur gaussiano
  separável replica bordas e usa o raio da receita relativo à largura lógica base 432.
  Precompute e análise ficam fora da UI thread. Sem shader customizado ou blur integral por frame.
- Renderer consome canvas, scrims, textura, centros/raios/cores/intensidade/queda das luzes,
  posições dos stops, reflexo, halo, rim/diâmetro do play, tinta, favorito e estilos das letras.
  `motionAmplitude` foi removido; transição pode ser desativada por redução de movimento.
  Campos de identidade e análise são expostos no diagnóstico/exportação, não comandos de estilo.
- Cache de features/receita/textura em memória e disco: bytes da capa + versão do pipeline +
  política serializada. Corrupção de JSON/PNG resulta em recomputação. Modelo permanece `none`.
- Verificação final: 12 testes direcionados, distribuição Windows e execução real do `.exe`.
  Determinismo foi testado por recomputação e round-trip, além de cache memory/disk/invalidação,
  nomes de álbum alterados, diferença real entre receitas, contraste ≥4.5:1 do material com
  amostras de gradiente/reflexo, NaN/valores inválidos, bordas do precompute e acentos periféricos.
- Capturas somente das duas referências: resoluções 941×1672 e 864×1536, controles/seek,
  luzes e botão isolados, LETRAS demo, fonte 150% e viewport 1080×2340. As outras dez entradas
  permaneceram placeholders, sem benchmark visual/análise/refinamento nesta rodada.
- Três ciclos preservados em `docs/player-v8/validation/phase-1b/`: (1) implementação inicial
  mostrou wash claro e excesso de tons quentes; (2) exposição/área negativa/máscara do halo
  corrigidas; (3) acentos moderados azul/violeta preservados, entrelinha/encaixe de título
  corrigidos e material/diâmetro calibrados por evidência cromática. Correções funcionais de
  seek e validação de limiares não constituíram um quarto ciclo estético.
- Evidências são capturas do GraphicsLayer usado pela UI em execução; comparações apenas
  colocam mestre e captura lado a lado, sem redimensioná-los. Nenhuma interface foi substituída
  por PNG gerado separadamente. Pixels fixos da barra permanecem iguais em 25/50/100%.
- Limitações: artes oficiais diferem das artes estilizadas nos mestres; luz branca ainda é
  difusa, reflexos espectrais e cor do favorito diferem, textura densa contém regiões claras/
  quentes excessivas e restam diferenças geométricas/tipográficas. Generalização e Android
  não foram validados. Detalhes medidos e arquivos estão em `reference-measurements.md`.
- Estado: motor clássico funcional; **visual NOT PASSED / validação do usuário pendente**.
  Sem aprovação estética automática, integração de produção, commit, push ou merge.


## 19. Decisão 2026-10-17 — Fase 1B.2 / compositor abstrato

- Partida: `408f5f668fcbb02589e2d378c81eb8ae48f43f25`, branch
  `feat/nonstop-v8-art-lab`. Preservar laboratório, catálogo, controles, cache e fronteira
  commonMain/Desktop. App principal e dez placeholders fora desta etapa.
- **Quatro componentes obrigatórios:** ambiente profundo, iluminação difusa, acentos locais,
  detalhes direcionais. `AtmospherePrecomputer` comum recebe somente `ArtRecipe` v3,
  nunca o bitmap. Gerar quatro máscaras 192×342 com quedas contínuas e uma composição
  opaca em cache para reduzir overdraw. Não reamostrar a capa como fundo; textos, rostos
  e formas não são copiados. Sem shaders personalizados e sem análise por frame.
- Ambiente profundo combina até cinco regiões cromáticas espaçadas, deformação suave e
  granulação analítica determinística. Luzes e acentos têm alcance/queda independentes.
  Todos os parâmetros de estilo da receita têm consumidor; caches de v2 são invalidados
  pela versão do pipeline e pela validação do schema.
- Features v3 acrescentam orientação/coerência por covariância dos grupos cromáticos,
  histograma axial de gradientes RGB e tensor de estrutura por célula 8×8. Coerência
  mede anisotropia, **não confiança semântica**. Direções locais fracas são omitidas;
  padrões esparsos usam segmentos cromáticos alongados somente se coerência >.45,
  reflexo neutro >.5; padrões densos usam detalhes locais discretos >.18.
- Materiais de controle: preenchimento/reflexo contido, aro espectral ou linear, halo
  circular com máscara até zero. Famílias cromáticas de 30° mantêm os pequenos sinais
  vermelhos que antes eram absorvidos pelo grupo laranja, sem inserir novas cores por nome.
- **FIDELIDADE** exibe somente o recorte da arte do mockup aprovado; **PRODUÇÃO** exibe
  a capa oficial verificada. Ambos recebem exatamente a receita analisada da capa oficial.
  Recortes não participam de features/cache/direção. Modos alteram a exibição, não o motor.
- Geometria compartilhada corrige a extensão vertical da capa, usa âncora própria para
  progresso e ajusta espaçamento do transporte pela redução tipográfica medida do título.
  Removida decisão de tamanho por comprimento do nome. Inter permanece fallback explícito.
- Validação exclusivamente nas duas capas, até três ciclos em
  `validation/phase-1b2/cycle-1..3`. Capturas reais de `ImageComposeScene`/Skia em 941×1672
  e 864×1536; comparações sem redimensionar mestre/render. Nenhuma janela, captura da tela,
  troca de foco ou entrada global é necessária. `--capture-suite` também usa o caminho
  offscreen; o processo termina automaticamente.
- Estados automáticos: dois modos, 0/25/50/100%, pausa por ação semântica Compose, seek por
  evento interno à cena preservando pausa, fonte 150%, letras demo, iluminação e botão
  isolados. Verificar gradiente fixo e quatro cantos externos do halo nos pixels reais.
- Testes: determinismo, JSON/NaN/contraste, orientação de fixtures rotacionadas, resposta
  efetiva de cada camada, bordas transparentes/continuidade das máscaras, cache memória/
  disco/invalidação/reparo, nomes alterados e receitas diferentes. Performance é medida
  por fase e em 12 frames raster aquecidos por modo; não é benchmark de GPU/Android/60 FPS.
- Três ciclos encerram esta calibração. Limites e números medidos em
  `reference-measurements.md`; não continuar ajustando indefinidamente nem afirmar que
  covariâncias recuperam a geometria semântica de um prisma ou cenário.
- Motor continua **clássico, sem modelo neural**. Visual **NOT PASSED / aguarda o usuário**.
  Sem integração, commit, push ou merge.

## 20. Decisão 2026-10-18 — Procedural Art Director (pedido explícito do usuário)

Esta decisão registra a evolução de escopo solicitada em “NØNSTOP v8 — IMPLEMENTAÇÃO
DO PROCEDURAL ART DIRECTOR”. Preserva os avanços da Fase 1B.2 e prevalece sobre as
restrições de rodada/IA anteriores **nesta execução**:

- Benchmark ampliado expressamente para as **12 capas originais** do §10, mais oito
  fixtures sintéticas. A antiga restrição a duas capas encerrou-se por este pedido;
  isso não representa aprovação estética dos resultados anteriores ou atuais.
- Pipeline atual exclusivamente clássico/procedural, **sem IA/inferência, modelo,
  download de pesos ou IA generativa**. Adaptador opcional histórico não é executado.
- Seis famílias combináveis (no máximo duas): prismático/acentos isolados, cromático
  denso, editorial/natural, contraste dramático, luminoso seletivo e minimalista/
  monocromático; composição neutra para evidência inconclusiva. Escores são heurísticos.
- Receita v4 acrescenta `family`, `familyWeights`, `colors` (ambiental/interação/acento)
  e `procedural` (seed, escala, oitavas, rugosidade, orgânico, névoa, curvas e vinheta).
  `ControlMaterial` distingue prismático, preenchido fosco e editorial. Campos de
  reflexo especular foram removidos; o coração tem contorno/preenchimento reais.
- Seed FNV-1a de 32 bits sobre hash opaco + versão do motor. Identidade não seleciona
  estilos/cores; testes com hashes diferentes verificam igualdade dos demais parâmetros.
  Ruído de valor interpolado e fractal multiescala, máscaras orgânicas, névoa, curvas,
  campos espaciais, feixes discretos e vinheta são gerados por código uma vez. Nenhum
  bitmap da capa entra no compositor; não há reconstrução semântica/figurativa.
- As quatro máscaras de diagnóstico e a composição opaca 192×342 são persistidas,
  com versão/política/hash da capa e SHA-256 dos PNGs. A UI exibe uma textura estática;
  geração e IO usam worker. Fallback imediato e descarte de seleções substituídas.
  Cache em memória limita assets completos a três; galeria decodifica thumbnails
  de até 128 px sem iniciar análise/geração de fundos.
- Capas verificadas no catálogo Apple Music/iTunes: edição, origem, copyright,
  checksum e disponibilidade em `labs/art-lab/cover-sources.json`; respostas oficiais
  das novas aquisições em `labs/art-lab/validation/sources/`. Uso local experimental,
  sem concessão de direitos de redistribuição. Script de aquisição reproduzível.
- Execução `--validate-procedural --cycle=N`: renderer Compose/Skia real offscreen,
  12 capas e oito fixtures, estados, comparações dos mestres, contato do conjunto,
  dois viewports e tempos por fase. `--cache-probe --cycle=N` inicia outro processo
  e exige hits em disco sem análise/geração e com receitas/pixels idênticos.
- A fixture “bright-photo-surrogate” é explicitamente **sintética**, testa luminância
  e textura, não reconhecimento de fotografia. Capas fotográficas reais também
  estão no benchmark. Métricas RGBA são estimativas calculadas de pixels retidos,
  excluem runtime/temporários; raster CPU offscreen não mede GPU, Redmi ou 60 FPS.
- Evidências e limites medidos continuam no registro `reference-measurements.md`.
  Aprovação estética final exclusivamente do usuário; sem integração Android/
  produção, sem computer-use, janela visível, commit, push ou merge.

## 21. Decisão 2026-10-18 — PLAYER_MICRO_INTERACTIONS_V1

Pedido explícito do usuário: glow da agulha, trail apenas durante drag e resposta
suave do aro/halo à energia do áudio. Esta decisão autoriza essas microanimações
locais; o fundo procedural permanece estático. Preserva o diretor/receitas/cache v4.2,
a geometria e todos os limites de produção das etapas anteriores.

- Métricas em dp na base lógica de 432 dp do renderer, escaladas com o viewport,
  como os demais controles. Glow sempre presente no modo normal: raio 6 dp/alpha .18;
  drag 10 dp/.34, escala da agulha até 1.08; tween smoothstep de 180 ms. Hover, foco
  e pressão usam intensidades intermediárias. Cor amostrada do gradiente fixo da barra.
- Trail: pool de 80 slots, oito partículas por emissão nominal de 36 ms, vida 220–280 ms,
  tamanho inicial e de desenho 2–6 dp (menor nas famílias discretas), alpha inicial
  .10–.18. Fade quadrático, sem emissão depois do release/cancelamento. Sem recuperar
  emissões perdidas em um frame atrasado. Faixa de desenho de ±8 dp da barra, protegendo
  os rótulos de tempo e o texto. Cores de cada emissão vêm do progresso naquela posição.
- PCM: porta comum `AudioEnergySource`, acumulador de RMS AC por canal em janelas de
  20 ms; estéreo em oposição de fase não se cancela. WAV local é decodificado/analisado
  em worker para um envelope leve, com referência global p95 e noise gate .003. Nenhuma
  análise de áudio por frame. WAV até 256 MiB/30 min, 1–8 canais, 8–192 kHz, conforme o
  suporte do Java Sound. Erro de entrada é mostrado no diagnóstico.
- Envelope visual: attack τ=180 ms, release τ=360 ms; coeficientes exponenciais baseados
  no delta real, com passo visual limitado a 48 ms contra saltos após stalls. Aro existente
  até +1.5 dp; halo até +6 dp/+ .12 alpha; escala somente do corpo até 1.02. Cor e material
  ficam estáveis. Materiais foscos não ganham aro decorativo; respondem pelo halo e escala.
- Ganhos combinados pelos pesos das famílias da receita, sem nomes/IDs: prismático 1.0,
  denso .70, editorial .35, dramático .70 com halo concentrado, luminoso .60, minimalista
  .20 e fallback .25. Não há regra por álbum nem modificação da receita a cada batida.
- Sem fonte de áudio, o play fica em repouso. Lab oferece WAV e fixture PCM de teste
  identificada. A prévia é **silenciosa**, usa relógio visual (play/pausa/seek) e não
  representa o áudio dos álbuns da galeria. Não integra Media3, reprodução ou captura
  de áudio do sistema. A validação das seis capas usa a mesma fixture PCM medida.
- Frame loop apenas enquanto necessário; alterações observadas em draw/layer, sem
  recompor layout por energia. Sem redraw em energia estável/silêncio. Redução de
  movimento mantém feedback de foco/pressão, desativa partículas, pulso e escala.
  Janela minimizada, controles ocultos ou microinterações desligadas suspendem efeitos.
  A gravação do player em GraphicsLayer agora ocorre somente para captura manual.
- Validação em Pink Floyd, Megadeth, Nirvana, Thriller, Abbey Road e Daft Punk:
  `--validate-micro`, Compose/Skia headless real com eventos locais de pointer/foco,
  snapshots, filmstrips e GIFs. Medições alternam três rodadas de 20 frames/modo,
  comparando idle e seek equivalentes com efeitos ligados/desligados. GIFs mostram
  recortes da barra e transporte e têm paleta limitada; PNGs completos são a evidência
  de cor/layout. Partículas usam dois discos antialiasados tênues, evitando um novo
  gradiente nativo por partícula/frame; halos continuam com gradiente radial suave.
- Dados medidos e limitações no registro `reference-measurements.md`. Sem IA, shader
  customizado, fundo animado, interface nativa automatizada, integração de produção,
  commit, push ou merge. Aprovação estética permanece exclusivamente do usuário.

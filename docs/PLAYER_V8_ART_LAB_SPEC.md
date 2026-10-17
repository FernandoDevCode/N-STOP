# NØNSTOP — PLAYER V8 ART LAB — ESPECIFICAÇÃO NORMATIVA
**Versão:** 1.0 · **Data:** 2026-10-17 · **Branch:** `feat/nonstop-v8-art-lab` · **Estado:** aprovada para construção do laboratório; NÃO aprova integração ao app principal.

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
- **PASS funcional**: testes automatizados e execução sem regressão. **PASS visual**: somente aceite do usuário após comparar as duas referências e 10 capas adicionais.
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
| PREMIUM_FALLBACK | evidência inconclusiva | canvas escuro com duas luzes sutis, arte desfocada, botão refinado |
Os arquétipos **não** devem virar um `when` rígido exclusivo: o diretor pode misturar no máximo dois tratamentos compatíveis, com pesos registrados. Exigir limites de saturação, luminância e legibilidade no resultado. Se houver conflito entre IA e features, usar estratégia conservadora e mostrar divergência no painel diagnóstico.

## 7. Receita tipada: contrato mínimo
Representar em data classes Kotlin serializáveis, sem strings mágicas:
- `schemaVersion`, `sourceImageHash`, `modelVersion`, `confidence`, `archetypeScores`
- `canvasColor`, `scrimTop`, `scrimBottom`, `artTextureOpacity`, `artBlurRadiusDp`
- `lights[]` com `x`, `y`, `radiusX`, `radiusY`, `color`, `intensity`, `falloff` e `motionAmplitude` (posições relativas)
- `controlSurface`: fill base/gradient stops, specular highlight, outer halo color/intensity/radius, ink color
- `progress`: fill/track, até N paradas cromáticas com localização espacial fixa [0,1], thumb/halo
- `lyrics`: foreground active/future, alpha progression e surface treatment (mesmo sem letras reais no laboratório)
- `analysisMetadata`: features resumidas, origem das decisões, fallback reason
Saneamento **obrigatório** de todos os valores, checagem de contraste/NaN, limites de luz/alpha; rejeitar receita inválida com fallback estável. JSON deve ser salvável/recarregável e versionado.

## 8. Renderização: fidelidade antes de técnica
- **Objetivo:** emulação perceptiva fiel das referências, não cópia literal dos shaders imaginados no mockup.
- **Proibido como padrão:** efeitos custosos refeitos a cada frame, blur de bitmap integral contínuo, IA na thread UI, backgrounds de cor chapada, glow retangular ou bounds visíveis, bordas cinza artificiais, rainbow decorativo sem suporte na capa.
- Compositor inicial **sem shader customizado obrigatório**: textura reduzida/fortemente desfocada gerada uma vez, camadas Compose com gradientes radiais/elípticos, scrims, imagens em cache, gradientes e efeitos estáticos/movimentação lenta dos offsets.
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
- Capa quadrada com `ContentScale.Crop` se necessário sem distorcer; manter composição e alinhamento.
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
G. Comparação com 12 capas — registrar configuração, receita e imagens capturadas do aplicativo, nunca substituir por PNGs gerados separadamente.

### Aceite visual exclusivamente humano
- **Referências Pink Floyd e Megadeth:** buscar correspondência 1:1 em geometria/composição/aparência do mockup, usando imagens originais e comparações side-by-side na mesma escala. Não aceitar apenas “ficou mais bonito”; não autorizar modificações estéticas deliberadas para “melhorar”.
- **Outras 10:** cada resultado deve ser específico da capa, não uma mesma atmosfera recolorida; o usuário decide se o acabamento é compatível com as duas referências. Números de score não substituem aprovação.
- **PASS total** somente depois do usuário aprovar as duas referências e o lote geral. Se qualquer referência falhar, status **NOT PASSED**, com diferenças registradas, sem afirmar sucesso.

## 14. Plano de execução e checkpoints do Codex
**Fase 0: auditoria (sem UI).** Confirmar branch, status/diff, referências mestras legíveis, JVM/JDK/Gradle e dependências Compose. Reportar riscos de compartilhamento Desktop/Android e licença do modelo. Não tocar em produção.
**Fase 1A: esqueleto Desktop.** Criar módulo/lab isolado, galeria, player estático Compose com dimensões mensuradas e alternância de capa. Tirar screenshots reais. Não mascarar ausência de IA.
**Fase 1B: motor clássico + recipes.** Pipeline de features, arquétipos, cache, fallback, componentes visuais e painel diagnóstico. Render em tempo real; comparar 12 capas.
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

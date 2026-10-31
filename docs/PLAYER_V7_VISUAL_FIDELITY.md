# NØNSTOP — PLAYER V7 · VISUAL FIDELITY
Status: especificação de refinamento visual, **não aprovação do APK**.
Destino: `feat/nonstop-checkpoint-2` · Android/Jetpack Compose · alvo principal Redmi Note 14.
Atualizado: 2026-10-17.

## 0. Fonte de verdade e propósito
Este documento descreve a aparência **percebida** e os microdetalhes que o player v7 precisa reproduzir. Complementa `docs/player-v7-visual-spec.md`, mas **prevalece em conflitos visuais** sobre os trechos antigos daquele documento, sobre `NONSTOP_CHECKPOINT_2_REFINAMENTO_VISUAL.md` e sobre descrições genéricas.

**As imagens aprovadas continuam sendo a referência geométrica principal**, quando presentes localmente. Não criar dimensões supostamente medidas com precisão a partir das imagens. Tratar valores em dp abaixo como pontos de partida a verificar em screenshots reais, não como verdades absolutas.

Referências aprovadas:
- **Megadeth, Rust in Peace — “Holy Wars... The Punishment Due”**: capa azul, atmosfera noturna em azul profundo e luz clara; controles luminosos de acabamento suave. A imagem de referência aprovada originalmente tem mais profundidade/contraste que o APK real.
- **Pink Floyd, The Dark Side of the Moon — “Time” e demais faixas do álbum**: capa majoritariamente preta, prisma claro e feixe multicolorido pequeno mas altamente saliente; base preta, acentos seletivos e luz controlada. Não pintar o player de vermelho/rosa chapado.
- **Player v7/LETRAS**: composição limpa com arte grande; painel LETRAS na base, sem texto cantável fixo; tela expandida protagonizada pelas letras com cabeçalho compacto.

Capturas reais do Redmi (17/10/2026) identificaram: fundo Megadeth ainda liso/difuso demais; espaço vertical maior que o necessário no player; cabeçalho compacto de letras corta início de títulos longos; linhas de letras apresentam contraste/desfoque e divisões de texto pouco refinados; em Pink Floyd a progressão multicolorida fica comprimida no início da barra. A versão revisada já melhorou o botão e a paleta: **preservar os acertos**, não reescrever tudo.

**Checkpoint 1 é intocável neste refinamento:** ícone, Home, navegação roxa fixa, mini-player, atmosfera do mini-player, seek 48dp e a geometria integrada à base. O player expandido NÃO deve importar glow roxo fixo da navegação.

## 1. Hierarquia visual do player principal
Ordem **fixa**, de cima para baixo:
1. Status bar real, sem invasão de áreas do sistema.
2. Cabeçalho discreto com seta/chevron de fechar à esquerda e `⋮` à direita; ações secundárias no menu, não dispersas. Áreas tocáveis ~48dp mesmo com ícones menores.
3. **Capa quadrada protagonista**: centralizada, com recuos laterais proporcionais, cantos modestamente arredondados; sem moldura cinza evidente, sem deformar arte.
4. **Progresso imediatamente sob a capa**: trilho fino, bolinha de progresso perceptível e timestamps abaixo, alinhados às extremidades úteis do trilho.
5. **Informação editorial**: título à esquerda, até 2 linhas com quebra natural; artista · álbum (quando disponíveis) em linha secundária discreta. Coração **único** na direita sem círculo.
6. **Controles**: shuffle, anterior, play/pause central, próximo, repeat; controle principal com presença maior sem aumentar os secundários.
7. **Handle LETRAS**: pequena alça com chevron para cima e texto espaçado; integrada ao fim da superfície, sem grande bloco opaco e sem cards extras de letra no player principal.

Evitar espaços vazios acidentais entre informações, controles e handle. Distribuir espaço segundo altura disponível: margens decorativas encolhem antes da capa ou controles, mas nunca sobrepor insets. Em telas altas, manter ritmo editorial equilibrado; não usar `Arrangement.SpaceBetween` indiscriminadamente causando lacunas enormes.

## 2. Geometria e tipografia
- Fundo de tela ocupa a região útil continuamente. Capa alinhada com o progresso e a informação; alinhamento óptico dos elementos importa mais que igualdade matemática das margens.
- Título grande, branco quase neutro, peso médio/semibold sem parecer bold excessivo. Títulos longos como **“Holy Wars... The Punishment Due”**: até duas linhas no player principal; nunca cortar início. Não confundir o título truncado fornecido por metadados com clipping imposto pelo layout.
- Artista/álbum: contraste secundário mas legível, separador `·` discreto; elipse apenas onde falta espaço de verdade.
- Coração solto visualmente, mas alvo de toque acessível. Não competir com título; não acrescentar segundo coração no cabeçalho.
- Progresso com hitbox maior que a linha visível, sem interceptar botão de coração ou gestos verticais da letra. Texto de tempos com contraste adequado.
- Evitar bordas em volta de botões do topo desnecessariamente pronunciadas; halos apenas onde a referência os mostra.

## 3. Motor cromático: sem hacks por álbum
O sistema já possui extração local/cache e modos **unificado**, **seletivo** e **cinematográfico**. Evoluir o que existe, não criar motor paralelo nem exceções por nome de música/artista/álbum.

Analisar com custo baixo:
- Fração de pixels escuros e claros, luminância, distribuição/posição de regiões saturadas.
- Contraste local e saliência: pequeno elemento vívido pode merecer papel de acento **sem** determinar cor da base.
- Preferir separação de papéis: `canvas/base` (escuro e estável), `ambient` (luz difusa), `progress` (contraste e informação), `play fill` (material/volume), `play halo` (luz controlada), `ink` (legibilidade).
- Confiabilidade insuficiente -> fallback neutro; não promover um pixel extremo de saturação para dominar tudo.
- Não inferir conteúdo semântico de objetos apenas pelas cores. Não prometer identificação automática de "prisma"/"lua"; detectar propriedades visuais generalizáveis.

**Pink Floyd**: preto profundo é a base. Prisma cinza claro + feixe arco-íris são características de contraste pequeno e relevante. Preservar preto; permitir detalhe multicolorido apenas em controles/progresso quando evidência for sólida. No progresso, não comprimir *todo o espectro cromático* nos primeiros poucos pixels: o gradiente precisa manter uma distribuição percebida, respeitando região preenchida/track e avanço real. Evitar arco-íris usado como background full-screen.

**Megadeth**: base azul-noturna, detalhes frios azul/violeta da arte, possíveis reflexos bem leves. Sem azul opaco chapado. O fundo não deve reproduzir figuras da capa nem virar outro quadro nítido duplicado atrás.

**Capa vermelha/verde**: fundo escurecido próximo a vinho/petróleo, sem tons neon contaminando textos. **Capa pouco colorida**: neutralidade elegante, sem roxo obrigatório. **Arte indisponível**: fallback neutro imediato e estável.

## 4. Atmosfera, profundidade e compositing
O acabamento pretendido não é "gradiente sobre tela". É **ambiente iluminado indiretamente pela arte**: duas ou poucas regiões luminosas difusas, assimétricas, com intensidade distinta, muito suaves atrás da capa e controles; centro de leitura mais escuro, contraste editorial preservado.

- Preferir arte real fortemente desfocada/atenuada + luzes cromáticas de baixa opacidade **com scrim escuro**. Não tornar a arte reconhecível em segundo plano; o observador deve sentir sua presença, não identificá-la.
- Luz não deve ter retângulos visíveis, bandas, bordas abruptas, `tile`/buffer quadrado, gradiente cortado ou "bug do After Effects". Inspecionar `blur`, `clipToBounds`, `drawWithCache`, `graphicsLayer` e alpha/composição; não usar caixas semitransparentes que revelem bounds.
- Atmosfera não invade status/navigation bars com defeitos de clipping; respeitar insets do Android.
- Transição de capa muda luz/paleta suavemente e sem flashes. Nada de cálculos caros de pixels ou blur bitmap por frame; cache e dispatcher fora da UI.
- Não multiplicar glows em todos os elementos. Controle central pode ser o foco, mas ambiente precisa sustentá-lo.
- Reduzir movimento -> estático; tela oculta -> suspender animações.

## 5. Play/pause — especificação de camadas e microdetalhes
O círculo central não pode parecer só um `CircleShape` preenchido uniformemente.
- **Silhueta**: círculo perfeito, sem stroke grosso permanente; tamanho visual predominante, área tocável real >=48dp.
- **Material interno**: preenchimento cromático com mudança sutil entre claro/escuro e leve reflexo, respeitando contraste do ícone. Luz interna suave, sem brilho branco lavado.
- **Glow externo discreto**: halo difuso suave que desaparece progressivamente no ambiente, **sem quadrado de alpha, sem clipping abrupto**. Não confundir com efeito de toque. Se glow real não renderizar limpo, reduzir/remover em vez de fabricar sombra retangular.
- **Paleta**: para Megadeth, preenchimento azul-claro frio com halo azulado fraco; para capas noturnas de luz branca, preenchimento branco frio com luz ambiente azul; para Pink Floyd, pode ficar quase neutro ou usar aro multicolorido discreto se estiver harmonioso, **sem inundar o player com arco-íris**.
- **Ícone**: play/pause opticamente centrado; tamanho e peso compatíveis, escuro sobre preenchimento claro ou branco sobre botão escuro. Estado deve derivar do playback real.
- **Toque**: feedback funcional imediato; nunca ripple quadrado, flare piscando, escala chamativa ou brilho que pareça bug.
- Não usar glow só porque a especificação diz "glow": ver o resultado renderizado e calibrar intensidade.

## 6. Progresso do player expandido
- Fino, legível e alinhado sob capa; bolinha discreta mas tocável por hitbox generosa. Timestamps embaixo, sem colidir com título.
- A barra representa posição temporal real, sem reiniciar paleta/ângulo a cada tick.
- Em modo seletivo, usar no máximo acentos visuais sustentados pela capa. Se houver faixa espectral real (prisma), conferir no screenshot que o arco-íris não aparece como um filete comprimido quando playback está no começo. Diferenciar cor do preenchimento e desenho do track ainda não percorrido.
- Não alterar a lógica de seek e bloqueios de mídia compartilhada.
- Verificar cor e posição com a música em 0%, 25%, 50%, 100% (sem confundir mudança de posição com mudança de paleta).

## 7. Letras expandidas: leitura protagonista
- Topo usa miniatura quadrada compacta à esquerda + bloco de título/artista + coração/ações, sem truncar **início** do título mesmo em músicas longas. Usar peso, ellipsis final, largura limitada e prioridades de espaço claras.
- Progresso e transporte compactos imediatamente abaixo do cabeçalho; linha de letra atual deve ocupar a principal área visual.
- Linha atual: nítida, contraste claro, sem tremor/blur. Próxima linha: legível com atenuação de opacidade, **não borrada**. Duas próximas linhas devem ser identificáveis em leitura rápida; linhas distantes podem sumir progressivamente, com blur máximo baixo e scrim/fade.
- Não aplicar blur como máscara retangular por linha ou em sílabas TTML. Evitar o recorte do bounding box.
- Preservar espaçamento natural e quebras: textos como "Do you kill on God's command?" não devem ganhar espaços artificiais entre sílabas/palavras ou letras visualmente espalhadas. Verificar layout e renderer TTML/LRC, respeitando que os dados originais podem conter divisões ruins.
- Linhas atuais e futuras têm baseline/entrelinhas coerentes; letras não ficam amontoadas nem com enormes espaços. Não desfocar texto por completo a ponto de perder antecipação.
- Rolagem automática, relógio sincronizado, offset/seek onde disponível, linhas instrumentais, letras simples, ausências e preferências existentes intactos. Nunca inventar letras nem usar texto mockado em produção.
- Gesto para fechar letras e retorno ao player, sem interceptar scroll normal da letra. Controles acessíveis.

## 8. Alça LETRAS do player principal
- Não transformar num bloco preto independente. Continuidade atmosférica com o player; separação delicada por luminosidade, não por retângulo/fundo chapado.
- Chevron claro e palavra `LETRAS` com tracking editorial discreto, área tocável confortável.
- Sem preview de duas linhas permanente (referência final substituiu essa ideia mais antiga).
- Estado sem letras não deve fingir conteúdo. Atentar a conteúdos longos/altura reduzida.

## 9. Verificação visual OBRIGATÓRIA quando houver ambiente Android
O processo **não** termina em `BUILD SUCCESSFUL`. Após editar:
1. Rodar o app no **Redmi Note 14 por ADB** ou emulador Android operacional; capturar screenshots reais de *Holy Wars*, *Time* e letras com título comprido. Se música real não estiver facilmente acessível, pedir ajuda mínima ao usuário para chegar ao estado; não criar conteúdo falso permanente.
2. Abrir/examinar as capturas resultantes e comparar lado a lado às referências aprovadas em tela/proporção equivalente, avaliando **posição/tamanho, cores, luz, blur, alinhamento e estados**.
3. Fazer **uma ou duas iterações focadas** nos maiores desvios visuais (não refazer tudo a cada tentativa).
4. Registrar o que foi de fato visto versus o que apenas passou em teste. Se não houver device/emulador acessível ou impossibilidade de analisar PNG, **declarar bloqueio explícito**, não alegar fidelidade comprovada.
5. Rodar `git diff --check`, build debug e, onde relevante, testes unitários direcionados. Usuário faz aceite visual no Redmi.

Sugestão de capturas: (a) player Megadeth; (b) player Pink Floyd no início e após ~metade; (c) letras Megadeth com título comprido; (d) letras Pink Floyd; (e) player com capa monocromática/verde/vermelha se disponível.

## 10. Riscos visuais específicos (checar com moderação)
- Fundo/halo com bounds retangulares, bordas cinza indevidas e clipping de blur.
- Ícone play/pause escapando da área circular; animação de toque com ripple quadrado.
- Capas deformadas, título ou artista cortados, menus sob status bar.
- Timestamps fora da área, scrub ruim, gestos sobrepostos.
- Blur de sílabas TTML renderizando caixas; textos muito claros/escuros.
- Tratamento multicolorido ficando chamativo no fundo.
Inspecionar apenas os riscos ligados aos arquivos alterados; evitar "auditoria paranoica".

## 11. Escopo e saída exigida do Codex
Não alterar Checkpoint 1, infraestrutura de áudio/Media3, fila, Cast, Listen Together, download, banco, login, sincronismo ou coleta de letras para produzir aparência. Não migrar backend nem exigir suite extensa de testes.
Antes da implementação, mapear os arquivos de `PlayerV7.kt`, `NowPlayingScreen.kt`, `PlayerArtDirection.kt`, `GlowingProgressBar.kt`, `LyricsMode.kt`, `LyricsModeModel.kt`, `LyricsView.kt` e `LyricsSyllableRenderer.kt`, somente conforme necessidade.
Informar: diff e alterações concretas, testes executados, APK, limitações, screenshots analisados e desvios ainda não resolvidos. **Sem commit/push/merge automático** pelo Codex; documentos adicionados diretamente pelo chat ao GitHub podem ter commit próprio.

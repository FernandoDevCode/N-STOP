# SONORA — mini-player / Atrito Cinético

## Escopo e causa do conflito

A implementação anterior media 52 dp e interceptava os 48 dp inferiores no
`PointerEventPass.Initial` do ancestral. Assim, quase toda a capa e os textos
estavam dentro da área de seek; somente os botões tinham uma exclusão calculada
por coordenadas globais.

O mini-player agora usa uma `Column` com duas regiões irmãs, sem sobreposição:

- Transporte de 52 dp: superfície clicável para expandir, capa/textos com o
  swipe existente e botões independentes de 48 dp.
- Progresso de 24 dp: linha curva, agulha, espaço para a emissão e gesto de seek.
  Esse espaço é uma alocação real do layout. Não é um alvo invisível sobre a capa.
  O down precisa estar dentro dos limites medidos dessa região. A expansão de
  alvo mínimo do Compose não pode aceitar eventos que começam acima dela.
  O mesmo ponteiro permanece dono do gesto quando sai da região; cancelamento
  e segundo dedo não confirmam seek.

O controle de progresso mantém `ProgressBarRangeInfo`, `SetProgress`, descrição
e estado desabilitado. O intervalo não é descendente da superfície de expansão.
Não há interceptação global em Initial, recortes de hitbox calculados por posição
dos botões nem overlays de toque.

A margem inferior passou de 6 para 10 dp. A altura total alocada é 86 dp
(52 + 24 + 10). O Scaffold existente mede essa altura e publica o inset inferior
para o conteúdo; nenhuma tela ou navegação foi editada. A experiência de rolagem
e os insets reais ainda precisam ser conferidos no aparelho.

## Geometria e efeito

`MiniPlayerRail` define duas curvas de 90 graus e uma tangente inferior. A agulha
usa distância ao longo desse percurso; o seek projeta o toque na mesma geometria,
incluindo x e y nas curvas. As extremidades retornam exatamente 0 e 1. A posição
é a do fluxo real do ViewModel, exceto pela prévia local durante seek e sua espera
de confirmação, preservada da implementação anterior. Não há avanço fictício.

Agulha branca/prateada, fragmentos quentes com pequenos reflexos, rastro curvo
direcional e decaimento após a soltura. A emissão normal de 64 partículas/s não
depende da velocidade do relógio musical. O toque aumenta a emissão; a velocidade
do gesto aumenta dispersão, comprimento e energia. A velocidade visual decai
quando o dedo para. Buffering reduz a emissão-base para 11/s. Pausa interrompe
a emissão e deixa os fragmentos restantes desaparecerem em até 430 ms.

Há no máximo 96 partículas em arrays reutilizados; paths e strokes são cacheados.
Não há PCM, biblioteca de partículas, shader radial criado por frame ou estado
de animação lido pela Home. O relógio de frames é lido na fase de desenho.
Visibilidade, lifecycle RESUMED e a opção Android de remover animações governam
o loop. A opção é observada em tempo real e produz uma representação estática.

## Arquivos desta tarefa

- `feature/nowplaying/src/main/kotlin/com/stash/feature/nowplaying/MiniPlayer.kt`
- `feature/nowplaying/src/main/kotlin/com/stash/feature/nowplaying/MiniPlayerProgress.kt`
- `feature/nowplaying/src/main/kotlin/com/stash/feature/nowplaying/MiniPlayerRail.kt` (novo)
- `feature/nowplaying/src/main/kotlin/com/stash/feature/nowplaying/MiniPlayerFriction.kt` (novo)
- `feature/nowplaying/build.gradle.kts` (harness Compose/Robolectric)
- Cinco testes novos no mesmo package de testes: `MiniPlayerInteractionTest`,
  `MiniPlayerRailTest`, `MiniPlayerFrictionTest`, `MiniPlayerMotionTest`,
  `MiniPlayerRenderTest`.
- Este relatório e suas evidências.

As alterações preexistentes em Home e os arquivos preexistentes não rastreados
foram preservados. Não houve commit, push ou merge. Player V8, serviços,
reprodução, downloads, banco, autenticação e navegação não foram editados.

## Validação reproduzível

Resultado final em 28/10/2026: **14 testes aprovados, zero falhas; APK principal
Debug compilado com sucesso**. `git diff --check` sem erros nos arquivos da tarefa.

- APK: `app/build/outputs/apk/debug/app-debug.apk`, 185.686.835 bytes.
- Application ID: `com.stash.app.debug`; versão 0.9.114 (150).
- SHA-256: `41f16fa6f83991c068295caa5a5f3e27d79a98751e380ab11d666b2d33a73d45`.
- As classes novas `MiniFrictionParticles`, `MiniPlayerRail` e
  `MiniPlayerFrictionKt` foram confirmadas em `classes15.dex` dentro do APK.
- Relatório JUnit: `feature/nowplaying/build/reports/tests/testDebugUnitTest/index.html`.
- Log: `build/mini-player-validation.log`.
- [Reprodução — prévia animada offscreen](evidence/reproducao-robolectric.gif)
- [Gestos lento/rápido e soltura — prévia animada offscreen](evidence/gestos-robolectric.gif)
- Capturas: [reprodução](evidence/reproducao-robolectric.png),
  [arrasto rápido](evidence/arrasto-rapido-robolectric.png),
  [pausado, inclusive com buffering reportado](evidence/pausado-robolectric.png).

As prévias são montadas a partir dos frames PNG produzidos pelo teste de render.
O teste de relógio também cobre pausa com frames de desenho perdidos: ao acabar
a dissipação, os fragmentos são explicitamente limpos para não congelarem na tela.

```powershell
$env:ANDROID_HOME = (Resolve-Path 'labs/app-desktop/build/android-sdk').Path
.\gradlew.bat :feature:nowplaying:testDebugUnitTest --tests '*MiniPlayer*Test' :app:assembleDebug --max-workers=2 '-Pkotlin.incremental=false' --console=plain
```

O SDK salvo em `local.properties` não existe nesta máquina. Foi usado o SDK já
disponível no workspace por variável de ambiente, sem editar a configuração local.

Os testes cobrem o componente de produção com ViewModels simulados somente no
harness: capa, título, artista, espaço livre, limite superior da faixa, botões,
arrasto para fora da faixa, gesto iniciado acima, ambos os extremos curvos,
acessibilidade, cancelamento, multitoque, seek desabilitado e ausência de faixa.
Também cobrem round-trip geométrico em três densidades/larguras, emissão sem
movimento do progresso, diferenças entre gesto lento/rápido, pausa, buffering,
dissipação, limite de partículas, direção, redução de movimento e visibilidade.

As imagens e sequências `robolectric` são renderizações Android offscreen via
Canvas do componente real, com dados de teste. O teste mantém a posição musical
parada para demonstrar que a emissão não depende do progresso avançar. Elas
NÃO são screenshots do Redmi, gravação de tela, reprodução musical real nem
medições de desempenho. A captura de janela via PixelCopy não funcionou nesse
harness; a renderização foi feita diretamente no Canvas Android nativo.

## Limites de aceitação

ADB consultado: nenhum dispositivo conectado. O SDK disponível não tem emulador.
Ainda é necessário no Redmi Note 14:

1. Repetir capa/texto/espaço livre, curtir/play e seek com os dedos, incluindo curvas.
2. Gravar reprodução real, arrasto lento/rápido e soltura, sem zoom; verificar se
   a assinatura tem a presença pedida na composição completa da tela.
3. Conferir rolagem, margem de 10 dp, navegação fixa e áreas seguras.
4. Medir fluidez, bateria e resposta ao seek; testar TalkBack e remover animações.

O acabamento foi inspecionado em render de teste. A aprovação artística e o
desempenho em aparelho permanecem pendentes; não há alegação de sucesso visual
definitivo baseada somente nas imagens ou na prévia offscreen.

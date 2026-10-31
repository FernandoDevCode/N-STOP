# Mini-player compacto e Atrito Cinético — 28/10/2026

## Proporção e interação

A versão anterior somava uma linha de conteúdo de 52 dp a uma faixa independente
de 24 dp. Essa faixa aumentava o card para 76 dp e afastava o trilho das informações.
O progresso agora acompanha o mesmo card do conteúdo, sem acrescentar uma linha
ao layout. A altura mínima é 56 dp (48 dp de controles + 4 dp acima e abaixo), com
crescimento permitido para texto ampliado. O afastamento inferior continua 10 dp.

O dono do seek é escolhido exclusivamente no down, pela distância ao Path: até
7 dp, abaixo do começo dos cantos, dentro do card e fora dos retângulos medidos
dos botões. Um down do corpo não pode adquirir seek depois. A arbitragem no passe
Initial consome somente os gestos que atendem a esses critérios; os demais ficam
para abertura, swipe existente e controles. Não há interceptação de uma faixa
retangular de 48 dp como na implementação original.

O nó de progresso sobreposto serve apenas à acessibilidade (range e SetProgress),
sem handler de ponteiro ou altura acrescentada. O renderer também não tem handler.
Capa, título, artista e corpo continuam abrindo o player. Os dois controles
mantêm alvos de 48 dp e prioridade sobre a proximidade do trilho.

## Física e desenho

O conjunto fixo de 96 partículas foi preservado. A emissão agora tem três famílias:
fragmentos que sobem, fragmentos que descem e lascas quase tangenciais. Cerca de
82% da emissão tangencial segue para trás do movimento; as demais partículas
rebatem para frente. As velocidades são transformadas pela tangente e normal
locais do Path, inclusive nas curvas.

Cada fragmento recebe comprimento próprio de rastro. A velocidade do gesto amplia
energia, emissão e persistência visual. Pequenas variações temporais suaves
modulam a emissão automática. O branco prateado predomina, com lavanda secundária.
Pausa permite dissipação e mantém a agulha; buffering reduz a base; redução de
movimento e invisibilidade desligam o relógio, como antes.

O desenho ocorre em um modifier externo ao clip do card. Apenas trilho e rastro
de superfície usam o recorte arredondado. As partículas podem alcançar até 8 dp
abaixo da borda, dissipando nos últimos 3 dp dessa região, dentro dos 10 dp já
existentes. O limite superior protege os metadados e a exclusão dos retângulos dos
controles protege os botões. Nas pontas há alcance lateral/vertical adicional
restrito às margens externas do conteúdo para não apagar a emissão nas curvas.

O Path aprovado foi preservado: raio completo compartilhado, x=0 até x=largura,
sem recuo horizontal. Fundo e preenchimento usam o mesmo Path, agulha usa
PathMeasure.getPosition/getTangent e seek projeta para seu percurso circular.
As posições 0%, 1%, 25%, 50%, 75%, 99% e 100% estão incluídas na validação.

## Evidências e limites

As evidências em `evidence/polish/` são renders Android nativos do Robolectric,
com dados de teste. As animações são sequências desses frames, não gravações do
Redmi. Há teste de pixels luminosos abaixo do card durante reprodução normal,
além dos testes de distribuição física acima/abaixo e direção predominante.

Foram exercitados os testes de gestos, extremos, semântica, cancelamento, dois
dedos, estados, ciclo do relógio, geometria, emissão e composição compacta.
O renderer também produz layouts em 280/320/412 dp, densidades 1/2,75/3 e fonte
ampliada em 1,5. A largura medida é conferida antes de salvar cada captura.

ADB não encontrou dispositivo conectado. Permanecem pendentes: conforto real
dos gestos no Redmi, gravação sem zoom na composição completa do aplicativo,
aprovação da naturalidade artística, TalkBack em aparelho, insets e medições de
fluidez/bateria. Compilação e partículas visíveis não encerram essa aceitação.

## Arquivos e reprodução

Nesta rodada foram editados `MiniPlayer.kt`, `MiniPlayerProgress.kt` e
`MiniPlayerFriction.kt`; a geometria preexistente em `MiniPlayerRail.kt` foi
preservada. Os testes alterados são `MiniPlayerInteractionTest`,
`MiniPlayerFrictionTest`, `MiniPlayerRailTest` e `MiniPlayerRenderTest`.
Fontes/testes ficam no package `com.stash.feature.nowplaying` do módulo
`feature/nowplaying`. Os arquivos locais preexistentes foram preservados.

```powershell
$env:ANDROID_HOME = (Resolve-Path 'labs/app-desktop/build/android-sdk').Path
.\gradlew.bat :feature:nowplaying:testDebugUnitTest --tests '*MiniPlayer*Test' :app:assembleDebug --max-workers=2 '-Pkotlin.incremental=false' --console=plain
```

Relatório JUnit: `feature/nowplaying/build/reports/tests/testDebugUnitTest/index.html`.
Log: `build/mini-player-polish-final.log`.
APK: `C:/Users/User/Documents/GitHub/Spotify/Stash/app/build/outputs/apk/debug/app-debug.apk`.

Sem edição das outras telas, navegação, V7/V8, reprodução ou serviços.
Sem commit, push ou merge.

Resultado final: **19 testes aprovados, 0 falhas; assembleDebug conclu�do**.
SHA-256 do APK: `1216f6af0a7d6f4ac95b2d4b148ae49e53b541efeebbb13d648ac59744d63318`.

[Reprodu��o animada](evidence/polish/reproducao-robolectric.gif) � [Gestos animados](evidence/polish/gestos-robolectric.gif) � [Sete posi��es do trilho](evidence/polish/geometria.png).

# Correção do contorno inferior do mini-player — 28/10/2026

O trilho passou a usar o raio completo de 13 dp do `RoundedCornerShape` do card,
sem subtrair um inset do raio. Seu centro começa em x=0, percorre o quarto de
círculo esquerdo, a tangente inferior e o quarto de círculo direito, terminando
em x=largura. Os centros das curvas coincidem com os cantos reais do card.

O mesmo Path é desenhado para a linha inativa e extraído por `PathMeasure` para o
preenchimento. A posição da agulha vem de `getPosition`; sua orientação e a
velocidade inicial das partículas usam `getTangent`. A emissão inclui a componente
ao longo da tangente, com o sinal do gesto, e a dispersão normal para dentro do
card. O seek mantém a projeção por distância no mesmo percurso circular, testada
contra as posições retornadas pelo Path nativo.

A linha é centrada no próprio contorno. O recorte já existente do card deixa
2,5 dp de espessura visível para dentro, em vez de criar uma margem vazia entre
o trilho e a borda. Não há recuo horizontal nas extremidades. A altura, os paddings,
a região independente de seek, os botões e a margem inferior de 10 dp não mudaram.

## Arquivos alterados nesta rodada

- `MiniPlayerRail.kt`: construção única do Path, raio completo e tangente analítica.
- `MiniPlayerFriction.kt`: Path compartilhado, agulha orientada e emissão tangente.
- `MiniPlayerProgress.kt`: projeção de seek com o raio completo.
- `MiniPlayerRailTest.kt`: amostras exigidas, extremidades e continuidade tangencial.
- `MiniPlayerFrictionTest.kt`: origem e rotação das velocidades nas curvas.
- `MiniPlayerRenderTest.kt`: comparação com o Path Android e cinco renders de progresso.
- Este relatório e `evidence/contour/`.

Os fontes ficam em `feature/nowplaying/src/main/kotlin/com/stash/feature/nowplaying/`
e os testes no package correspondente de `src/test/kotlin/`.

## Validação

**17 testes aprovados; zero falhas. Build principal Debug concluído.**
`git diff --check` sem erros.

Os testes de interação existentes continuam cobrindo abertura por capa, textos e
área livre, botões independentes, seek nos dois extremos, gesto iniciado acima da
faixa, cancelamento, multitoque e ações de acessibilidade. Os testes geométricos
incluem 0%, 1%, 50%, 99% e 100%, três densidades e três larguras.

- [Comparação visual das cinco posições](evidence/contour/amostras.png)
- [Prévia animada offscreen](evidence/contour/reproducao-robolectric.gif)
- PNGs individuais: `evidence/contour/contour-{000,001,050,099,100}.png`.
- Relatório: `feature/nowplaying/build/reports/tests/testDebugUnitTest/index.html`.
- Log: `build/mini-player-contour.log`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- SHA-256: `d76433b9c8f6f8af62492896a6ca013b15d34f0df70a63cb6d55b7a6f4710a66`.

```powershell
$env:ANDROID_HOME = (Resolve-Path 'labs/app-desktop/build/android-sdk').Path
.\gradlew.bat :feature:nowplaying:testDebugUnitTest --tests '*MiniPlayer*Test' :app:assembleDebug --max-workers=2 '-Pkotlin.incremental=false' --console=plain
```

As cinco imagens foram inspecionadas no renderizador Android nativo do Robolectric.
Elas usam dados de teste e não são capturas de aparelho. ADB não encontrou dispositivo
conectado; a conferência no Redmi e a gravação real continuam pendentes.

Nenhuma outra tela, Player V8, dimensão do mini-player ou navegação foi alterada.
Alterações preexistentes preservadas. Sem commit, push ou merge.

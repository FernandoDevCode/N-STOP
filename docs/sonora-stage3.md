# Sonora — Etapa 3 (28/10/2026)

## Superfícies

O mini-player utiliza `sonoraMiniGlass`, conectado ao `LocalChromeHazeState`
que o scaffold já fornece. O `StashNavHost` já é a fonte Haze. Não foi criada
outra fonte, captura de tela, polling ou animação de superfície.

Em Android 12/API 31 ou superior, Haze 1.6.10 aplica backdrop blur de 20 dp
ao conteúdo atrás da superfície. A tonalização usa o grafite existente
`#20202C` a 84%, com reflexo branco-frio de 5,5% e lavanda muito discreta.
Sem fonte Haze (previews/testes) ou quando o backend usa fallback, a base
tonalizada fica a 97%. Não há `Modifier.blur` aplicado ao mini-player.

O modificador fica depois do desenho de Atrito Cinético, mantendo seu desenho
externo fora do recorte. Capa, título, artista, controles, agulha e trilho
continuam sendo desenhados sem blur. Altura, margens e gestos não mudaram.

O fundo opaco do conjunto inferior foi removido. O fade tem uma faixa de
58 dp: começa transparente 36 dp acima da navegação e chega a opaco 22 dp
dentro dela, continuando até o grafite original `#0E0F14` na base.
O fundo acompanha os insets; controles e posições continuam independentes.
No player expandido o fade é desativado. Os demais usos de `VisualBottomBar`
mantêm o fundo anterior por padrão.

## Arquivos desta etapa

- `core/ui/.../components/SonoraChromeSurface.kt`: superfície e fade.
- `feature/nowplaying/.../MiniPlayer.kt`: troca somente da superfície.
- `app/.../navigation/StashScaffold.kt`: composição do fundo e insets.
- `core/ui/.../visual/VisualScreens.kt`: opção de delegar o fundo ao host.
- `feature/home/build.gradle.kts`: dependência de teste do mini-player.
- `feature/home/.../SonoraStage3SurfaceTest.kt`: evidências integradas.

As modificações locais prévias das Etapas 1/2 permanecem no workspace.
Sem commit, push ou merge.

## Evidências e limites

`feature/home/build/reports/sonora-stage3/` recebe capturas da Home e do
mini-player de produção, com dados determinísticos de teste. Os arquivos
`home-rolada` mostram rolagem real do composable; `claro`, `escuro` e
`colorido` usam placas de estresse atrás do conjunto inferior.

Tamanhos: 360×640 (16:9) e 393×873 (aproximação lógica de 1080×2400,
proporção do Redmi Note 14), ambos em fontes 100% e 150%. Insets de 24 dp
são simulados no teste; não representam uma medição de dispositivo físico.

Estas capturas são Android offscreen/Robolectric e exercitam o fallback
tonalizado. Não provam o resultado do RenderEffect/Haze em hardware.
Não houve teste em dispositivo físico nem emulador conectado, teste com
catálogo ao vivo/capas reais, medição de frame time ou inspeção GPU de
overdraw. Esses pontos permanecem pendentes de validação no dispositivo.
A implementação reutiliza Haze, sem nova dependência de produção, e o fade
usa dois desenhos de fundo com brushes em cache e nenhuma atividade ociosa.

APK principal: `app/build/outputs/apk/debug/app-debug.apk`.

## Resultado da execução

- `:feature:nowplaying:testDebugUnitTest --tests '*MiniPlayer*Test'`: 24 testes,
  zero falhas, incluindo interação, física, geometria e renderização.
- `:feature:home:testDebugUnitTest`: 113 testes, zero falhas/erros.
- `:app:assembleDebug`: sucesso após o ajuste final.
- `git diff --check`: sem erros.
- 20 capturas geradas; inspeção visual de claro/16:9, Home rolada/16:9,
  escuro/393×873 e colorido/393×873 com fonte 150%.
- APK: 184.259.932 bytes; SHA-256
  `46226A7C21BEBEC2D8773F38BB209B181DCBF1463C763C74DCC8EDE1E1D45FEF`.

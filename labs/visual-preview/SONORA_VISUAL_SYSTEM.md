# Sonora — padrão visual compartilhado

O ponto de partida é a Home aprovada, sem cabeçalho. Esta consolidação não altera
a marca existente nas outras telas nem integra o aplicativo Android principal.

## Vocabulário do código

`core/ui/visual/VisualTheme.kt` concentra os valores compartilhados em `VisualDesign`:

| Grupo | Regra efetiva |
|---|---|
| `Background`, `Surface`, `Ink`, `Muted`, `Border`, `Accent` | Papéis provenientes do esquema claro/escuro. Não escolher cores de texto a partir de capas. |
| `cardSurface`, `filterSurface`, `navigationSurface` | Gradientes atuais dos componentes, com alternativas claras e fundo AMOLED preto. |
| `Heading`, `Body`, `Caption`, `Brand`, `PageTitle`, `HeroTitle`, `HeroArtist`, `CardTitle`, `CardCaption`, `NavigationLabel` | Inter e a hierarquia existente. Tracking zero nos textos dos cards; os demais estilos preservam o tratamento anterior. |
| `Gutter`, `Space` | Margem de página 20dp; intervalo de rails 10dp; separação entre seções 24dp. Espaços locais sem repetição continuam no componente. |
| `Size`, `Radius` | Chrome 62dp, controles 40dp, ícone inferior 22dp; hero 304dp; card 110dp de largura e mínimo de 168dp; foto do card 82dp. Raios: card 8dp, hero 11dp, filtro 12dp e mini-player 13dp. |
| `Light` | Núcleo `#FCFAFF`; bloom amplo 7dp/0,95 e próximo 2dp/0,9; transição de 220ms. Energia Rádio 0,65 e Biblioteca 0,8. Os valores aprovados foram mantidos. |

Não criar um token para cada número. Centralizar valores que definem a identidade
ou são compartilhados; manter decisões particulares junto ao componente.

## Comportamento responsivo

- **Cabeçalho:** a Home continua sem ele. Nas outras telas, mede a marca e os
  controles; se não couberem, coloca os controles abaixo. Não reduz a fonte da marca.
- **Filtros:** mantêm as proporções como mínimos. Rola horizontalmente; um texto
  muito longo pode quebrar linha dentro da largura útil, sem reticências.
- **Hero:** a caixa de artwork permanece em **304dp**, com o carregamento, crop e
  scrim originais. O componente mede os textos e os botões. Quando não há altura
  suficiente, somente os metadados continuam num painel abaixo da foto. Os botões
  podem ocupar mais de uma linha. Título, artista e descrição têm até duas linhas,
  com reticências e texto completo na semântica.
- **Rails:** preservam largura e caixa de imagem. A altura do card é um mínimo,
  não uma restrição que possa cortar texto ampliado. Títulos/descrições têm até
  duas linhas; subtítulo em branco não reserva uma linha. Rails vazios não exibem
  cabeçalho nem ação. Muitos itens usam `LazyRow`, com IDs estáveis fornecidos pelo host.
- **Categorias:** preservam a caixa da imagem e o comportamento nativo do Compose:
  até duas linhas com reticências quando não houver espaço. O teste confere a altura
  realmente desenhada, pois `didOverflowHeight` também sinaliza reticências válidas;
  o texto completo permanece na semântica.
- **Seções:** se a ação deixar pouco espaço para o título, ela passa para uma
  linha própria. Nesse modo o título admite três linhas, mantendo a semântica completa.
- **Navegação:** ordem fixa Início, Explorar, Rádio, Buscar, Biblioteca. Apenas os
  rótulos que não cabem recebem ajuste proporcional de fonte; não se encolhe a tela.
- **Mini-player:** layout, artwork, transporte, progresso e altura de 52dp
  preservados. Textos longos usam uma linha com reticências; artista ausente é omitido.
  Sem faixa atual, o host passa `null` e todo o espaço do mini-player é liberado.
- **Insets:** pertencem ao host, uma única vez. O APK isolado aplica a união de
  `safeDrawing` e IME, cobrindo barras, recortes e teclado. O Desktop usa a área
  cliente da janela. A matriz simula insets sem desenhar barras ou teclados falsos.

## Iluminação

O ambiente usa coordenadas proporcionais ao tamanho da superfície, com dois campos
elípticos em carvão/lavanda. Brushes são preparados por `drawWithCache` e limitados
ao fundo; não se sobrepõem ao artwork. AMOLED omite a luz ambiental e mantém fundo preto.

O ícone ativo combina núcleo vetorial com dois pequenos layers desfocados. O bloom
fica contido na navegação para não invadir o mini-player. Não usar um retângulo
colorido como indicador. A animação é finita; não há loop de iluminação do fundo.
Android anterior à API 31 usa os strokes translúcidos de compatibilidade; precisa
de revisão visual própria. Não igualar essa aproximação ao blur nativo sem medir.

## Conteúdo e novas telas

Novas telas recebem `VisualScreenState` e `VisualActions`, mantendo serviços e
persistência no host. Envolver a apresentação com `VisualTheme` e `VisualBackground`;
reutilizar superfícies e estilos existentes. Não derivar a paleta de álbuns nesta etapa.
Definir explicitamente Loading/Empty/Error e passar IDs opacos e estáveis para ações.

`VisualScenarios.kt` está exclusivamente no laboratório: `Approved`, `Dynamic`,
`Many`, `NoPlayer`, `Loading`, `Empty`, `Error`. Dynamic reutiliza os recursos
existentes branco (`wall`), escuro (`library5`) e colorido (`library1`), além de `null`
para capa indisponível; inclui nomes longos e subtítulos vazios. Many contém 60 itens.
Esses cenários não instanciam `AppController`, não consultam contas nem gravam biblioteca.
`ReferenceArtwork` e os arquivos de imagem permanecem intocados. Para conteúdo real,
o host continua usando o carregador existente; `null` já recebe o placeholder atual.

## Verificar uma mudança

Na raiz do repositório:

```powershell
# Antes de mudar uma aparência aprovada (não sobrescreve baseline existente):
.\gradlew.bat -p labs/app-desktop run "--args=--capture-visual"
python labs/visual-preview/review_system.py --record-baseline
# Depois da mudança:
.\gradlew.bat -p labs/app-desktop desktopTest
.\gradlew.bat -p labs/app-desktop run "--args=--capture-visual"
.\gradlew.bat -p labs/app-desktop run "--args=--capture-system"
python labs/visual-preview/review_system.py
.\gradlew.bat -p labs/app-desktop run "--args=--visual-preview --visual-scenario=dynamic"
```

`--capture-visual` preserva o formato histórico de comparação com as referências:
as quatro telas são renderizadas em 390dp e densidade ajustada aos pixels de cada
imagem. A captura `small-home` foi corrigida para representar **280dp reais**.
Para validação responsiva, usar `--capture-system`, não deduzir dp pelo nome de um PNG.
Esse comando também atualiza as capturas históricas antes de executar a matriz.
Sem baseline ou hashes registrados, o relatório informa `null`, não aprovação.

`build/validation/sonora-system/matrix/matrix.json` registra somente capturas
efetivamente concluídas: dp, pixels, densidade, fonte, tema, página, cenário e insets.
`index.html` é a galeria; `summary.json` verifica dimensões, hashes dos recursos e
comparação com a baseline. PNGs originais ficam disponíveis; `overview.png` apenas
reduz capturas inteiras para facilitar a revisão. Erro de pixels não é aprovação estética.

A matriz cobre 280/320/360/390/412/480dp; fontes 100/125/150%; claro/escuro/AMOLED;
densidades 1/1,5/2/3 em combinações representativas; conteúdo dinâmico, muitos itens,
estados vazios/carregando/erro, ausência de player, todos os destinos selecionados,
altura reduzida, insets laterais e reserva de teclado. Não é um produto cartesiano
de todas essas dimensões: consultar o manifesto para a combinação exata executada.

## Android e pendências reais

```powershell
$env:ANDROID_HOME = (Resolve-Path labs/app-desktop/build/android-sdk).Path
.\gradlew.bat -p labs/visual-preview/android assembleDebug --max-workers=2
.\gradlew.bat :core:ui:compileDebugKotlin --max-workers=2
& "$env:ANDROID_HOME/platform-tools/adb.exe" devices
```

Com dispositivo próprio conectado, instalar o APK isolado e testar, por exemplo:

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" install -r labs/visual-preview/android/build/outputs/apk/debug/NonstopVisualPreview-debug.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" shell am start -S -n com.stash.visualpreview/com.stash.preview.PreviewActivity --es scenario dynamic --es theme light --es page home
```

Compilação não comprova paridade visual. Conferir fontes, insets reais/teclado,
efeitos nativos/fallback, rolagem e desempenho num dispositivo antes de liberar.
O aplicativo Android principal **ainda precisa dos adaptadores de tela**, de ligação
dos seus ViewModels aos estados/callbacks compartilhados e da preservação das ações
extras exclusivas do Android. Essa migração não faz parte desta consolidação.

# Sonora — validação da consolidação

Execução local em 27/10/2026, sobre o estado de trabalho existente da branch
`feat/nonstop-v8-art-lab`. As alterações anteriores foram preservadas. Não houve
commit, push, migração de serviços nem integração das telas no Android principal.

## Resultado e limites

- Desktop: **179 testes, zero falhas e zero erros**, incluindo cinco novos testes
  de adaptação, conteúdo e acomodação do mini-player.
- **158 capturas Desktop concluídas**. Dimensões em pixels conferidas contra dp e
  densidade no manifesto; nenhuma largura é inferida apenas pelo tamanho do PNG.
- Home e quatro capturas históricas de navegação: **igualdade exata dos pixels**
  com a baseline local anterior à consolidação. Isso comprova preservação dessa
  configuração aprovada; não mede fidelidade às referências nem paridade Android.
- Hashes de todos os recursos em `labs/visual-preview/res`: **inalterados**.
- APK visual Android: `assembleDebug` concluído com sucesso.
- Módulo Android compartilhado: `:core:ui:compileDebugKotlin` concluído com sucesso.
  Há avisos de ícones Material depreciados; não impediram as duas compilações.
- Execução Android: **não realizada**. `adb devices` sem dispositivos; sem emulador
  ou AVD instalado nos caminhos locais verificados.

## Matriz efetivamente capturada

| Grupo | Combinações | Capturas adicionais |
|---|---|---:|
| Home aprovada | 280/320/360/390/412/480dp × fontes 100/125/150% × escuro/claro/AMOLED | 54 |
| Home com conteúdo dinâmico | Mesmas seis larguras × fontes 100/150% × três temas | 36 |
| Muitos itens, sem player, loading, empty, error | Seis larguras × fonte 150%, escuro | 30 |
| Explorar, Buscar, Biblioteca | 280/390/480dp × fontes 100/150%, escuro | 18 |
| Densidade | Home 390dp × fontes 100/150% × densidades 1,5/2/3 | 6 |
| Insets laterais | Home dinâmica 390dp, fonte 150%, densidade 2, laterais 24dp | 1 |
| Altura reduzida | Home dinâmica e sem player, 280×560dp, fonte 150% | 2 |
| Cinco destinos selecionados | 390dp × fontes 100/150% × densidades 1/2; descontadas combinações anteriores | 10 |
| Reserva de teclado | Buscar 390dp, fonte 150%, reserva inferior 300dp | 1 |
| **Total** | Combinações representativas, não produto cartesiano integral | **158** |

As capturas usam área segura simulada de 24dp no topo e na base, salvo a reserva
de teclado indicada. O APK isolado usa `safeDrawing.union(ime)` real.

O cenário dinâmico reutiliza capas existentes claras/escuras/coloridas, inclui
artwork indisponível, textos extremos, subtítulos ausentes e uma seção vazia.
O cenário Many usa 60 IDs distintos. Nada é inserido na biblioteca real.

## Problemas encontrados e tratamento

- A antiga captura `small-home` tinha 280 pixels, mas representava 390dp. Agora
  captura 280dp reais; a nova matriz separa largura, densidade e escala de fonte.
- Marca e controles do cabeçalho podiam disputar espaço. Nos outros destinos,
  controles passam para uma segunda linha quando necessário. Home segue sem cabeçalho.
- Textos ampliados do hero podiam exceder a área disponível. A caixa da foto
  permanece 304dp; somente metadados passam para baixo, com ações que podem quebrar linha.
- Título de seção longo podia ficar comprimido pela ação. A ação ocupa outra linha
  quando necessário; cards usam altura mínima e omitem subtítulos vazios.
- Insets, cinco rótulos, filtros extensos, player ausente e rail com 60 itens foram
  exercitados por testes. Mini-player continua com 52dp, sem sobrepor a navegação.

Inspeção visual representativa: Home nas seis larguras com fonte 150%, Home dinâmica
clara, Explorar estreito com fonte ampliada e Buscar com reserva de teclado.
O glow conserva os parâmetros aprovados, com capturas em quatro densidades.
Textos extremos de cards e metadados ainda usam reticências intencionais; filtros
podem exigir rolagem horizontal. O texto completo permanece na semântica.

## Artefatos e reprodução

Todos os caminhos abaixo são relativos à raiz do repositório:

- Galeria: `labs/app-desktop/build/validation/sonora-system/index.html`.
- Visão geral: `labs/app-desktop/build/validation/sonora-system/overview.png`.
- PNGs e combinações exatas: `labs/app-desktop/build/validation/sonora-system/matrix/matrix.json`.
- Verificações: `labs/app-desktop/build/validation/sonora-system/summary.json`.
- Baseline preservada: `labs/app-desktop/build/validation/sonora-system/baseline/`.
- APK: `labs/visual-preview/android/build/outputs/apk/debug/NonstopVisualPreview-debug.apk`.

```powershell
.\gradlew.bat -p labs/app-desktop desktopTest run "--args=--capture-system"
python labs/visual-preview/review_system.py
$env:ANDROID_HOME = (Resolve-Path labs/app-desktop/build/android-sdk).Path
.\gradlew.bat -p labs/visual-preview/android assembleDebug --max-workers=2
.\gradlew.bat :core:ui:compileDebugKotlin --max-workers=2
```

Os artefatos em `build/` são locais e podem ser removidos por `clean`.
O padrão de implementação está em [SONORA_VISUAL_SYSTEM.md](SONORA_VISUAL_SYSTEM.md).

## Pendências Android

Executar o APK isolado em dispositivo/emulador para verificar fontes, blur nativo,
fallback anterior à API 31, recortes, barras, teclado, rolagem e desempenho.
O aplicativo principal ainda necessita dos adaptadores de tela e da conexão dos
ViewModels aos estados/callbacks compartilhados, preservando suas ações exclusivas.
Essa migração permanece fora desta etapa; não há alegação de paridade visual Android.

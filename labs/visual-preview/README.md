# NØNSTOP — remodelação visual

Referências: capturas fornecidas pelo usuário em 27/10/2026 (Home, Explorar,
Buscar e Biblioteca). Esta tarefa é a linguagem geral do aplicativo, não Player v8.
Art Lab e player em tela cheia permanecem fora da remodelação.

## Abrir no Windows

Na raiz do repositório:

```powershell
# Prévia fixa, isolada: não instancia AppController, serviços, sessão ou biblioteca.
.\gradlew.bat -p labs/app-desktop run "--args=--visual-preview"

# App Desktop com dados reais e apresentação remodelada.
.\gradlew.bat -p labs/app-desktop hotRunDesktop --auto
```

Feche uma execução antiga antes de iniciar a nova. A prévia é identificada no título
da janela e seus diálogos explicam que as interações são locais. Play/pausa e curtida
alteram apenas estado de memória. A janela não desenha a moldura laranja vista na
captura antiga. A decoração nativa do Windows fica fora da área do aplicativo.

## O que está compartilhado

`core/ui/.../visual` contém o tema, estados de apresentação e os componentes de tela:
cabeçalho, filtros, banner, seções, cartões, grade, linhas, busca, mini-player e abas.
São os mesmos arquivos compilados pelo Desktop, pelo módulo Android `core:ui` e
pelo APK visual isolado. O renderer recebe IDs opacos e callbacks; não conhece
contas, rede, banco, download, navegação de plataforma nem áudio.

Home/Explorar/Buscar/Biblioteca do Desktop real usam esse renderer com adaptadores
de dados existentes. A biblioteca vazia continua vazia; nenhum conteúdo de referência
é semeado. Busca e resultados reais, importação, criação de playlist, menus de faixa,
curtidas e reprodução preservam os controladores anteriores. Conteúdo dos serviços
continua sujeito aos limites descritos no README do Desktop.

As telas secundárias Desktop recebem o tema. O player em tela cheia conserva o
tema/layout anterior. Tema claro, AMOLED e escala de fonte continuam disponíveis;
o alvo visual das referências é o tema escuro sem AMOLED, com escala de fonte 1.

`labs/visual-preview/kotlin` contém somente fixtures de desenvolvimento. As imagens
originais estão em `res/drawable`; a prévia usa regiões fotográficas como atlas em
tempo de renderização. Títulos, ícones, controles e layout são Compose. Não há uma
imagem da tela inteira sendo exibida como interface.

## Android

O laboratório Android possui ID próprio `com.stash.visualpreview`, sem permissões
de rede, sem serviços e sem inicializar o Application do aplicativo principal.

```powershell
python labs/visual-preview/prepare_sdk.py
$env:ANDROID_HOME = (Resolve-Path labs/app-desktop/build/android-sdk).Path
.\gradlew.bat -p labs/visual-preview/android assembleDebug --max-workers=2
.\gradlew.bat :core:ui:compileDebugKotlin --max-workers=2
```

APK: `android/build/outputs/apk/debug/NonstopVisualPreview-debug.apk` (o nome exato
é registrado em `output-metadata.json`). Para instalar e abrir em dispositivo próprio:

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" install -r labs/visual-preview/android/build/outputs/apk/debug/NonstopVisualPreview-debug.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" shell am start -n com.stash.visualpreview/com.stash.preview.PreviewActivity
```

O SDK local usa arquivos oficiais com hash verificado, dentro de `build/` ignorado.
O script não modifica `local.properties` nem registra aceitação de licenças.

**Limite de rollout:** a navegação e as telas de produção do aplicativo Android
principal ainda não foram substituídas pelos novos adaptadores de apresentação.
O APK isolado comprova compilação Android do renderer e permite revisão visual;
não significa que o APK principal já recebeu as quatro novas telas. A migração
dessas telas precisa preservar também as ações extras presentes apenas no Android.
Nenhum dispositivo/emulador estava conectado durante esta implementação; não há
alegação de comparação visual entre Android e Desktop, nem de fidelidade 1:1.

## Capturas e revisão

```powershell
.\gradlew.bat -p labs/app-desktop desktopTest
.\gradlew.bat -p labs/app-desktop run "--args=--capture-visual"
python labs/visual-preview/compare.py
```

Saída: `labs/app-desktop/build/validation/remodel/`. As quatro capturas mantêm as
dimensões das referências: 311×710, 309×714, 301×722 e 310×724. A área superior
correspondente à barra de status é reservada, sem desenhar relógio/bateria falsos.
O relatório HTML contém imagens lado a lado, sobreposição com slider e erro RGB
bruto. O JSON registra a máscara de exclusão para moldura e áreas do sistema.
Erro RGB não é porcentagem de fidelidade e não é aprovação estética.

Capturas adicionais: Home pequena, Home com fonte 150%, Biblioteca vazia,
carregando, erro, tema claro e AMOLED. Testes exercitam navegação, busca, filtros,
transporte, callbacks com IDs opacos, opções sem disparar o clique pai e texto longo.

Diferenças conhecidas: Inter é uma aproximação da fonte original; wordmark é
tipográfico; navegação e controles superiores têm glifos vetoriais próprios,
ajustados à referência, enquanto os demais ícones vêm do Material;
superfícies usam transparência/gradientes sem
blur de conteúdo real; arte do banner tem apenas regiões disponíveis na captura
e difere do original. Animações não são dedutíveis de screenshots. Na versão com
dados reais, capas vêm dos dados; categorias sem arte usam ilustrações vetoriais.

Sem commit, push ou merge. Aprovação visual continua sendo do usuário.

## Verificação em 27/10/2026

- Desktop: 174 testes em 30 classes, zero falhas/erros.
- APK visual Android: `assembleDebug` concluído.
- Módulo Android principal: `:core:ui:compileDebugKotlin` concluído com SDK local.
- Capturas Compose/Skia das quatro telas e sete variantes renderizadas e inspecionadas.
- `git diff --check` passou (avisos de CRLF preexistentes).
- `adb devices` não encontrou dispositivo ou emulador; validação visual Android pendente.

Refinamento dos menus: casa/bússola preenchidas quando selecionadas, Biblioteca
com quatro traços, contornos finos nos estados inativos e halo lilás no símbolo
ativo. Cabeçalho com ícones finos e alinhamento óptico; filtros com proporções,
contraste e bordas ajustados. Os quatro estados da barra inferior são capturados
em `navigation-{home,explore,search,library}.png`. Os seis testes de apresentação
e navegação passaram após a alteração; o APK visual Android foi recompilado.

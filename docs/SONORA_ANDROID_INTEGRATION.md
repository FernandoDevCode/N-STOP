# Sonora — integração Android principal

Execução autorizada pelo pedido de 27/10/2026, concluída tecnicamente em
28/10/2026. Checkout existente, sem commit/push/merge. Alterações locais anteriores
preservadas. Esta execução não representa aprovação artística 1:1.

## Implementação

| Área | Integração e funções preservadas |
| --- | --- |
| Início | Filtros, cards, rails e hero compartilhados, ligados às recomendações, playlists e mixes reais. Ordem/visibilidade configuradas, gêneros, abrir/play/criar/editar, menus, personalização e podcasts preservados. Ações de serviços e suporte ficam abaixo do conteúdo musical. |
| Explorar / Rádio | Adaptadores de `HomeUiState` para `VisualScreenState`; mesmos provedores e IDs, navegação real, gêneros, estados de carregamento/vazio. Nenhum catálogo Desktop ou fixture inserido. |
| Buscar | Cabeçalho e campo compartilhados, tema e superfícies Sonora. Resultados, histórico, artista/álbum, reprodução, download, fila, rádio e salvar em playlist permanecem na composição Android. |
| Biblioteca | Cabeçalho, filtros, pesquisa e linhas compartilhadas de playlists/artistas/álbuns. Dados persistidos, ordenação, fontes, importar, downloads, curtidas, seleção em lote e menus existentes preservados. |
| Navegação | Barra compartilhada na ordem Início → Explorar → Rádio → Buscar → Biblioteca. Mesmo NavController, save/restore, destinos secundários, deep links e botão voltar. Insets e teclado tratados pelo host. |
| Mini-player | Uma única instância musical, com material e dimensões Sonora. Transporte, progresso/seek, gestos, favoritos e sessões reais mantidos. Podcast conserva o transporte próprio já existente e mutuamente exclusivo. |
| Player | Renderer e diretor comuns do Art Lab, capa e metadados atuais, controles do NowPlayingViewModel. V7 preservado como recuperação automática/manual; letras sincronizadas e Listen Together conservam a composição funcional Android. |

As composições Android com ações extras foram preservadas onde o contrato visual
não representava todas as funções. Não há alegação de igualdade visual entre
Android e Desktop. Painel de letras ainda usa a apresentação funcional anterior.

## Player V8

O módulo `core:art` compila os arquivos comuns do laboratório. Os mestres aprovados
foram conferidos por SHA-256 e não foram alterados:

- Megadeth: `3547184210edca28399a0945dc28192e65341ebd1545be070873593004e3d68d`
- Pink Floyd: `6b7cbd4da0fa601379b2d8d790d70aac2b532d22bb35739370fc3958fec430f0`

Análise clássica/procedural, sem IA. Cache limitado, checksum, worker e cancelamento
por troca de capa. Fallback neutro para capa ausente. Microinterações de seek
funcionam; reação à energia de áudio fica inativa porque não existe fonte PCM real
conectada. Nenhum relógio simulado ou letra de teste entra no fluxo de produção.

A infraestrutura Media3, os repositórios, autenticação, banco, protocolos, ícones,
marca, applicationId e credenciais de assinatura não foram alterados.

## Verificação

Suítes executadas: **1.711 testes, zero falhas/erros, zero ignorados**.

| Suíte | Testes |
| --- | ---: |
| Home, incluindo adaptadores Explorar/Rádio | 66 |
| Buscar | 133 |
| Biblioteca | 155 |
| Now Playing, incluindo estado V8 e recuperação de falha em Robolectric | 259 |
| Reprodução / core:media | 891 |
| Arte Android: cancelamento e equivalência de pixels | 2 |
| Apresentação e aplicativo Desktop | 179 |
| Art Lab | 26 |

Build principal: `:app:assembleDebug`. `git diff --check` aprovado, com avisos
preexistentes de conversão LF/CRLF. A assinatura Debug foi verificada com
`apksigner`; certificado SHA-256:
`800f720a316b07f645382371fef41dfab64f39efdad704d6a5050265f16c70aa`.

As primeiras tentativas de build tiveram bloqueios corrigidos: SDK inexistente em
`local.properties` (usado o SDK local via `ANDROID_HOME`), JAR retido por daemon
ocioso, dependência JSON/import de teste e delimitação de argumento PowerShell.
A inspeção do pacote também identificou que filtros Java não excluíam fontes
Kotlin; o módulo passou a usar uma lista explícita de fontes Kotlin de produção.
Não houve teste executado terminando com falha; esses problemas impediram builds
ou a compilação de testes antes de serem corrigidos.

Logs locais: `integration-packaging-final.log`, `integration-final-build.log`,
`integration-validation-3.log`, `integration-artlab-tests.log`,
`integration-visual-tests.log`. Relatórios XML/HTML ficam nos diretórios `build`
de cada módulo. Resumo verificável do APK e testes: `sonora-android-validation.json`.

ADB não encontrou dispositivo. Não há AVD nem emulador no SDK local verificado.
Não foram executados instalação, streaming físico, navegação no celular, testes
de segundo plano/recriação de Activity, offline real, ANR/jank ou fidelidade visual
Android. As funcionalidades conectadas precisam dessa rodada no Redmi Note 14.

## Instalação preservando dados

O build Debug mantém o ID preexistente `com.stash.app.debug`, versão 0.9.114 (150),
Android mínimo 8.0/API 26 e target 35. APK do módulo principal `app`, não do preview.

Caminho: `C:\Users\User\Documents\GitHub\Spotify\Stash\app\build\outputs\apk\debug\app-debug.apk`.
Tamanho: 185.686.736 bytes (aproximadamente 177 MiB).
SHA-256: `6db002fa735be6e65c536c15082ded380ec95cb58c4d57ca21820efc8f8a7e98`.
Conteúdo DEX conferido: renderer compartilhado, host V8, UI Sonora e serviço de
reprodução presentes; catálogo, fixtures e letras de teste do laboratório ausentes.

```powershell
$env:ANDROID_HOME = (Resolve-Path labs/app-desktop/build/android-sdk).Path
.\gradlew.bat :app:assembleDebug --max-workers=2 '-Pkotlin.compiler.execution.strategy=in-process'
& "$env:ANDROID_HOME/platform-tools/adb.exe" devices
& "$env:ANDROID_HOME/platform-tools/adb.exe" install -r "C:\Users\User\Documents\GitHub\Spotify\Stash\app\build\outputs\apk\debug\app-debug.apk"
```

`install -r` preserva dados de uma instalação com o mesmo ID e assinatura compatível.
Se houver erro de assinatura, interromper; não desinstalar nem limpar dados.
A versão release `com.stash.app` é outro aplicativo: seus dados permanecem intactos,
mas a biblioteca privada dela não fica acessível ao Debug. Não houve mudança de ID
para contornar essa separação nem migração de dados.

Para aceitar no aparelho: navegar nas cinco abas, carregar catálogo, buscar e tocar
uma música, controlar pelo mini-player/V8, seek/anterior/próxima, atualizar arte,
abrir letras/fila/menus, alternar abas, testar segundo plano/rotação, offline e capa
indisponível. Verificar logcat por crashes/ANRs e revisar acabamento e desempenho.

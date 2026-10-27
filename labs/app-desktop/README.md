# NØNSTOP Desktop — migração do app Android atual

**Remodelação visual (27/10/2026):** as quatro telas principais do Desktop agora
usam a apresentação compartilhada em `core/ui/visual`. A prévia fixa é aberta com
`run "--args=--visual-preview"`. Consulte [o laboratório visual](../visual-preview/README.md)
para capturas comparativas, limites de fidelidade e o APK Android isolado. A tabela
abaixo registra a migração funcional anterior; o APK Android principal ainda não
recebeu os novos adaptadores de tela.

O laboratório visual agora prepara um SDK local em `build/android-sdk` e compila
o APK isolado e `:core:ui:compileDebugKotlin` usando `ANDROID_HOME` explícito.
As notas antigas sobre SDK ausente descrevem a situação antes dessa preparação;
`local.properties` permanece intacto.

Pedido: usar o app atual no Windows em Kotlin nativo, com código compartilhado.
Base identificada: checkout `6310abe2c`, produção `ad6a0ade1`, Android 0.9.114
(versionCode 150). O APK local de 17/10 é anterior ao último commit de produção;
os fontes são a origem da migração. Não há decompilação do APK.

**A migração integral ainda não está concluída.** A execução padrão já abre um
aplicativo navegável com dados, busca e áudio reais, em vez da antiga bancada.
A tabela abaixo distingue implementação e paridade com o telefone.

## Executar

Na raiz do repositório, com JDK 21 e `ffmpeg`, `ffprobe` e `yt-dlp` no PATH:

```powershell
.\gradlew.bat -p labs/app-desktop run
.\gradlew.bat -p labs/app-desktop hotRunDesktop --auto
```

Se a bancada antiga estiver aberta, feche-a e encerre sua execução antes de abrir
a nova versão. Alterações no build/dependências requerem reiniciar essa execução;
hot reload não substitui o processo antigo automaticamente nesses casos.

`hotRunDesktop --auto` usa Compose Hot Reload 1.0.0 e JetBrains Runtime 21,
provisionado pelo resolver Foojay. `run` é a execução convencional.

A bancada anterior permanece acessível somente com `--args=--workbench`.
Nenhuma conta, música, playlist ou histórico de demonstração é semeado no app.
O usuário optou por começar com biblioteca nova no Desktop, sem dados do celular.

## Implementado neste alvo

| Área | Implementação e limites |
|---|---|
| Navegação | Cinco abas reais, detalhes, fila e configurações. A barra é o mesmo componente consumido pelo Android. O host Desktop ainda é próprio. |
| Home | Wordmark, filtros, cartão de entrada e componentes originais; rails derivados da biblioteca real. Não replica ainda todos os banners/serviços do Home Android. |
| Biblioteca | Faixas, playlists, álbuns, artistas, curtidas; busca local, criação de playlist e inclusão de músicas. Persistência própria em JSON com escrita atômica. |
| Busca | Clientes `InnerTubeClient`/`YTMusicApiClient` originais, com sessão YouTube opcional. Busca pública continua funcionando sem conta. Detalhes de álbuns/artistas e paginação usam o mesmo código do Android. |
| Explore | Consulta pública do home YouTube Music, usando os parsers de álbuns existentes. **Não substitui nem implementa o provedor Qobuz do Android.** |
| Player | Layout, backdrop, transporte, coração e seek `PlayerV7` originais compilados no Desktop. Mesma análise de cores a partir de pixels; carregamento de imagem e relógio têm adaptadores Windows. Metadados e menu ainda são hosts adaptados. |
| Áudio | FFmpeg decodifica para PCM estéreo 48 kHz/16 bits; Java Sound envia ao Windows. Play/pausa, seek, anterior/próxima, shuffle, repetição e volume. Streaming YouTube resolvido por yt-dlp; arquivo local tem preferência. Sem promessa de saída hi-res exclusiva. |
| Downloads | Download explícito YouTube via yt-dlp/FFmpeg para Opus e registro na biblioteca. Não usa a cascata lossless/Qobuz nem os workers Android. |
| Letras | Fonte LRCLIB e parser LRC originais compartilhados; sidecar LRC local, destaque temporal e seek ao clicar. Sem todos os provedores/TTML do Android. |
| Aparência | Tema, AMOLED, fontes TTF, dimensões e controles compartilhados; preferências e volume persistem em `local-data/settings.json`. |
| Arquivos | Importação de áudio local com metadados ffprobe. Leitura opcional de backup ZIP/stash.db em modo somente leitura, sem transferir credenciais. Não necessária para começar. |
| Contas | Spotify por `sp_dc`, YouTube por sessão validada no servidor, Discord por token de sessão; Last.fm por autorização no navegador. Cartões de conexão originais. Cofre Windows DPAPI, sem fallback em texto puro. |
| Sincronização | Leitura manual de curtidas/playlists Spotify/YouTube, pastas Spotify, mixes dos serviços opcionais e álbuns salvos YouTube opcionais. Importação aditiva, identidade remota estável, cancelamento antes de gravar e sinalização de resultados parciais. |

Dados Desktop ficam em `labs/app-desktop/local-data/` quando executado pelo Gradle:
`library.json`, `settings.json` e `audio/`. A pasta é ignorada pelo Git.
Credenciais ficam em `local-data/accounts/*.dpapi`, protegidas para o usuário Windows
atual. Falha de decifragem preserva o arquivo; a tela oferece remoção explícita antes
de reconectar. Desconectar remove a sessão local e preserva a biblioteca importada.
Backup é uma importação da biblioteca visível; não é restore completo dos bancos,
preferências, índices, identidades, logins ou bytes de áudio do telefone.

## Ainda necessário para paridade integral

- Login embutido via WebView como no Android; no Desktop, Spotify/YouTube/Discord
  usam a opção manual de sessão, e Last.fm usa autorização no navegador.
- Scrobbling/histórico Last.fm e Rich Presence Discord. Conectar a conta não ativa
  essas funções. Credenciais Android/Keystore não são portáveis por cópia.
- Modos de espelhamento com remoções, agendamentos e histórico completo de sync.
  A sincronização atual é manual/aditiva e nunca exclui faixas/membros anteriores.
- Provedores Qobuz/lossless, cascata de resolução e downloads, condições de rede,
  workers e gerenciamento completo de falhas/downloads.
- Geração de rádios, Daily Discover e mixes personalizados; afinidade/histórico,
  receitas, tags e todas as seções personalizadas do Home.
- Podcasts, audiobooks, comunidade, Listen Together e link/sync entre dispositivos.
- Equalizador, crossfade, Cast, temporizador, velocidade e saída hi-res exclusiva.
- Demais opções/telas Android, menus completos e todas as funções de letras.
- Comparação visual e de comportamento com o Android em estados equivalentes.

As integrações ausentes são identificadas na interface; não há login falso nem
conteúdo de catálogo inventado. A presença de uma aba não significa que todos os
seus serviços Android foram migrados.

## Código compartilhado e fronteiras

O build independente compila os arquivos originais de `core/model`, uma seleção
explícita de `core/ui`, Home/Settings/PlayerV7, parsers YouTube e LRCLIB/LRC. A lista
está no build.gradle.kts. Recursos R/XML são indexados em build a partir dos res
originais e lidos por um adaptador Desktop; não há cópia manual da marca ou fontes.

Extrações pequenas nos módulos de produção separam fontes/barras Android,
barra de navegação, helpers JSON/parser de busca, qualifier LRCLIB, menu Android
PlayerV7 e adaptador Bitmap. As chamadas Android continuam consumindo os mesmos
componentes; reações da sala são recebidas como slot de composição.
Na etapa de contas, `TokenStore` separa o armazenamento Android do Windows; Hilt
continua ligando o Android a `EncryptedTokenStore`. O gerenciador original de tokens,
Spotify/TOTP, clientes Spotify/YouTube e cliente Last.fm são compilados diretamente
dos fontes. A renovação Spotify e a desconexão usam a mesma trava, impedindo que uma
renovação em andamento restaure uma sessão desconectada. Logs Android dos clientes
são desativados no adaptador Desktop para não emitir diagnósticos de autenticação.

Os hosts `NativeApp`, `AppScreens` e `AppController` são adaptações Desktop e
**ainda não** uma apresentação integral compartilhada com o app Android.
O compartilhamento atual é Android/JVM Desktop, com dependências java.* existentes;
não é uma migração iOS/Web de commonMain. Art Lab/renderer v8 não foi integrado.

## Verificar

```powershell
.\gradlew.bat -p labs/app-desktop desktopTest
.\gradlew.bat -p labs/app-desktop run "--args=--capture-app"
.\gradlew.bat -p labs/app-desktop run "--args=--probe-online"
.\gradlew.bat -p labs/app-desktop run "--args=--probe-shared-catalog"
```

- `desktopTest`: inclui testes originais de modelos, parsers de busca, LRCLIB,
  LRC, direção de arte e seek, além de navegação, playlists, curtidas, persistência,
  leitura de SQLite e decodificação FFmpeg para um sink sem alto-falantes.
- `--capture-app`: Compose/Skia offscreen real; home/library/search/settings/
  appearance/accounts/sync e player em `build/validation/`. Player usa fixture explicitamente
  identificada, pausada, sem áudio nem capa inventada. Não captura a área de trabalho.
- `--probe-online`: busca pública, resolução e mais de um segundo de PCM pelo
  backend real, com sink silencioso; verifica recuperação de duração e pausa.
  Não imprime URLs assinadas nem usa credenciais. Depende da rede/serviço no momento.
- Downloads completos e áudio em dispositivo físico Windows não são comprovados
  pelo probe silencioso; não há alegação de todos os fluxos validados.
- O build Android permanece sem verificação: o SDK configurado em local.properties
  aponta para uma pasta inexistente neste PC. Não se alterou essa configuração.

Capturas não constituem aprovação visual nem prova 1:1. Sem commit, push ou merge.

Primeira rodada em 2026-10-27: **137 testes em 21 classes, zero falhas/erros**;
capturas reais das seis telas e `git diff --check` passaram. O probe online retornou
quatro seções de busca, recuperou a duração pelo resolvedor, decodificou mais de um
segundo de PCM pelo backend Desktop e confirmou a pausa, sem usar alto-falantes.

## Usar contas e sincronização

1. Reabra o app após atualizar as dependências e vá em Configurações → Contas.
2. Spotify/YouTube/Discord: entre no site oficial pelo navegador e informe a
   credencial da sua sessão somente na tela local do aplicativo. A tela explica
   o campo de cada serviço; os campos são mascarados e não usam estado salvável.
3. Last.fm: o app usa `lastfm.apiKey`/`lastfm.apiSecret` do arquivo local do build
   ou `LASTFM_API_KEY`/`LASTFM_API_SECRET`. Também é possível configurá-los no próprio
   fluxo de conexão. Abra a autorização no navegador e clique em Concluir conexão.
   A sessão é persistida somente após o serviço confirmar a autorização.
4. Abra Sincronização, escolha serviços/conteúdo e pressione Sincronizar agora.

Cookies YouTube são verificados no menu de conta do servidor antes da gravação;
uma string contendo SAPISID, sozinha, não é tratada como conexão comprovada.
Faixas Spotify importadas podem ser resolvidas pelo matcher original do YouTube
quando reproduzidas. Arquivos locais ausentes geram erro local, sem buscar seus
metadados privados na rede. Não há gravação automática de likes nem envio de
presença/scrobbles para os serviços nesta etapa.

Validação de contas usa servidor HTTP de teste e credenciais sintéticas; DPAPI foi
exercitado de verdade no Windows. A busca pública e o matcher do cliente YouTube
original também foram verificados ao vivo sem conta. **Nenhuma conta pessoal foi
conectada nesta execução**, portanto login/sync com sessões reais ainda exige a
validação manual no aplicativo. Isso não declara paridade integral com o Android.

Rodada de contas/sync em 2026-10-27: **171 testes em 29 classes, zero falhas/erros**.
Inclui persistência/restauração DPAPI real, recusa de sessão anônima, os quatro
fluxos com servidor de teste, refresh concorrente único, desconexão durante refresh,
paginação por contagem bruta, pastas Spotify, IDs estáveis de playlists, respostas
parciais, cancelamento antes de commit e navegação Contas → Sincronização.
Capturas `app-accounts.png` e `app-sync.png` foram renderizadas e inspecionadas.

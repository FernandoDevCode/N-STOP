# Sonora — Plano mestre de biblioteca, sincronização e downloads

**Data:** 25/11/2026 · **Status:** implementação parcial em branch de trabalho; nenhum release novo. Ver checkpoint de execução ao final.
**Repositório:** `FernandoDevCode/N-STOP` · **Branch canônica:** `nonstop` · **Base auditada e revalidada:** `c2f54e5bdfd9661b3792620767c2c290032da280` (v226). Revalidar HEAD antes de executar.
**Destino sugerido no repositório:** `docs/sonora/LIBRARY_SYNC_DOWNLOADS_PLAN.md` (nesta branch de trabalho).
**Autoridades:** `AGENTS.md`, `docs/sonora/UI_RULES.md`, `docs/sonora/IDENTITY_PROVIDERS_GATE.md`, `docs/sonora/SEARCH_REWORK_PLAN.md` e `docs/SONORA_CONSOLIDATION.md`. Este plano fecha o contrato de biblioteca; não substitui contratos de Busca ou Player.

## 0. Missão e limites

Entregar um único aplicativo integrado, sem exigir reinstalação limpa. Biblioteca representa o que o usuário salvou/importou; sincronização atualiza coleções remotas selecionadas; download controla arquivos no dispositivo. Spotify é origem de catálogo pessoal em leitura, não destino de escrita. Reprodução local/streaming usa o motor já existente. Não prometer o mesmo catálogo, áudio, resolução de faixas ou disponibilidade do Spotify: a consulta de biblioteca é uma dependência externa e a reprodução usa os provedores autorizados pelo gate vigente.

Não reconstruir Media3, animações, Player V8, Busca, Home editorial, matching ou infraestrutura de download sem necessidade comprovada. Não reativar Stash, Last.fm, Qobuz, compartilhamento remoto ou sync web legado. Preservar FLAC existente, app ID, assinatura, chaves e dados. Nenhum backend novo. Sem mudanças estéticas fora das superfícies afetadas.

## 1. Contrato de produto fechado

### 1.1 Biblioteca e origem

- [ ] Músicas curtidas são uma coleção única na Biblioteca, união de curtidas locais e importadas das fontes selecionadas, sem duplicação de identidade. Filtros de origem (Sonora, Spotify, YouTube Music se já utilizado) são opcionais e não criam cópias de arquivos. A entrada na Home e a Biblioteca abrem o mesmo modelo de coleção.
- [ ] Curtida no Sonora é exclusivamente local. Nenhuma adição, remoção, espelhamento, auto-save ou alteração de playlist é enviada ao Spotify. Não inferir curtida a partir de reprodução. Não enviar histórico de reprodução ao Spotify como parte desta tarefa.
- [ ] Na primeira configuração do Spotify, importar curtidas por padrão; playlists comuns desmarcadas por padrão em novas configurações. Em instalações existentes, preservar escolhas e conteúdo já importado; não reativar automaticamente playlists explicitamente desmarcadas. Desativar descoberta automática de mixes Spotify em novas configurações desta fonte, sem apagar mixes existentes. A importação das playlists é opcional e independente; a seleção usa catálogo de coleções disponíveis, sem inundar a Biblioteca.
- [ ] Salvar/curtir/importar não baixa áudio. Exibir músicas salvas, playlists locais/importadas, artistas e álbuns derivados das músicas da Biblioteca, mesmo sem arquivo ou resolução de streaming conhecida. Conteúdo transitório de pesquisa, histórico isolado e candidatos de mixes não entram automaticamente na Biblioteca. Arquivos locais importados e downloads individuais já existentes continuam acessíveis; downloads de faixas avulsas ficam disponíveis na coleção Downloads e não implicam curtida.
- [ ] Sem conexão, navegar normalmente pelos metadados persistidos. Faixas sem arquivo válido ficam indisponíveis offline; não iniciar rede/retry em loop. Ao tocar faixa não baixada sem conexão, mensagem clara; reprodução da coleção offline usa apenas faixas disponíveis, preservando a ordem relativa. Online, preferir arquivo local válido e usar resolução existente para as demais; não prometer match perfeito.
- [ ] Não expor modos globais Online/Offline, Refresh/Accumulate como condicionantes de biblioteca/download. Conectividade, Wi-Fi, qualidade de áudio e armazenamento continuam preferências operacionais válidas.

### 1.2 Curtidas: identidade e transições

Estados independentes por identidade de faixa: `L` (curtida local), `S` (vínculo remoto Spotify da conta), `H` (ocultação local de S), e origens externas existentes quando habilitadas. Visibilidade efetiva = `L OR (S AND NOT H)` mais outras origens locais/importadas efetivas. Não usar `spotifySavedAt` como S; é marca histórica de escrita pelo app. Não transformar importadas automaticamente em curtidas locais independentes: isso impediria refletir remoções legítimas da origem.

| Evento | Resultado |
|---|---|
| Spotify adiciona faixa | Criar vínculo S; não escrever L nem baixar sem intenção. |
| Spotify remove faixa (leitura completa) | Remover S, manter L e demais vínculos; H pode ser limpo após ausência confirmada. |
| Usuário curte faixa que não aparece como curtida | Criar L, limpar ocultação local correspondente; sem escrita remota. |
| Usuário toca coração preenchido/descurte no Sonora | Remover L e ocultar localmente vínculos externos efetivos existentes; nenhuma alteração remota. Não reaparecer a cada sync. |
| Usuário desfaz descurtida ou curte novamente | Restaurar estado local/visibilidade, sem escrita remota. |
| Spotify remove e volta a curtir após ausência confirmada | Tratar como nova curtida remota, reexibir; não inferir ciclo se não observado. |
| Mesma faixa em ambas as origens | Uma linha, duas origens; remoção remota não apaga L. |
| Falha/expiração/desconexão | Manter último estado local confirmado. |

Aplicar a mesma projeção efetiva ao player, notificação/MediaSession, mini-player quando houver ação, listas e seleção múltipla. Preservar timestamps originais quando disponíveis; não usar data de cada sync para redefinir a ordem. Ordenação padrão: mais recentes primeiro, com desempate estável; importadas respeitam `addedAt` remoto, sem inventar datas ausentes. Undo restaura estado completo da ação local. Favoritos são deduplicados por identidade de gravação confirmada, não por título/artista isolados. Versões ao vivo, remaster, cover, edição explícita e vídeo não são fundidas só por semelhança textual.

### 1.3 Playlists

- [ ] Importar apenas playlists selecionadas; preservar ordem remota, nome, capa e ocorrências repetidas quando a origem as fornecer. Coleções vazias selecionadas permanecem visíveis. Faixas repetidas contam como posições distintas, mas compartilham a mesma identidade/arquivo de áudio.
- [ ] Playlist remota é uma coleção espelhada somente leitura quanto à composição de origem. Permitir copiar para playlist local editável; não fazer edição remota. Preservar adições locais legadas na migração e distingui-las da composição remota (não descartar; se necessário exibir como extensão local e oferecer cópia local). Não confundir playlist do Spotify com playlist local de mesmo nome.
- [ ] Mudança de conteúdo em snapshot completo atualiza exclusivamente os vínculos de origem; snapshot parcial só acrescenta itens confirmados (sem remover/reordenar vínculos existentes por dados incompletos). Nome/capa só atualizam a partir de campos válidos, respeitando substituições locais explícitas já suportadas.
- [ ] Ao desativar atualização de uma coleção, conservar a cópia já importada, com estado “Sincronização pausada”; não apagar a coleção ou arquivos. Remover da Biblioteca é ação local explícita separada, com confirmação quando necessário, sem deletar remoto. Ao remover localmente, não ressuscitar por descoberta automática: marcar seleção desativada até nova escolha. Se a playlist deixar de existir na origem após inventário completo, conservar cópia local com estado “Não disponível na origem”, sem exclusão silenciosa; oferta de remover ou copiar. Erro/contagem suspeita não é ausência.
- [ ] Desconectar Spotify pausa atualização, limpa credenciais conforme comportamento existente e mantém dados/arquivos. Não cancelar downloads independentes apenas por origem de catálogo. Troca de conta não pode substituir ou misturar silenciosamente coleções de contas distintas. Manter coleções da conta antiga preservadas e pausadas; a nova conta usa namespace próprio. Não expor ID de conta, tokens ou cookies em UI/logs. Se conta legada não puder ser atribuída com confiança, preservá-la como origem legada até confirmação; não inferir proprietário só pelo login atual.

### 1.4 Downloads

- [ ] Download de coleção no cabeçalho de Músicas curtidas e de playlists locais/importadas. Na coleção agregada de curtidas, a intenção cobre toda união efetiva e futuras adições; não fazer o usuário baixar cada origem separadamente. Se já houver intenção específica de uma coleção de origem antiga, preservar sem forçar download da união inteira na migração.
- [ ] Download individual por menu contextual de uma faixa. Um arquivo válido por identidade de áudio sempre que seguro (sem fundir versões distintas); manter arquivos legados e não deduplicar fisicamente de forma destrutiva na migração.
- [ ] Intenção persistente individual + intenções de coleções que contêm a faixa; estado real do arquivo separado da intenção. `keepOffline` é reaproveitado para playlists. Fila não é fonte exclusiva de intenção (histórico de fila pode ser limpo). Coleção agregada precisa de preferência persistente própria ou representação existente equivalente, sem materializar cópia desnecessária de faixas.
- [ ] Ligar download de coleção: enfileirar faltantes, sem duplicar trabalhos; futuras entradas baixam automaticamente. Desligar: confirmação de remoção de downloads, cancelar demanda daquela coleção e excluir arquivos somente se nenhum outro pedido válido os protege. Se arquivo ainda necessário por outra playlist ou pedido individual, mantê-lo e explicar. Faixas retiradas da coleção deixam de ser pedidas por ela; sem outro pedido, podem liberar áudio gerenciado pelo Sonora somente após atualização confiável e conforme consentimento de download; jamais arquivos de usuário importados/legados preservados. Cancelar operação antes da confirmação não altera nada.
- [ ] Remover download individual: retirar pedido individual; se coleção offline ainda o exige, manter arquivo e explicar como desligar download da coleção. Não criar exceção por faixa que contradiga “baixar tudo”. Remoção explícita de música da biblioteca não é equivalente a apagar arquivo ou bloquear música para sempre.
- [ ] Estados visíveis: não baixada, aguardando (incluindo Wi-Fi), baixando, disponível offline, incompleta/falha com tentar novamente. Contagem de coleção inclui todas as faixas únicas efetivamente elegíveis/necessárias, contabiliza indisponíveis e falhas explicitamente; nunca esconder falhas do denominador para declarar “Baixada”. Ocorrências duplicadas não exigem downloads distintos. Zero faixas: mostrar coleção vazia, sem falso 100%.
- [ ] Rede celular respeita política de download, inclusive ações manuais e retomadas (qualquer override exige consentimento explícito). Retomar interrupções e reinício sem perda de intenção, sem duplicação e sem corrida entre exclusão e conclusão. Espaço insuficiente/falta de permissão de armazenamento são falhas recuperáveis, sem eliminar biblioteca. Não baixar automaticamente por importação/sync sem intenção existente.

### 1.5 Sincronização e transparência

- [ ] Manual: “Sincronizar agora” atualiza fontes/coleções habilitadas; opção específica de Spotify pode ser usada sem depender de YouTube. Automática: reaproveitar agenda WorkManager/SyncScheduler, com execução única, restrições de rede, backoff e sem bloqueio da UI. Não polling agressivo a cada tela; inicial após conexão/seleção, depois agenda já existente. Incremental quando há revision/stamp confiável; curtidas sem revisão confiável exigem caminhada paginada completa para remover vínculos. Não inventar API incremental.
- [ ] Resultado por fonte e tipo (curtidas/playlists): última tentativa, último sucesso completo, encontrados, vinculados, adicionados/removidos, ignorados/indisponíveis, parcial/erro. Separar downloads do resultado da sincronização. Indicar sessão expirada e CTA reconectar; sem log de cookie/token/corpo privado.
- [ ] Coleção de curtidas precisa ser selecionável antes de qualquer resposta, inclusive vazia ou com falha; não depender de uma linha criada por snapshot. Catálogo de playlists para seleção é inventário, não Biblioteca. Se não há seleção, não baixar conteúdo das playlists comuns desnecessariamente; metadados necessários à seleção podem ser buscados.
- [ ] Leitura completa: aplicar remoções de origem apenas quando consulta, paginação, identidade e contagens são confiáveis. Parcial: mesclar somente adições confirmadas e manter ordem/estado anterior; marcar parcial. Falha: preservar. Vazio validado: snapshot explícito com zero e identidade da conta confirmada; remover apenas vínculos da origem (não L, outros vínculos ou arquivos protegidos). Vazio ambíguo: não excluir. Caso total bruto > 0 mas zero faixas válidas por alteração de parser, tratar como suspeito, não biblioteca vazia. Faixas legitimamente indisponíveis contam separadamente.

## 2. Evidências e pontos de alteração

Base: [commit auditado](https://github.com/FernandoDevCode/N-STOP/commit/c2f54e5bdfd9661b3792620767c2c290032da280). Paths abaixo são do checkout auditado, nomes internos `stash` preservados por compatibilidade. O executor verifica call sites/rotas atuais antes do patch.

| Evidência | Arquivo / símbolo | Ação |
|---|---|---|
| `syncEnabled` mistura funções, `keepOffline` já existe | `core/data/.../db/entity/PlaylistEntity.kt`; `PlaylistDao` | Separar seleção/visibilidade e download; distinguir inventário de biblioteca. |
| Curtidas `getLikedSongs` usa GraphQL `fetchLibraryTracks`, paginação com limite e snapshot parcial | `data/spotify/.../SpotifyApiClient.kt`, `SpotifyTrackParser.kt`; `core/data/.../sync/workers/PlaylistFetchWorker.kt` | Endurecer parsing, contagem, repetição de página, classificação de erro e snapshot vazio. |
| Diff ignora coleção em Download mode; usa Refresh/Accumulate | `DiffWorker.kt` | Tirar vínculo de download; aplicar política única por tipo e confiabilidade; manter stamps e proteção de adições locais. |
| Consultas de músicas/artistas/álbuns dependem de arquivo | `MusicRepositoryImpl.kt`, `TrackDao.kt`, `PlaylistDao.kt`, `LibraryViewModel.kt` | Criar consultas explícitas de biblioteca salva e downloads, sem trocar indiscriminadamente todos os usos de consultas antigas. |
| Desligar download pode desligar sync; remover downloads em lote não protege outras coleções | `MusicRepositoryImpl.kt` (`setPlaylistDownload`, `removeDownloadsForPlaylist`, `removeDownload`, `cleanOrphanedMixTracks`); `DownloadQueueDao` | Unificar checagem de demanda, cancelamento, limpeza e consistência do arquivo. |
| `keepOffline` pós-sync já enfileira faltantes | `queueKeptPlaylists`, `DiffWorker`, `DownloadQueueDao` | Reaproveitar worker e locks; incluir coleção agregada e likes locais. |
| Botão de download exclui curtidas; cálculo descarta falhas do total | `PlaylistDetailViewModel.kt`, `LikedSongsDetailViewModel.kt` e respectivas telas | Unificar estado de download de coleção, oferecer botão na coleção agregada e playlists. |
| Curtidas locais existem, mas player usa `stashLikedAt` | `StashLikedPlaylistRepository`, `TrackDao.observeLikeState`, `NowPlayingViewModel`, `StashPlaybackService`, `LibraryViewModel`, `LikedSongsDetailViewModel` | Projeção efetiva + atualização local consistente; sem usar `spotifySavedAt` como importação. |
| Escrita Spotify ativa via like e auto-save | `LikeCoordinator`, `LikeDestinationDispatcher`, `SpotifyLibraryApiClient`, `SpotifyApiClient.addToLibrary/removeFromLibrary`, `AutoSaveScrobbler`, `StashApplication`, `LikePreferences`, `SettingsAccountsScreen` | Cortar caminhos ativos e bloquear escrita na fronteira Spotify; preservar colunas históricas. |
| Identificador fixo `spotify_liked_songs` e chave única `source_id` | `PlaylistFetchWorker`, `PlaylistEntity`, conta/autenticação (`TokenManager`, `SourceAccountEntity`) | Namespace por conta verificada, migração conservadora, isolamento. |
| PK `(playlist_id, track_id)` não representa ocorrências repetidas | `PlaylistTrackCrossRef`, `PlaylistDao`, `DiffWorker`, `TrackDao`, undo/backup | Evoluir membership para suportar ordem/ocorrências sem duplicar TrackEntity. |
| Room v55 e backups/undo | `StashDatabase`, `DatabaseModule`, schemas exportados, `DatabaseBackupManager`, DAO de undo | Migração não destrutiva, round trip de backup e undo. |
| Modo global remanescente | `StreamingPreference`, `SyncPreferencesManager`, `SyncViewModel`, `HomeViewModel`, `SettingsViewModel`, `LosslessRetryWorker`, `FlacUpgradeSweeper`, Android Auto, Busca/perfil/álbum | Remover dependências somente onde biblioteca/sync/download exigem; manter preferências de qualidade/celular e contratos de áudio. |
| Desconexão cancela downloads por fonte | `SettingsViewModel.onDisconnectSpotify`, `MusicRepository.cancelPendingDownloadsForSource` | Não cancelar pedidos locais legítimos; origem do catálogo não é provedor de áudio. |

## 3. Invariantes de implementação

1. Nenhum request de mutação de biblioteca Spotify pode ser disparado por qualquer caminho Android ativo, inclusive serviço de reprodução, preferências legadas restauradas, auto-save, fila, ações em lote e descurtidas. Remover/neutralizar entrada e fronteira de escrita; não confiar apenas em toggle. Não alterar login de leitura. Conferir todos os call sites, inclusive outros módulos compartilhados; desktop/labs fora da entrega Android, não quebrar sua compilação deliberadamente.
2. Curtida, membership, intenção offline e arquivo são fatos diferentes. Não apagar `TrackEntity` ao descurtir ou remover arquivo. Histórico/lyrics/tags/IDs, relações e fila não devem desaparecer por cascade indevido. Bloqueios explícitos de usuário continuam válidos, sem transformar descurtir em blocklist global.
3. Não adotar correspondência por palavras como identidade definitiva sem validar identificadores e versão. Reutilizar mecanismos existentes de identificação exata, preservar IDs já persistidos; não alterar matching global sem evidência. Dedupe de arquivo respeita versão, formato e arquivo real.
4. Todas as exclusões automáticas exigem prova de ausência de demanda e proteção de arquivos legados/local-importados. Revisar `cleanOrphanedMixTracks`, cascade delete, startup sweep, queue cleanup, undo, backup restore e atualizações lossless. Separar coleta de metadados descartáveis de exclusão física. Na dúvida, manter arquivo. Não usar mudança de `SyncMode` como autorização de exclusão.
5. Operações de sync por coleção aplicam mudança coerente em transação; IO de arquivo fora da transação com revalidação de demanda/path e proteção contra corrida de worker. Cancelamento de WorkManager não conta como sucesso. Nunca uma leitura parcial autoriza limpeza. Usar locks/registry existentes, e ordem consistente de aquisição. Logs diagnósticos sanitizados.
6. Preservar `sourceId` legado quando necessário à compatibilidade e mapear IDs de conta sem cruzar proprietários. Nenhum token/cookie no banco de biblioteca nem backup de credenciais.
7. Uma migração não baixa nem deleta arquivos, não conecta contas e não reinterpreta marca histórica de escrita como curtida de origem. Sem `fallbackToDestructiveMigration` novo. Preferências antigas não voltam a habilitar escrita ou auto-download surpresa após restore.

## 4. Persistência — desenho mínimo necessário

Mapear o esquema e os DAOs reais antes de implementar. A proposta é evoluir o modelo existente, não adicionar um banco paralelo. Os nomes novos abaixo são descritivos, não exigência de nomes finais; mudanças de design que alterem o contrato exigem registrar justificativa.

- **Coleções:** reaproveitar `PlaylistEntity.keepOffline`; separar estado persistente de seleção de sync do estado de presença na biblioteca. `syncEnabled` pode continuar como seleção desde que não mais signifique download. Inventário descoberto não selecionado não é salvo. Status pausado/indisponível precisa manter coleção e vínculos. Para curtidas Spotify, persistir escolha por conta independentemente da existência de snapshot. Namespace da origem por identidade estável da conta, não por cookie. Identidade de conta não confirmada não pode destrutivamente atualizar outra.
- **Curtidas:** usar `STASH_LIKED` + `stashLikedAt` existentes para origem local e membership `LIKED_SONGS` para importada. Persistir ocultação por identidade/faixa e origem/conta (pequena tabela ou campo equivalente que suporte múltiplas origens); não duplicar lista agregada de faixas. Ler projeção efetiva num fluxo/consulta compartilhada. Timestamps remotos passam por snapshot/membership, preservando informação e ordenação. A intenção offline da coleção agregada é persistente e independente de filtros de origem.
- **Downloads:** `isDownloaded/filePath` continuam fato físico; persistir pedido individual (flag/campo dedicado em faixa ou tabela equivalente) e proteção legada. Demanda de coleção deriva de membership ativo e `keepOffline` ou intenção agregada. Reusar `DownloadQueueEntity.userRequested` para execução/telemetria, não como única prova persistente. Definir uma consulta de demanda reutilizada por enqueue, cancelamento e exclusão; preservar estados de downloads falhos e restrições de rede.
- **Ocorrências:** permitir várias ocorrências da mesma faixa em playlist importada, sem duplicar `tracks`. Preferir evolução aditiva/compatível do join (ex.: ordinal/identidade de ocorrência, padrão 0 para vínculos existentes, com chave composta ou identificador estável). Auditar todos os `getCrossRef`, `INSERT REPLACE`, dedupe, atualização de posições, digest/stamp, `removed_at`, backup, undo, contagens e UI key/seleção antes de mudar a PK. Se mudança ampla inviabilizar um PR seguro, documentar bloqueio de fidelidade; não declarar paridade de ordem/duplicatas sem resolver. Curtidas continuam únicas por identidade.
- **Migração Room:** base v55 auditada; criar migração aditiva na próxima versão real disponível, exportar schema e registrar no módulo. Backfill: preservar arquivos existentes, assinalar proteção legada quando intenção individual não for recuperável; preservar pedidos pendentes explicitamente identificados, `keepOffline` existente, seleções anteriores e coleções já salvas. Não converter `syncEnabled=true` em download indiscriminado: recuperar intenção legada de download só quando sustentada por modo anterior/estado e preferências, se incerto preservar arquivos e não enfileirar novas faixas sem consentimento. Preservar a data original. Testar v55 → nova e cadeias antigas suportadas, backup merge/replace e restore de DataStore antigo; não resetar IDs nem `sqlite` destrutivamente. Não excluir arquivos legados mesmo que não haja owner recuperável; só ação explícita do usuário libera proteção.

## 5. Sequência executável — uma entrega, checkpoints internos

### F0 — baseline e proteção imediata
- [ ] Revalidar HEAD, AGENTS, status de features, PRs abertos relevantes, gates e UI rules. Criar uma branch a partir de `nonstop` (ex.: `fix/sonora-library-sync-downloads`). Não misturar branches históricas. Registrar baseline e testes reais/ambiente.
- [ ] Mapear chamadas de Spotify add/remove, limpeza física, modos, filtros, download e backup. Incluir service, startup, scheduled workers, restores e Android Auto. Criar testes que reproduzam defeitos antes do patch quando viável.
- [ ] Desabilitar/remove espelhamento Spotify e auto-save desde os pontos de entrada e fronteira de rede; impedir reativação por preferências antigas. Curtir/descurtir continua local. Aplicar proteção conservadora de arquivos antes de alterar semântica de sync. Testes de ausência de mutações e limpeza.

### F1 — persistência e migração
- [ ] Implementar estados/DAOs definidos na seção 4, migração, schema, backup/restore, undo; namespace de conta, proteção legada e ocorrências. Testes de migração e isolamento. Não apagar mídia.
- [ ] Introduzir projeções explícitas para biblioteca e downloads; rever usos de `getAllTracks/getAllArtists/getAllAlbums/getAllVisible` e filtros de busca dentro da biblioteca. Não expor resultados transitórios. Introduzir estado efetivo de like compartilhado e tests de transição.

### F2 — sincronização somente leitura
- [ ] Ajustar `SpotifyApiClient.getLikedSongs`, `SpotifyTrackParser`, `PlaylistFetchWorker`: verificar esquema/contagens/páginas, retornar informações suficientes para distinguir vazio válido, parcial e erro; detectar página repetida e limite sem excluir; sanitizar diagnóstico. Não substituir por endpoint não verificado nem prometer sessão privada sempre funcional. Preservar login existente; se a API realmente falhar, expor motivo verificável/reconexão sem fabricar resultado.
- [ ] Ajustar `DiffWorker`: seleção desacoplada de download; link de curtidas/playlist sem arquivo; reconciliar snapshot completo com proteção de L, ocultações e outras origens; parcial apenas merge; timestamps/ordem/ocorrências; inventário e coleção vazia; conta/switch/reconexão. Reaproveitar revision stamp somente se confiável; selecionar coleção pela UI antes de snapshot.
- [ ] Ajustar `SyncScheduler`, `SyncPreferencesManager` e estado/diagnósticos: manual/automático, fonte/coleção selecionada, rede, última tentativa e sucesso; preservar comportamento de importação YouTube Music existente salvo contrato compartilhado. Não executar downloads quando não solicitados.

### F3 — propriedade e motor de downloads
- [ ] Centralizar avaliação de demanda: individual OU coleção mantida offline OU agregado de curtidas OU proteção legada. Preservar fontes/arquivos locais; reusar workers, locks, fila, network prefs e resolver existente.
- [ ] `setPlaylistDownload`: não altera `syncEnabled`. Implementar download agregado de curtidas; novos likes/remoções disparam reconciliação de demanda. `removeDownload/removeDownloadsForPlaylist`, cascade e orphans consultam a mesma regra. Cancelamentos e conclusão concorrente testados (incluindo falhas de arquivo/SAF); `clearDownloadState` apenas quando consistente. Nenhum download repetido por ocorrência ou playlist.
- [ ] Não usar Download/Online global como gate de fila, sync ou biblioteca. Preservar qualidade, Wi-Fi, celular, lossless existente e caminhos de streaming. Não tentar baixar tudo em uma única operação de UI; batching fora da main thread e WorkManager.

### F4 — UX/UI, fluxos e acessibilidade
- [ ] Sincronização: um cartão por fonte conectada, último status, curtidas com toggle sempre disponível, playlists com seleção dedicada, atualização automática e ação “Sincronizar agora”. Sem `Refresh/Accumulate`, `Online/Download`, `Sync your likes` Spotify e `Auto-save liked tracks`. Retirar controles inversos/legados da UI sem expor referências ao Stash. Não colocar gerenciamento de downloads nesta tela.
- [ ] Biblioteca: Curtidas (coleção única), Playlists, Músicas/Álbuns/Artistas salvos e filtro Downloads. Preservar ordenação, pin, busca local, acesso a arquivos antigos e Home já aprovada. Não redesenhar barra inferior ou Home inteira.
- [ ] Cabeçalho de curtidas e playlist: download (estado real/progresso, confirmar desligamento), reproduzir, aleatório e ações de conjunto; mesma geometria/tokens existentes. Download por faixa e opções no bottom sheet contextual por pressão longa, sem ícone de ação fixo em cada linha; indicadores de status passivos. Playlist importada: indicação de origem discreta, ação copiar local, e pausa/remover da biblioteca com semântica clara.
- [ ] Player/mini-player/notificação/Android Auto: coração efetivo consistente e somente local; online/offline por conectividade/arquivo. Não alterar arquitetura Media3, ordem da fila ou reprodução exata da Busca sem necessidade. Sem rede, indicar indisponíveis; fila offline coerente.
- [ ] PT-BR/EN com recursos localizados; sem literais ingleses novos visíveis. Grafite, tokens Sonora, áreas táteis, contraste, fontes grandes, TalkBack, gesto alternativo, mini-player, estados de erro/parcial/zero. Não copiar paleta Spotify nem marca como identidade do Sonora. Validar telas reais e componentes compartilhados segundo UI_RULES.

### F5 — validação, integração e APK
- [ ] Atualizar testes antigos que codificam comportamento substituído, sem simplesmente remover cobertura. Testar módulos afetados e suíte exigida pelo workflow canônico. `git diff --check`, `python3 tools/check_sonora_identity.py`, build Debug, manifesto/assinatura/pacote. Verificar comandos/módulos reais no Gradle e workflow, não inventar resultados.
- [ ] Abrir PR para `nonstop`, auditar diff/testes e migração, integrar somente após gates. Atualizar este plano e documentação de consolidação com resultados reais, SHA, testes, limitações. Não publicar APK de branch temporária como entrega consolidada.
- [ ] Disparar workflow `.github/workflows/sonora-preview.yml` para commit integrado conforme AGENTS; verificar artefato e release vinculados ao SHA, link de download. Homologação no aparelho com conta real e biblioteca existente permanece explícita; não declarar sucesso funcional remoto apenas por CI. Se falhar gate crítico, não publicar versão insegura.

## 6. Matriz mínima de aceite (todos pendentes)

| ID | Cenário | Resultado exigido |
|---|---|---|
| S01 | Spotify conectado, curtidas > 50 e > 1.000 | Todas as páginas válidas processadas; contagens e ordem corretas; nenhuma playlist não escolhida baixada. |
| S02 | Primeira sync, apenas curtidas selecionadas | Biblioteca preenchida sem arquivo novo, playlists comuns apenas no inventário. |
| S03 | HTTP/token/schema inválido na primeira página | Erro claro, nenhuma exclusão, reconnect quando aplicável. |
| S04 | Erro na página N / cap / repetição / total incompatível / corpo errors-only | Parcial ou erro, não sucesso pleno; somente adições confirmadas; preservar vínculos e arquivos. |
| S05 | Resposta vazia válida vs vazia suspeita vs parser descarta tudo | Só vazio validado remove vínculos de origem; os demais preservam. |
| S06 | Mesma sync duas vezes / mudança durante paginação | Idempotência; inconsistência não autoriza remoções; sem duplicação. |
| S07 | Adição/removal/rename/reordenação, ocorrências repetidas | Conteúdo remoto correto, arquivo único, ordem e posições distintas; partial não reordena destrutivamente. |
| S08 | Pausar/remover playlist / coleção vazia / desaparece da origem | Comportamento seção 1.3; sem remoção remota, sem ressuscitar por inventário. |
| S09 | Desconectar e reconectar mesma conta, conectar segunda conta | Dados/arquivos preservados e isolamento; nenhuma sobreposição do sourceId fixo. |
| S10 | Sync concorrente/manual+automático, cancelamento | Trabalho único ou serializado; status correto; retomar sem dados truncados. |
| L01 | Local, importada, ambas, descurtir remoto/local, ocultação, re-curtir, undo | Tabela seção 1.2; coração consistente em todas as superfícies. |
| L02 | Mesmo título/artista, versões distintas e IDs diferentes | Não fundir incorretamente; curtidas iguais confirmadas deduplicam. |
| L03 | Biblioteca sem nenhum download | Coleções e faixas salvas aparecem; busca transitória não entra; álbuns/artistas derivados. |
| L04 | Retirar download mantendo faixa salva/histórico | Sem exclusão de TrackEntity ou vínculo; estado muda para não baixada. |
| W01 | Like/unlike player, notification, seleção, fila e histórico repetido | Zero requisições de escrita ao Spotify, mesmo com prefs antigas true/backup restaurado. |
| W02 | Fonte Spotify leitura com login válido | Autenticação, leitura e fotos autorizadas continuam; nenhuma mutação. |
| D01 | Download individual e de playlist/curtidas agregadas | Intenção persistida, arquivo único, progresso real, futuras adições baixam. |
| D02 | Faixa em A e B (ambas offline); desligar A | B conserva arquivo e fila; desligar B libera apenas se sem outro pedido/proteção. |
| D03 | Faixa com pedido individual e de coleção | Remover uma demanda não remove outra. Repetição de faixa em playlist não duplica fila. |
| D04 | Falha, cancelamento, reinício, Wi-Fi, pouco espaço, permissão SAF | Status fiel, retomada, nenhuma exclusão ou retry em loop; respeitar rede. |
| D05 | Desligar download enquanto worker termina, remover/re-adicionar coleção | Sem arquivo ressuscitado sem pedido ou exclusão de arquivo desejado; validação de concorrência. |
| D06 | Sync remove última origem, usuário pausa, startup sweep/undo | Histórico e arquivos protegidos mantidos; limpeza apenas áudio gerenciado sem demanda comprovada. |
| D07 | Arquivos FLAC legados, importados do dispositivo e caminhos compartilhados | Sem perda/conversão/dedup destrutiva; reproduzir arquivos originais. |
| D08 | Faixa não resolvível / download parcial | Não declarar coleção baixada; informar falhas/indisponíveis e retry. |
| M01 | Upgrade com v55 e preferências antigas, dados reais sintetizados | Todos os IDs/relacionamentos/histórico/arquivos preservados, sem download ou escrita surpresa. |
| M02 | Backups antigos e novos, merge/replace, restore settings, undo | Preservar novos estados e compatibilidade; não reativar escrita ou apagar mídia. |
| P01 | Online/offline, queda de rede, retomada player, Android Auto | Reprodução válida conforme disponibilidade, ordem relativa e fila; sem regressão de Busca exata. |
| U01 | Curtidas/playlist header e sync antes/depois de importar | Controles funcionais e sem estados falsos, PT-BR/EN, dark/light, TalkBack, fontes grandes. |
| U02 | 5.000+ faixas, importação e downloads em andamento | UI não bloqueia; listas lazy, batch DB, sem N+1 grave; medir regressão em aparelho quando disponível. |
| R01 | Build, suite existente, identity gate e workflow release | Commit integrado confirmado; APK assinado/pacote preservado, link verificável. |

### Testes existentes a estender (nomes confirmados no repositório)

`SpotifyApiClientPaginationTest`, `SpotifyTrackParserTest`, `DiffWorkerTest`, `DiffWorkerKeepOfflineTest`, `DiffWorkerMixNoDownloadTest`, `DefaultSyncEnabledTest`, `PlaylistDaoMixVisibilityTest`, `PlaylistDaoNeverCheckedVisibilityTest`, `MusicRepositoryQueuePlaylistTest`, `MusicRepositoryPlaylistDownloadTest`, `LikeCoordinatorTest`, `NowPlayingViewModelTest`. Incluir testes novos de migração, snapshots vazios/parciais Spotify, origem efetiva, múltiplas contas, ocorrências, demanda e concorrência. Não assumir que esses testes existentes já cobrem o novo comportamento.

## 7. Escopo e decisões propostas que não vieram explicitamente do usuário

São escolhas conservadoras para viabilizar a execução sem microaprovações; registrar divergência se houver evidência nova, não ampliar escopo por conta própria.

- Pausar atualização mantém cópia; excluir da origem mantém coleção local marcada indisponível, até decisão explícita. Descurtir importada localmente oculta sem enviar ao Spotify; um novo ciclo remoto confirmado pode reexibir. Não há alteração remota.
- Preservar a importação YouTube Music e demais funcionalidades locais existentes; por coerência com “curtidas exclusivamente no Sonora”, ações de coração não devem iniciar novo espelhamento externo, inclusive YTM. Não retirar importação YTM nem outras funções independentes. Se um toggle YTM tiver sido explicitamente desejado anteriormente, registrar conflito e não ampliar mudança de escrita YTM além de tornar coração local; prioridade é zero escrita Spotify.
- Novas instalações: curtidas Spotify habilitadas, playlists/mixes não selecionados, download desligado. Instalações existentes: preservação acima da limpeza automática. Arquivos legados sem origem/intenção comprovada só são removidos explicitamente.
- Ordenação e repetição em playlists são requisito de fidelidade; não usar título/autor como dedupe destrutiva. Contas antigas são preservadas. Não fazer paridade completa de recursos do Spotify (colaboração, edição remota, áudio, recomendações).
- Não obrigar novo backend ou login diferente. A falha específica de importação da conta real permanece desconhecida; diagnóstico do telefone é gate de homologação, não justificativa para inventar resposta ou burlar autenticação.

## 8. Fechamento e registro de execução

- [ ] F0 — proteção e baseline
- [ ] F1 — persistência e migração
- [ ] F2 — sync somente leitura
- [ ] F3 — downloads independentes
- [ ] F4 — UX e integração de superfícies
- [ ] F5 — testes, integração e APK

**Regra:** não marcar como concluído com base apenas em relato do executor. Registrar arquivos, commit, resultado de cada teste, número/URL do PR, SHA integrado e release, e itens bloqueados. Proibido declarar homologação em aparelho sem teste real. Se acesso à execução local/CI ou sessão real não estiver disponível, informar precisamente o que está bloqueado e entregar alterações/testes possíveis sem fingir conclusão.

**Registro inicial:** auditoria estática; HEAD confirmado; nenhuma alteração de código, PR, migração executada, teste ou APK gerado por este plano. A causa específica da falha de curtidas na conta do usuário não foi confirmada. Plano completo para implementação, não confirmação de funcionalidade.

## 9. Checkpoint de execução — 25/11/2026

Branch `fix/sonora-library-sync-downloads`, base `c2f54e5bdfd9661b3792620767c2c290032da280`. Alterações em código: `LikeCoordinator` exclusivamente local; inicialização de auto-save removida; opções de espelhamento retiradas da tela Contas; `SpotifyApiClient` rejeita mutações antes de rede/autenticação; `getLikedSongs` rejeita erros GraphQL e vazios sem contagem coerente; fetch de curtidas detecta duplicatas, perda de itens no parser e inconsistências de contagem, marcando partial e erro diagnóstico, mantendo adições confirmadas elegíveis para diff. Testes de regressão escritos, ainda não executados. F0 e F2 apenas parciais; F1, F3, F4 e F5 pendentes (exceto remoção dos controles de escrita em Contas). Não declarar importação corrigida em conta real nem autorizar merge/release. A execução local foi bloqueada por ausência de acesso Git direto e toolchain Android. Não houve alteração de schema nem exclusão de arquivos. Próximo trabalho deve continuar nesta mesma branch, fechar todos os itens restantes e executar gates antes de integrar na `nonstop`.


## 10. Checkpoint — separação de biblioteca e downloads

PR [#117](https://github.com/FernandoDevCode/N-STOP/pull/117), branch de trabalho existente. Alterações adicionais (ainda não integradas):
- Spotify: curtidas habilitadas por padrão quando a coleção ainda não existe; playlists descobertas entram no catálogo sem leitura integral até seleção. Importação de playlists pessoais/curtidas não autoriza downloads. Desconectar conta não cancela downloads locais.
- Download: keepOffline governa a coleção pessoal Spotify; limpeza da fila preserva pedidos explícitos e coleções offline. Remoção em lote conserva arquivos locais e faixas compartilhadas com outras coleções, além de verificar jobs ativos. Proteção conservadora, não substitui persistência completa de propriedade.
- Biblioteca: projeção de faixas salvas ou baixadas e agrupamentos de artistas/álbuns; não inclui automaticamente stubs de busca; curtidas agregadas deduplicadas por ID local, com controle de download das coleções existentes. Remover curtida por origem não apaga áudio ou TrackEntity; confirmação localizada PT-BR/EN.
- Testes de regressão acrescentados/atualizados para seleção Spotify, projeção de biblioteca, download independente, arquivos locais compartilhados e remoção de curtidas. Não marcar como aprovados sem resultado do CI.
- CI do checkpoint anterior (272205ed466f409160b989fda4b98b1260ce86d3): gate de identidade, build Debug e demais shards reportados aprovados; features falhou na obtenção de artefato Robolectric (MavenArtifactFetcher). Gate de identidade também passou em 4251bbf745255f061be48af265427ae980aaf0a4; testes desse SHA ainda enfileirados na consulta. Nenhum desses resultados valida os commits posteriores.

Pendências de aceite: migração/persistência de propriedade offline individual e agregada, isolamento de conta Spotify, reconciliação remota segura (incluindo vazio confirmado, remoção/recurtida), ocorrências e ordem de playlist, toda a simplificação da UI de sincronização e estados, testes de integração/upgrade e execução completa do CI. A escolha provisória conservadora ao desativar download é cancelar pedidos ainda não iniciados e manter arquivos existentes; remoção física só explícita e protegida. A coleção agregada ainda depende das origens já existentes ao ligar o botão. Não declarar paridade com Spotify nem liberar APK antes dos gates. Nenhuma alteração de schema ou operação em dados de aparelho nesta etapa.


## 11. Checkpoint — segurança de reconciliação e seleção

Até `87cc26c874af938f696f4da6304d606c7d50a9d7` no PR #117:
- Importação desativada pausa novas leituras e vínculos Spotify mesmo se keepOffline estiver ligado; downloads da cópia existente continuam independentes.
- Resposta de curtidas confirmadamente vazia produz snapshot vazio; erro não produz snapshot vazio. O modo de reconciliação existente continua respeitado; não há remoção automática em modo Accumulate.
- Contagem de coleções pessoais Spotify em snapshots completos usa vínculos ativos. Limpeza de órfãos preserva faixas de origem Spotify, URI Spotify conhecida, marcas de curtida e pedidos de download explícitos ainda registrados. Não equivale a propriedade offline durável: metadados de origem e filas antigas podem ser incompletos.
- Textos PT-BR/EN esclarecem seleção de importação, download na coleção e leitura sem escrita remota. Controles legados de modo permanecem enquanto sua semântica ainda afeta outras coleções.
- Testes adicionados/ajustados para zero confirmado vs erro, proteção de áudio órfão e pausa de importação independente de keepOffline. Não executados nesta sessão.

CI consultado: em `9be9903105edf28c0ddb4e5a5675ccb27d7bca14`, identidade aprovada, testes ainda enfileirados (run 45726244532). Em `7b011d978c0925b121ba031d80d6c464dec1428b`, workflows ainda não apareciam. Sem validação do HEAD atual, sem merge e sem APK publicado. Permanecem pendentes propriedade offline durável/migração, isolamento por conta e reconciliação completa de remoções/recurtidas, ordem/ocorrências, simplificação integral da interface, homologação. Não liberar antes dos gates.


## 12. Checkpoint — propriedade persistente dos downloads

Até `aff972e2140a9d574ccfaca1fc6f14a71e9bdbeb` no PR #117:
- Room v56 e migração aditiva v55→56: `tracks.offline_requested`. Arquivos legados são protegidos como propriedade individual conservadora; pedidos explícitos não cancelados são preservados. Nenhuma exclusão física ou novo download na migração.
- Download manual concluído persiste propriedade antes da limpeza de histórico; a fila também continua protegendo pedidos em curso. Busca, mapeadores e importação de backups foram adaptados. Remover download individual libera sua intenção, mantendo o arquivo quando uma coleção ainda o exige.
- Desativar download de coleção pessoal Spotify/local libera arquivos novos sem outro proprietário, preservando importações locais, pedidos individuais, coleções offline e arquivos legados protegidos. Não esquece registro se a exclusão física falhar. Job já em execução pode terminar e deixar arquivo preservado; não há cancelamento destrutivo de transferência ativa nesta etapa.
- Testes adicionados para migração, persistência após limpeza de histórico e preservação/liberação de arquivos compartilhados. Smoke test SQL isolado em SQLite aprovado para backfill de propriedade e limpeza de histórico (fixture mínimo, não valida Room/Android).
- Falta exportar e versionar o schema Room gerado `56.json`, rodar migração e testes completos no CI. A consulta anterior ao checkpoint ainda mostrava testes enfileirados. Sem merge/release.
- Pendências reais: isolamento de contas Spotify, reconciliação completa/removals e recurtição, ocorrências/ordenação, revisão de corridas e ações de UI (incluindo mensagens quando um arquivo fica por outra coleção), downloads de origens legadas e simplificação integral de modos. Não marcar F1/F3/F5 como concluídas nem liberar APK antes dos gates.


## 13. Checkpoint — correções das falhas reais do CI

Run 45731963047 em `f503251e1c45caa5944bbb8beab0673442daeeb3`: build debug, compilação dos testes e gate de identidade aprovados; 10 testes falharam (7 engine, 2 features, 1 foundations). Diagnóstico confirmado por logs e relatório XML de autenticação.

Alterações até `e3b7321b145102a8c4553c8a99c0f348b2bc9809`:
- Política compartilhada `LibraryPlaybackPolicy` aplicada às telas de músicas, curtidas, playlists, álbuns e artistas e ações manuais de fila. Conectividade validada e preferência de dados móveis determinam elegibilidade; não alteram vínculos salvos. Faixas locais usam metadados de download e o player valida o arquivo quando o abre. Uma faixa tocada que não é elegível não substitui silenciosamente por outra. Testes online/offline/celular e reprodução de álbum salvos sem áudio.
- Recuperação de filas legadas: reencaminha pedidos explícitos ou de coleções mantidas offline para discovery, sem tocar em IN_PROGRESS; cancela pendentes sem autorização. Sincronização manual não força rede móvel para o download automático das coleções. Teste real de DAO adicionado.
- Testes de artistas, álbuns e importação agora distinguem pertencimento à biblioteca de disponibilidade de áudio; cobrem stubs não salvos, membros indisponíveis, playlist não selecionada e curtidas selecionadas.
- Migração v55→56: teste abre arquivo v55 com implementação Room gerada, valida versão, dados e vínculos. CI passa a exportar o schema gerado em artefato `generated-room-schema-v56`; ainda é necessário baixar, versionar `56.json` e verificar CI/migração reais. Nenhum hash de schema inventado.
- Auth: XML de TokenManagerRefreshSingleFlightTest confirma CompletionHandlerException durante cancelamento de ProducerCoroutine no Default após resetMain do teste anterior. Adicionado awaitClosed para aguardar shutdown dos observadores; sem mudança de token, refresh ou cookie.
- Todas as alterações permanecem no PR #117. Não validadas pelo CI ainda. Não declarar a release pronta. Pendências anteriores de isolamento de conta, reconciliação remota completa, intenção offline agregada, ordem/ocorrências e UI permanecem.


# Sonora — especificação vigente e contrato do rework da Busca

**Status:** Busca universal integrada pelo PR #95; reprodução exata pelo PR #97. A V217 apresenta os resultados em categorias e concentra a configuração das fontes em um painel.

**Data da consolidação:** 10/11/2026

**Repositório:** `FernandoDevCode/N-STOP`

**Destino canônico:** `nonstop`

**Autoridade:** este documento prevalece, para a Busca, sobre planos históricos e experimentais. `AGENTS.md` e `docs/SONORA_CONSOLIDATION.md` continuam prevalecendo nas regras gerais do repositório.

## Revisão estrutural pós-V217

A V217 foi integrada pelo PR #104. A revisão abaixo atualiza os contratos de fontes, recuperação e ranking descritos nos checkpoints V217 deste documento. Os controles de pin e restauração, histórico, identidade de catálogo e fronteiras de reprodução continuam aplicáveis.

- O Spotify fornece os IDs, títulos, créditos e edições do catálogo musical pela API de busca já autorizada. Uma solicitação inicial cobre faixas, artistas e lançamentos, com até dez entradas por tipo. O fim de uma lista musical solicita a próxima página somente para aquele tipo. O estado da consulta guarda páginas e erros por tipo e mantém resultados já recebidos em falhas de continuação. O cliente respeita `Retry-After` entre consultas, páginas e enriquecimento de capas.
- Uma falha do Spotify inicia o fallback YouTube Music. Se a resposta Spotify omitir tipos, o fallback cobre apenas esses tipos. Uma lista de itens vazia e válida é uma resposta concluída. O painel mantém apenas a seleção musical Spotify. Preferências fixadas antigas mantêm a escolha musical, incluindo o alias YouTube Music.
- Top, Artistas, Álbuns e Músicas usam entidades de catálogo por tipo. Álbuns incluem singles com o tipo de lançamento do provedor. YouTube WEB permanece no chip YouTube, com a chamada independente da mesma consulta já usada na V217. Um resultado Spotify com equivalente no YouTube aparece em sua categoria musical com o ID do Spotify.
- O ranking usa faixas ordenadas de evidência. Título com crédito explícito indica intenção mais forte que um título coincidente. IDs de crédito corroboram artistas exatos ou parciais. Correspondências equivalentes, prefixos, termos, participação e vínculo com lançamento ajudam a ordenar os demais candidatos. A posição no provedor e a chave estável resolvem empates. A pontuação ordinal do YouTube WEB preserva sua ordenação. Resultados legítimos de baixa relevância permanecem disponíveis.
- Um toque em faixa, álbum ou artista Spotify só usa um destino YouTube Music quando a resolução encontra um candidato único com os metadados exigidos para aquele tipo. A faixa verifica créditos, edição, duração e explicitude. O lançamento verifica os créditos, o ano e o tipo. O histórico armazena o destino de navegação separadamente da identidade Spotify. A reprodução de vídeo YouTube WEB continua enviando seu próprio ID ao fluxo exato.

As seções abaixo registram o contrato e a integração até a V217.

## 1. Registro da preparação

O checkout auditado estava em `codex/search-checkpoint-0`, no commit `0ded5dbad41c967f603fe80f5cb14c4986718cff`. Na auditoria, `nonstop`, o merge-base e o HEAD apontavam para o mesmo commit; o remoto também declarava `nonstop` como branch padrão. A branch de trabalho não substitui a regra canônica: os futuros branches/worktrees devem nascer da `nonstop` atualizada e os PRs devem ter base `nonstop`.

Estado inicial observado:

```text
## codex/search-checkpoint-0
 M feature/search/src/main/kotlin/com/stash/feature/search/SearchScreen.kt
 M feature/search/src/main/kotlin/com/stash/feature/search/SearchViewModel.kt
 M gradle.properties
?? docs/sonora/SEARCH_REWORK_PLAN.md
?? tools/sonora-emulator-smoke.ps1
?? tools/sonora_emulator_capture.ps1
```

As alterações locais preexistentes não pertencem a esta preparação e não podem ser descartadas, sobrescritas ou incorporadas automaticamente. Em especial, preservar sem edição:

- `gradle.properties`;
- `tools/sonora-emulator-smoke.ps1`;
- `tools/sonora_emulator_capture.ps1`;
- as alterações Kotlin já presentes em `SearchScreen.kt` e `SearchViewModel.kt`.

Este trabalho documental só pode alterar `AGENTS.md` e este arquivo. Não cria worktrees, não implementa a Busca e não faz commit, push ou merge.

## 2. Decisões de produto consolidadas

1. A Busca é **universal**. Uma consulta produz uma coleção ordenada pelo motor; a apresentação V217 deriva localmente Top, Artistas, Álbuns, YouTube e Músicas.
2. Há duas opções de catálogo, **Spotify e YouTube**, ambas ativas por padrão. Spotify consulta o catálogo YouTube Music e preserva sua identidade. YouTube consulta o catálogo WEB independente.
3. Um botão junto ao campo abre o painel de fontes com as duas opções, persistência e restauração. Os chips de categoria aparecem quando a consulta contém texto.
4. Ao fixar a seleção, o conjunto atual é salvo imediatamente e atualizado a cada alteração. A seleção temporária dura a sessão atual.
5. “Restaurar padrão” fica no painel e é acionável quando a seleção difere de `{SPOTIFY, YOUTUBE}`.
6. A Busca musical funciona sem sessão Spotify. O Spotify fornece somente imagens e metadados usados para verificação visual. Sua indisponibilidade conserva as imagens originais.
7. A proveniência de cada resultado está disponível nas opções por pressão longa e na semântica da linha.
8. Vídeos raros, demos, covers, lives, remixes e versões escolhidas pelo usuário são resultados legítimos. Não substituir silenciosamente um vídeo selecionado por uma gravação “canônica” e não deduplicar agressivamente versões alternativas.
9. A seleção de fontes visíveis na Busca não controla nem descreve, por si só, a infraestrutura interna usada posteriormente para resolver áudio. Fonte de catálogo e fonte/ID de áudio são identidades diferentes.
10. Mini-player, bottom navigation, perfis de artista e identidade visual Sonora são preservados. Este rework não redesenha chrome global nem perfis.
11. O mecanismo de reprodução, Media3, downloads, fila e resolução de stream não mudam nesta tarefa. A Busca apenas entrega identidades separadas e aciona as fronteiras existentes.
12. A busca do YouTube convencional usa InnerTube WEB independente, sem YouTube Data API v3, NewPipeExtractor ou yt-dlp na pesquisa. O cliente e o adaptador já alimentam a Busca universal e o toque aciona a reprodução exata quando há um ID elegível.
13. Uma sessão temporária da Busca dura enquanto a mesma instância de navegação da Busca existir. A seleção não fixada é conservada ao abrir artista/álbum e voltar para essa instância; é descartada ao iniciar uma nova instância/sessão da Busca. A seleção fixada é restaurada em qualquer sessão futura.

## 3. Escopo e não escopo

### Incluído

- consulta universal e agregação de provedores;
- filtros por fonte, alfinete e persistência da preferência;
- estados independentes por fonte e falha parcial;
- modelo visual único e histórico de pesquisas;
- ordenação, identificação conservadora de duplicatas e contrato UI/motor;
- Compose da Busca e dados simulados aderentes ao contrato;
- investigação e, depois de aprovada, integração de catálogo do YouTube convencional.

### Excluído

- mudança no player, Media3, extração de áudio, download, autenticação, mini-player, navegação global ou perfis;
- substituição automática de versões, canonicalização de escolhas ou deduplicação sem identidade forte;
- novas funcionalidades de recomendação, charts, personalização ou suporte a novos tipos de resultado;
- exceções por artista, álbum, faixa ou serviço;
- uso de filtros de exibição como configuração do resolvedor de áudio.

## 4. Contrato compartilhado entre Motor e Interface

Os nomes abaixo são normativos. A implementação Kotlin pode acrescentar detalhes mecânicos, mas não alterar sem aprovação os significados, defaults ou transições.

### 4.1 Fontes e tipos

```kotlin
enum class CatalogSource {
    SPOTIFY,
    YOUTUBE,
    YOUTUBE_MUSIC,
}

enum class SearchResultType {
    TRACK,
    VIDEO,
    ARTIST,
    ALBUM,
}

```

- `TRACK` representa uma faixa de catálogo; `VIDEO` preserva a identidade de vídeo e suas versões; `ARTIST` e `ALBUM` abrem as rotas já suportadas quando houver uma identidade navegável.
- Não converter `VIDEO` em `TRACK`, nem trocar seu ID, para favorecer uma versão canônica.
- Uma fonte pode não suportar todos os tipos. Ausência de suporte não autoriza inventar resultados nem mudar o tipo retornado.

### 4.2 Seleção de fontes e persistência

`SearchSourceSelection.enabled` guarda os filtros visuais com os nomes já persistidos. `activeCatalogs` deriva o conjunto de catálogos reais.

| Filtro visual | Catálogo consultado |
| --- | --- |
| Spotify | YouTube Music |
| YouTube | YouTube WEB |

Spotify visual ativo gera uma consulta para YouTube Music, enquanto YouTube visual ativo consulta YouTube WEB de forma independente. Desativar Spotify cancela somente a consulta YouTube Music e seu enriquecimento. Alterar YouTube não muda o ID nem o estado do catálogo musical; identificação, navegação e histórico permanecem vinculados à fonte real. Seleções fixadas antigas contendo `CatalogSource.YOUTUBE_MUSIC` são normalizadas para `CatalogSource.SPOTIFY` na leitura e na gravação, sem migrar identidades de resultados.

```kotlin
data class SearchSourceSelection(
    val enabled: Set<CatalogSource>,
    val isPinned: Boolean,
)

val DefaultSearchSources = setOf(
    CatalogSource.SPOTIFY,
    CatalogSource.YOUTUBE,
)
```

Regras de transição:

- início sem preferência fixada: `enabled = DefaultSearchSources`, `isPinned = false`;
- somente Spotify/YouTube são alternáveis pela UI; consultas são iniciadas ou canceladas quando o conjunto de catálogos efetivos muda;
- seleção vazia é permitida; com consulta ativa mostra orientação para ativar ao menos uma fonte, sem disparar provedores. Com consulta vazia, mostrar somente histórico ou prompt, nunca o controle de fontes;
- ao ativar o alfinete, persistir imediatamente o conjunto atual e `isPinned = true`;
- enquanto fixado, toda mudança de filtro atualiza a preferência persistida;
- ao desativar o alfinete, manter a seleção atual apenas durante a sessão e persistir `isPinned = false`; na próxima sessão, usar o padrão;
- navegar para um resultado e voltar à mesma instância da Busca conserva a seleção temporária em memória; criar uma nova instância da Busca inicia outra sessão e usa o padrão, salvo seleção fixada;
- “Restaurar padrão” define explicitamente `{SPOTIFY, YOUTUBE}`, disponível durante consulta ativa apenas para escolha diferente do padrão. Se fixado, atualiza o valor persistido; se temporário, altera somente a sessão atual;
- não inferir seleção a partir de login, disponibilidade, erro ou infraestrutura de áudio. Uma fonte selecionada pode estar `unavailable`.

### 4.3 Identidades separadas

```kotlin
data class CatalogIdentity(
    val source: CatalogSource,
    val catalogId: String,
    val catalogUri: String? = null,
)

data class AudioIdentity(
    val provider: String,
    val audioId: String,
)
```

- `CatalogIdentity` é obrigatória e determina a origem exibida e a navegação.
- `AudioIdentity` é opcional. Ela só existe quando já é conhecida com segurança; não é derivada pela Interface.
- IDs Spotify, YouTube e YTM nunca são gravados no campo uns dos outros.
- Resolver áudio depois de uma ação não muda título, arte, fonte, tipo ou `catalogId` do item escolhido.
- Se o áudio não puder ser resolvido pela fronteira existente, a ação falha claramente; não toca outro vídeo ou versão por aproximação silenciosa.

### 4.4 Modelo visual único

```kotlin
data class UniversalSearchResult(
    val identity: CatalogIdentity,
    val audioIdentity: AudioIdentity? = null,
    val type: SearchResultType,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val isExplicit: Boolean,
    val versionLabel: String?,
    val providerRank: Int,
    val rankingScore: Double,
    val supportedActions: Set<SearchResultAction>,
)

enum class SearchResultAction {
    PLAY,
    PLAY_NEXT,
    ADD_TO_QUEUE,
    START_RADIO,
    ADD_TO_PLAYLIST,
    DOWNLOAD,
    NAVIGATE,
}
```

- `stableKey` é uma propriedade derivada de fonte + tipo + `catalogId`; não é fornecida pelo chamador e nunca usa posição na lista.
- `rankingScore` é normalizado pelo Motor para `0.0..1.0`; a Interface não recalcula relevância.
- `versionLabel` conserva qualificadores recebidos, como live, demo, cover ou remix; não é usado para esconder versões.
- Campos ausentes permanecem nulos e recebem tratamento visual neutro. A Interface não fabrica metadados.
- O mesmo modelo alimenta dados reais, fixtures, previews e testes do Codex Visual.
- `supportedActions` é a fonte única de disponibilidade. A Interface só exibe/habilita uma ação presente no conjunto; ausência significa que a integração correspondente não existe ou não está comprovada para aquele resultado.

### 4.5 Estado por fonte e estado agregado

```kotlin
sealed interface SourceSearchState {
    data object Idle : SourceSearchState
    data class Loading(
        val query: String,
        val previous: List<UniversalSearchResult> = emptyList(),
        val isRetry: Boolean = false,
    ) : SourceSearchState
    data class Data(val query: String, val items: List<UniversalSearchResult>) : SourceSearchState
    data class Empty(val query: String) : SourceSearchState
    data class Unavailable(val query: String, val reason: UnavailableReason) : SourceSearchState
    data class Error(val query: String, val message: String, val canRetry: Boolean) : SourceSearchState
}

enum class UnavailableReason {
    AUTHENTICATION_REQUIRED,
    NOT_IMPLEMENTED,
    SERVICE_DISABLED,
}

data class UniversalSearchUiState(
    val query: String,
    val selection: SearchSourceSelection,
    val sourceStates: Map<CatalogSource, SourceSearchState>,
    val orderedResults: List<UniversalSearchResult>,
    val recentSearches: List<RecentSearchItem>,
    val artworkOverrides: Map<String, String>,
)
```

Semântica:

- `idle`: consulta ainda não elegível ou fonte não consultada;
- `loading`: consulta em andamento; `previous` só pode ser não vazio em retry explícito da mesma `query` (`isRetry = true`);
- `data`: um ou mais itens válidos;
- `empty`: a fonte respondeu sem itens;
- `unavailable`: a consulta não pode ser feita nas condições atuais, por exemplo quando o serviço está desabilitado;
- `error`: tentativa falhou; contém retry somente quando seguro.

Cada catálogo efetivamente selecionado evolui de forma independente. Uma fonte indisponível ou com erro não derruba dados das outras. A lista universal contém resultados `data` das fontes bem-sucedidas; a UI mostra aviso discreto e ação por fonte para falhas parciais. Só existe erro de página inteira quando nenhuma fonte selecionada fornece dados e pelo menos uma falha; só existe vazio global quando todas as fontes selecionadas concluíram como `empty`. Um catálogo é desativado quando nenhum filtro marcado aponta para ele.

Todos os estados não ociosos carregam a consulta que os produziu. `UniversalSearchUiState` só aceita estados cuja `query` coincide com sua consulta atual. Ao mudar a consulta, resultados anteriores são removidos da lista universal; eles não podem ser publicados como dados nem como `Loading.previous` da nova consulta. Reutilização de `previous` é exclusiva de retry da mesma consulta.

Cada item de `Data.items` ou `Loading.previous` deve pertencer à `CatalogSource` usada como chave daquele estado em `sourceStates`. Cada item de `orderedResults` deve ser integralmente igual a um item visível do estado da mesma fonte; igualdade apenas de `stableKey` não basta e não autoriza trocar identidade, tipo, título, áudio ou ações no agregado.

### 4.6 Ordenação universal

O motor entrega `orderedResults` em ordem global. A interface usa essa ordem para escolher o destaque e mantém a ordem relativa de cada categoria.

1. ordenar por `rankingScore` decrescente;
2. em empate, preservar `providerRank` crescente;
3. persistindo o empate, usar a ordem estável `YOUTUBE`, `YOUTUBE_MUSIC` e depois `stableKey`;
4. manter uma única coleção ordenada no motor;
5. chegada tardia de uma fonte pode recalcular a lista, mas itens com a mesma identidade e score mantêm desempate determinístico.

A normalização de relevância entre provedores precisa de teste com consultas representativas antes de congelar pesos. O contrato de mocks deve incluir scores variados, mas não transforma pesos simulados em evidência de qualidade real.

### 4.7 Duplicatas e versões

- Remover automaticamente apenas repetição do mesmo `(source, type, catalogId)` na mesma resposta.
- Resultados de fontes diferentes permanecem separados na primeira entrega, mesmo quando título e artista parecem iguais.
- Título, artista, duração aproximada ou artwork não bastam para colapsar itens.
- Um eventual detector pode marcar candidatos a duplicata para telemetria/teste, mas não escondê-los sem identidade forte e aprovação de produto.
- Demos, covers, lives, remixes, remasters, edits, versões acústicas e vídeos raros permanecem visíveis e selecionáveis.

### 4.8 Imagens e histórico

`artworkOverrides` contém somente URLs de apresentação indexadas pela chave estável do resultado. As imagens do catálogo são publicadas primeiro. O enriquecimento resulta em atualização visual, preservando as identidades, a ordem, as ações e os metadados usados para navegação.

- **Faixas YouTube Music:** título e artista completos, incluindo qualificadores, lançamento explícito, duração e marcação de conteúdo quando disponíveis. O Spotify precisa oferecer uma correspondência única.
- **Álbuns YouTube Music:** título completo, artista, ano e tipo compatível. Versões disputadas ou metadados insuficientes conservam as capas de origem.
- **Fotografias de artistas:** escolha manual, foto Spotify validada e imagem original, conforme o repositório compartilhado. O perfil continua responsável por verificar a referência via `SpotifyArtistPhotoLookup` e por procurar a mesma fotografia em resolução superior.
- **Vídeos:** conservam o thumbnail de origem. Quando ausente, usam o thumbnail do ID de vídeo exato, se válido.
- **Orçamento:** uma consulta Spotify de capas por vez para resultados elegíveis visíveis. Cada resultado é tentado uma vez por consulta ativa, e a troca de consulta cancela o trabalho anterior. Cache em memória de até 256 entradas, com 24 horas para acertos, 30 minutos para ausência de correspondência e espera curta para falhas transitórias. Resposta 429 respeita `Retry-After`.
- **Abertura de álbum:** navegação e histórico levam a capa exibida na hora do toque, com o ID original do YouTube Music.


```kotlin
data class RecentSearchItem(
    val id: String,
    val kind: RecentSearchKind,
    val label: String,
    val subtitle: String?,
    val resultIdentity: CatalogIdentity?,
)

enum class RecentSearchKind { QUERY, RESULT }
```

- preservar a capacidade atual de registrar consulta submetida e resultado aberto;
- manter limite, ordem recente, remoção individual e limpar tudo já existentes;
- deduplicar consultas por texto normalizado e resultados por `CatalogIdentity`, sem misturar fontes por título;
- tocar uma consulta restaura o texto; tocar um resultado usa sua identidade original;
- o histórico não persiste `AudioIdentity` resolvida e não altera filtros ou alfinete.

### 4.9 Ações e callbacks oferecidos à Interface

```kotlin
interface UniversalSearchActions {
    fun onQueryChanged(query: String)
    fun onSearchSubmitted()
    fun onClearQuery()
    fun onSourceToggled(source: CatalogSource)
    fun onPinToggled()
    fun onRestoreDefaultSources()
    fun onRetrySource(source: CatalogSource)
    fun onVisibleResultsChanged(query: String, stableKeys: Set<String>)
    fun onResultAction(
        result: UniversalSearchResult,
        action: SearchResultAction,
        displayedArtworkUrl: String? = null,
    )
    fun onRecentSelected(item: RecentSearchItem)
    fun onRecentRemoved(item: RecentSearchItem)
    fun onRecentCleared()
}
```

- A Interface não chama clientes de rede, DataStore ou resolvedores diretamente.
- `onResultAction` só é executável quando `action in result.supportedActions`; preserva a identidade escolhida e delega à fronteira existente.
- A interface V217 oferece as ações conectadas nesta rota: reprodução exata, navegação e consulta aos metadados do item.
- A reprodução de resultados elegíveis percorre `PlayerRepository.playExactSearchResult` pelo fluxo integrado.
- As fixtures compartilhadas fornecem somente DTOs e estados puros. A implementação fake de `UniversalSearchActions`, com no-ops ou gravação de eventos, é responsabilidade do Codex Visual em seu componente isolado.

## 5. Comportamentos de referência para dados simulados

O Codex Visual deve conseguir demonstrar, sem integração real:

- consulta vazia com e sem histórico;
- dois filtros ativos (Spotify e YouTube), com resultados reais do YouTube Music e YouTube WEB independentes e sem terceiro chip;
- duas fontes em `loading` e uma com dados anteriores;
- dados intercalados de faixa, vídeo, artista e álbum;
- falha parcial com resultados preservados;
- vazio global e seleção vazia;
- filtros temporários, alfinete ativado/desativado e restauração explícita;
- artwork ausente, texto longo, explícito e versões live/demo/cover/remix;
- mini-player presente/ausente e bottom navigation preservada.

Fixtures não podem ser apresentadas como prova de integração, relevância, latência ou viabilidade de reprodução.

Fixtures do YouTube convencional servem somente para renderização e invariantes do DTO. Elas não simulam cliente, rede, autenticação, disponibilidade ou reprodução e não permitem declarar a fonte pronta.

## 5.1 Compatibilidade temporária durante o paralelo (histórico)

- O Motor implementa componentes isolados e não quebra nem substitui a `SearchScreen` atual durante seu workstream.
- O Visual implementa um componente Compose isolado, alimentado exclusivamente pelos DTOs, callbacks e fixtures compartilhados; não depende do novo Motor nem altera o `SearchViewModel` atual.
- A `SearchScreen`/`SearchViewModel` atuais permanecem compatíveis até a etapa de integração.
- A conexão definitiva entre Motor e Visual acontece somente após os dois workstreams, na ordem da seção 7.
- O contrato usa um único tipo para cada conceito. Não criar enums de fase paralelos ao sealed state, modelos duplicados de seleção/resultado ou adapters que representem exatamente o mesmo estado sem necessidade de fronteira.

## 6. Divisão de trabalho da integração inicial (histórico)

Antes de abrir os dois worktrees de implementação, materializar este contrato em um pequeno **bootstrap comum** na `nonstop`: somente DTOs/interfaces/fixtures-base acordados, sem rede ou UI. Os dois worktrees devem nascer do mesmo SHA após esse bootstrap. Esses arquivos ficam congelados durante o paralelo; qualquer mudança exige acordo explícito e atualização deste documento. Isso evita que os agentes criem cópias incompatíveis do contrato.

### A — MOTOR

Responsável por APIs, provedores, catálogo, agregação, ordenação, matching conservador, filtros, persistência e performance.

Pode alterar/criar:

- `data/spotify/src/main/kotlin/com/stash/data/spotify/**` e testes correspondentes, apenas para busca de catálogo;
- o local aprovado após o spike para o cliente de YouTube convencional e seus testes;
- `data/ytmusic/**` apenas quando necessário para adaptar resultados ao contrato, sem mudar perfis ou reprodução;
- `feature/search/src/main/kotlin/com/stash/feature/search/engine/**`;
- `feature/search/src/main/kotlin/com/stash/feature/search/SearchViewModel.kt`;
- `feature/search/src/main/kotlin/com/stash/feature/search/SearchSourcePreferences.kt`;
- testes JVM do Motor em `feature/search/src/test/**` com nomes `*EngineTest`, `*RankingTest`, `*PersistenceTest` ou `SearchViewModelTest`.

Não pode alterar `SearchScreen.kt`, componentes Compose, strings visuais, player, Media3, downloads, navegação ou perfis.

### B — INTERFACE

Responsável por Compose, campo, painel de filtros, alfinete, lista universal, linhas, estados, animações e UX.

Pode alterar/criar:

- `feature/search/src/main/kotlin/com/stash/feature/search/SearchScreen.kt`;
- `feature/search/src/main/kotlin/com/stash/feature/search/SongRow.kt` somente para a apresentação universal, sem mudar ações;
- `feature/search/src/main/kotlin/com/stash/feature/search/ui/**`;
- `feature/search/src/main/kotlin/com/stash/feature/search/fixtures/**`;
- `feature/search/src/main/res/values/strings.xml` e `values-pt-rBR/strings.xml`, somente chaves da Busca;
- testes Compose/semântica/captura da Busca em `feature/search/src/androidTest/**` e fixtures/tests visuais específicos.

Não pode alterar ViewModel, clientes, persistência, ranking, matching, player, Media3, downloads, navegação ou perfis.

### Arquivos compartilhados congelados

- DTOs e interfaces do bootstrap que materializarem a seção 4;
- `RecentSearch.kt` e `RecentSearchesStore.kt`, salvo correção previamente aprovada e executada pelo Motor;
- Gradle e catálogos de dependência;
- arquivos de navegação e chrome global.

Nenhum agente pode, por iniciativa própria, mudar arquitetura, defaults, semântica do alfinete, ordenação, deduplicação, escopo ou ownership. Dúvidas retornam para decisão antes do código.

## 7. Ordem da integração inicial (histórico)

1. Revisar e aprovar esta base documental.
2. Atualizar `nonstop` por fast-forward, confirmar árvore limpa ou separar com segurança alterações locais e registrar o SHA.
3. Materializar e testar o bootstrap comum do contrato; PR para `nonstop` e integração antes dos worktrees paralelos.
4. Criar os worktrees A e B do mesmo SHA integrado da `nonstop`.
5. Integrar primeiro **A — Motor** em `nonstop`, pois ele prova provedores, persistência e estado real sem depender do redesign.
6. Atualizar/rebasear **B — Interface** sobre a `nonstop` já com A, trocar fixtures pelo estado real sem alterar o contrato e integrar B.
7. Executar aceite conjunto, regressões e validação visual; somente então publicar artefato a partir do commit integrado.

Não encadear PR B sobre branch A. Ambos os PRs têm base `nonstop`; B é atualizado depois da integração de A.

## 8. Testes de aceite

### Contrato e Motor

- [ ] padrão do painel: Spotify e YouTube ativos, persistência inativa;
- [ ] seleção temporária não reaparece em nova sessão;
- [ ] ativar alfinete persiste imediatamente; alteração fixada atualiza persistência; desativar volta ao padrão na sessão seguinte;
- [ ] restaurar padrão só ocorre por ação explícita e respeita o estado fixado/temporário;
- [ ] uma chamada por catálogo efetivo. Spotify visual consulta YouTube Music; YouTube visual consulta YouTube WEB; preferências legadas YTM migradas para Spotify;
- [ ] com Spotify indisponível, a busca musical mantém resultados e capas de origem;
- [ ] sucesso parcial preserva e ordena resultados; retry afeta só a fonte pedida;
- [ ] query nova não publica resposta antiga; cancelamento e erro não misturam consultas;
- [ ] coleção agregada segue score e desempates definidos, e a apresentação preserva a ordem relativa em cada categoria;
- [ ] dedupe remove só identidade idêntica na mesma fonte e preserva versões alternativas;
- [ ] IDs de catálogo e áudio nunca são intercambiados;
- [ ] histórico preserva consulta/resultado, fonte e remoções existentes;
- [ ] nenhuma resolução de áudio ocorre por digitação, carregamento ou scroll;
- [ ] baseline e p50/p95 distinguem debounce, rede, parsing, agregação e primeira linha.

### Interface

- [ ] sem consulta: título, campo com botão de filtros e recentes/prompt; com texto: chips de categorias com semântica acessível e rolagem horizontal; painel com opções de catálogo, persistência e restauração;
- [ ] proveniência disponível na semântica e na folha de opções do resultado;
- [ ] todos os seis estados por fonte e falha parcial representados;
- [ ] Top destaca o primeiro resultado uma vez; seções abrem a categoria completa, que mantém as chaves e a ordem recebida;
- [ ] seleção vazia, histórico, limpar, IME Search, retry e restauração funcionam;
- [ ] live/demo/cover/remix aparecem sem aviso de substituição ou canonicalização;
- [ ] texto 150%, 280/390/480 dp, teclado aberto, artwork ausente e tema AMOLED não quebram layout;
- [ ] animações não escondem mudanças de fonte/estado e respeitam redução de movimento;
- [ ] mini-player, bottom navigation, insets, perfis e identidade visual Sonora não regrediram.

### Integração

- [ ] fixtures e estado real usam os mesmos DTOs e callbacks, sem adaptador paralelo de produto;
- [ ] YouTube Music e YouTube WEB podem aparecer juntos. YouTube Music participa quando Spotify visual está ativo, sem terceiro chip;
- [ ] indisponibilidade de uma fonte não apaga resultados das demais;
- [ ] tocar um vídeo conserva exatamente seu `catalogId` e sua versão até a fronteira existente;
- [ ] mecanismo de reprodução, fila, downloads e Media3 não tiveram diff;
- [ ] testes JVM/Compose pertinentes, `git diff --check` e build do módulo passam;
- [ ] validação em emulador é reportada separadamente de eventual validação no Redmi.

## 9. Pendências técnicas ainda não comprovadas

1. **YouTube convencional:** InnerTube WEB aprovado; validar disponibilidade regional, SafeSearch, manutenção e comportamento Android antes da integração. Apenas vídeos são expostos nesta versão.
2. **Reprodução de item YouTube:** confirmar, sem mudar o player nesta tarefa, quais IDs a fronteira atual aceita e quais limitações existem. Catálogo disponível não significa áudio reproduzível.
3. **Capas Spotify:** verificar cobertura com sessão conectada e consultas reais. A disponibilidade do enriquecimento é independente da busca de catálogo.
4. **YouTube Music:** confirmar se a implementação atual distingue com segurança faixa e vídeo e quais IDs de artista/álbum abrem as rotas preservadas.
5. **Normalização de ranking:** medir escalas dos provedores e validar consultas conhecidas antes de fixar pesos. A ordem estrutural está definida; a calibração não está provada.
6. **Cancelamento HTTP:** verificar se cancelar a coroutine encerra cada request real, em vez de apenas ignorar a resposta tardia.
7. **Performance e jank:** não há baseline numérica suficiente para abertura, digitação, primeira linha e rolagem da lista universal.
8. **Disponibilidade visual:** quando o Spotify estiver indisponível ou sem sessão, o enriquecimento conserva a capa de origem em silêncio.
9. **Contrato de tipos do YouTube:** se a integração não fornecer `TRACK`, `ARTIST` ou `ALBUM` confiáveis, retornar apenas tipos comprovados; não inferir por heurística visual.
10. **Fronteira de reprodução:** validar se `PlayerRepository.playTrack(Track)` pode ser reaproveitado sem perda de identidade ou mudança de player. Até essa prova, nenhuma ação `PLAY` deve ser anunciada como disponível por uma integração nova.

Essas pendências bloqueiam afirmações de viabilidade completa, não a construção da Interface com fixtures.

## 10. Documentos históricos com risco de conflito

| Documento | Risco | Tratamento |
| --- | --- | --- |
| `docs/sonora/SEARCH_REWORK_PLAN.md` antes desta consolidação | Definia duas abas, apenas uma fonte ativa, Spotify como aba padrão e ausência de mistura. | Substituído integralmente por esta versão: lista universal e filtros multi-seleção. |
| `docs/superpowers/specs/2026-04-17-search-artist-profile-design.md` | Define YTM como fonte única da Busca e acopla multi-categoria aos perfis. | Histórico. Preservar perfis já consolidados; não usar a exclusividade YTM como decisão atual. |
| `docs/superpowers/specs/2026-05-04-search-tab-overhaul-design.md` | Propõe preview/download lossless, prefetch e mudanças profundas na fronteira de áudio. | Fora do escopo. Não importar mudanças de player, cache, download ou resolvedor. |
| `docs/superpowers/specs/2026-07-01-search-track-actions-and-recent-searches-design.md` | É anterior ao contrato universal, mas descreve ações e recentes já existentes. | Preservar comportamento consolidado; adaptar identidade para incluir fonte, sem reabrir decisões de produto. |
| `docs/superpowers/specs/2026-03-25-stash-music-app-design.md` | Contém matching Spotify→YouTube e modelo antigo amplo do app. | Apenas contexto histórico. Não autoriza canonicalização nem mudança de reprodução. |
| `docs/superpowers/specs/2026-04-16-failed-matches-visibility-design.md` | Trata falhas de matching/sync, não estados de catálogo da Busca. | Não reutilizar sua UX ou estados automaticamente. |
| `labs/visual-preview/**` e capturas antigas | São referência/fixture ou evidência de versões anteriores, não contrato atual de produto. | Usar apenas para regressão e linguagem visual aprovada; não importar conteúdo ou arquitetura. |

## 11. Checklist desta preparação

- [x] `AGENTS.md` vigente lido; nenhum `AGENTS.md` adicional aplicável foi encontrado.
- [x] `docs/SONORA_CONSOLIDATION.md` lido e `nonstop` confirmada como destino canônico.
- [x] plano existente preservado e revisado no mesmo arquivo.
- [x] documentação histórica diretamente relacionada inspecionada e classificada sem promovê-la a autoridade.
- [x] decisões de produto atualizadas para lista universal, filtros individuais e alfinete.
- [x] contrato de fontes, seleção, persistência, resultados, IDs, tipos, estados, falha parcial, callbacks, histórico, ordenação e duplicatas definido.
- [x] comportamento temporário/fixado e restauração explícita definido.
- [x] duração da sessão temporária, isolamento entre consultas e retry da mesma consulta definidos.
- [x] ações existentes e disponibilidade por resultado incluídas sem implementar player.
- [x] compatibilidade temporária entre Motor, Visual e tela atual definida.
- [x] contrato Kotlin puro e fixtures materializados na branch de revisão.
- [x] testes JVM do contrato, suíte JVM do módulo e compilação Kotlin executados com sucesso.
- [x] workstreams, ownership de arquivos, ordem de integração e testes de aceite definidos.
- [x] riscos históricos e pendências técnicas não comprovadas registrados.
- [x] estado inicial, branch, HEAD e arquivos locais a preservar registrados.
- [x] nenhum Kotlin, Gradle, API, player, UI, dependência, emulador ou documento histórico alterado por esta preparação.
- [ ] Revisão humana e aprovação desta base documental.
- [ ] Bootstrap comum do contrato integrado em `nonstop` antes de criar os dois worktrees.
- [ ] Worktrees A e B criados do mesmo SHA integrado, somente após aprovação.

## 12. Git status final da preparação

```text
## codex/search-checkpoint-0
 M AGENTS.md
 M feature/search/src/main/kotlin/com/stash/feature/search/SearchScreen.kt
 M feature/search/src/main/kotlin/com/stash/feature/search/SearchViewModel.kt
 M gradle.properties
?? docs/sonora/SEARCH_REWORK_PLAN.md
?? tools/sonora-emulator-smoke.ps1
?? tools/sonora_emulator_capture.ps1
```

Em relação ao status inicial, somente `AGENTS.md` foi acrescentado à lista de arquivos modificados; o plano continuou untracked no mesmo caminho e foi atualizado. Os dois arquivos Kotlin, `gradle.properties` e os dois scripts permaneceram presentes com o mesmo estado observado, sem descarte ou sobrescrita por esta preparação.

## 13. Registro do bootstrap compartilhado

Branch e HEAD no início e no fim do bootstrap:

```text
codex/search-checkpoint-0
0ded5dbad41c967f603fe80f5cb14c4986718cff
```

Status no início do bootstrap:

```text
## codex/search-checkpoint-0
 M AGENTS.md
 M feature/search/src/main/kotlin/com/stash/feature/search/SearchScreen.kt
 M feature/search/src/main/kotlin/com/stash/feature/search/SearchViewModel.kt
 M gradle.properties
?? docs/sonora/SEARCH_REWORK_PLAN.md
?? tools/sonora-emulator-smoke.ps1
?? tools/sonora_emulator_capture.ps1
```

Arquivos criados pelo bootstrap:

- `feature/search/src/main/kotlin/com/stash/feature/search/contract/SearchContractModels.kt`;
- `feature/search/src/main/kotlin/com/stash/feature/search/contract/UniversalSearchActions.kt`;
- `feature/search/src/main/kotlin/com/stash/feature/search/contract/UniversalSearchFixtures.kt`;
- `feature/search/src/test/kotlin/com/stash/feature/search/contract/SearchContractTest.kt`.

Validações executadas:

```text
./gradlew :feature:search:testDebugUnitTest --tests com.stash.feature.search.contract.SearchContractTest :feature:search:compileDebugKotlin
BUILD SUCCESSFUL

./gradlew :feature:search:testDebugUnitTest
BUILD SUCCESSFUL
```

Status final do bootstrap:

```text
## codex/search-checkpoint-0
 M AGENTS.md
 M feature/search/src/main/kotlin/com/stash/feature/search/SearchScreen.kt
 M feature/search/src/main/kotlin/com/stash/feature/search/SearchViewModel.kt
 M gradle.properties
?? docs/sonora/SEARCH_REWORK_PLAN.md
?? feature/search/src/main/kotlin/com/stash/feature/search/contract/
?? feature/search/src/test/kotlin/com/stash/feature/search/contract/
?? tools/sonora-emulator-smoke.ps1
?? tools/sonora_emulator_capture.ps1
```

Durante o bootstrap, somente este plano e os quatro arquivos novos de contrato/teste foram editados. `AGENTS.md`, `gradle.properties`, `SearchScreen.kt`, `SearchViewModel.kt` e os scripts do emulador conservaram o estado preexistente. Nenhum worktree, commit, push ou merge foi criado.

## 14. Spike de busca no YouTube convencional sem Data API v3

Em 2026-11-10 foram executadas, com rede real, duas amostras para cada uma de seis consultas iguais (comum, demo, apresentação ao vivo, upload raro, cover e consulta artificial sem correspondência), limite de 15 s e sem baixar mídia. Os tempos abaixo são do ambiente de desenvolvimento, não medições em Android:

| Candidato | Sucesso | Média | Mediana | p95 | Observação |
| --- | ---: | ---: | ---: | ---: | --- |
| InnerTube WEB em `youtube.com/youtubei/v1/search` | 12/12 | 840 ms | 868 ms | 936 ms | Sem chave de projeto; paginação real, 18 itens na segunda página e zero IDs repetidos. |
| NewPipeExtractor 0.26.5 | 12/12 | 974 ms | 729 ms | 3.777 ms | O primeiro acesso foi o cold start; nas 11 amostras seguintes, média de 720 ms e p95 de 842 ms. |
| yt-dlp 2026.08.19 com `ytsearch20:` | 12/12 | 3.144 ms | 3.029 ms | 3.820 ms | Retornou 20 vídeos nas consultas úteis; `ytsearch` não expôs token estável de continuação. |

Os três preservaram o `videoId` original e encontraram a demo `JOvAJJI2Ui8`, a apresentação `JQ2pTamaqQ4`, o upload raro `9Vz-MkbnSg4` e o cover `MoL0u4fxUW4`, com título, canal, thumbnail e duração. InnerTube e NewPipe também distinguiram vídeos, canais e playlists. A consulta artificial ainda recebeu aproximações do YouTube em InnerTube e NewPipe; somente yt-dlp retornou zero, portanto consulta sem correspondência não pode ser inferida por texto aleatório.

Recomendação para revisão: usar InnerTube WEB convencional como candidato primário, reaproveitando OkHttp, kotlinx.serialization e o transporte existente, mas com parser próprio de `videoRenderer` e cancelamento que encerre o `Call`. NewPipe é a alternativa pura para Android com parser e paginação maduros, porém acrescentou seis artefatos de runtime e 3.702.880 bytes em JARs brutos antes de compressão/empacotamento; isso não é medição do delta do APK. yt-dlp já existe no aplicativo e seu `execute` pode ser interrompido, mas depende do runtime Python, é serializado no processo e foi substancialmente mais lento, sendo inadequado como caminho primário a cada digitação.

As três opções usam acesso automatizado não oficial. Os [Termos do YouTube](https://www.youtube.com/t/terms) restringem meios automatizados salvo as exceções neles descritas; a natureza pessoal do aplicativo não elimina esse risco. A aprovação humana deve tratar conformidade e tolerância a bloqueios antes da integração.

Antes da implementação faltam aprovação da alternativa e de seus termos, política de atualização contra mudanças do YouTube, regra aprovada para converter ordem do provedor em `rankingScore` sem inventar relevância, desenho de timeout/cancelamento e teste Android de memória, APK e reprodução. Estruturalmente, um vídeo cabe como `CatalogIdentity(CatalogSource.YOUTUBE, videoId)` e `SearchResultType.VIDEO`; isso não prova que todo resultado possa ser reproduzido e não autoriza substituir demo, cover ou live por outra versão.

### Implementação isolada aprovada — 2026-11-10

`YouTubeWebSearchClient` usa o OkHttpClient recebido e kotlinx.serialization existentes, descobre a configuração WEB pública na página inicial e consulta `/youtubei/v1/search`. Cada operação HTTP tem timeout de 15 s por padrão; cancelamento da coroutine chama `Call.cancel()`. Erros HTTP e respostas inválidas propagam falha, sem virar lista vazia. Metadados opcionais permanecem nulos; itens sem ID válido ou título são descartados. Duplicatas são preservadas para a deduplicação por identidade do engine existente.

A página retorna continuation e `nextRank`; o chamador deve fornecer esse valor como `rankOffset` na próxima página da mesma consulta. Posições de `videoRenderer` incompletos são preservadas como lacunas. O adaptador retorna somente `YOUTUBE`/`VIDEO`, sem áudio ou ações executáveis. O score `1.0 / (providerRank + 1.0)` é uma convenção ordinal provisória aprovada, não relevância medida.

Testes locais usam fixture JSON e MockWebServer para metadados, campos ausentes, vazio, IDs duplicados, paginação, 429, timeout e cancelamento efetivo (evento OkHttp `canceled` e `Call.isCanceled()`). O adaptador testa demos, lives e covers sem substituir IDs. Não há conexão com SearchScreen/SearchViewModel nem alteração em InnerTubeClient, resolvedores ou dependências.

Validação: `:data:ytmusic:testDebugUnitTest`, `:feature:search:testDebugUnitTest`, `:data:ytmusic:compileDebugKotlin` e `:feature:search:compileDebugKotlin` concluíram com `BUILD SUCCESSFUL`. Os cinco testes novos do cliente passaram. Esta implementação foi validada com rede local simulada; a evidência de rede externa continua sendo o spike acima. Limitações: a configuração WEB é descoberta a cada página (uma requisição adicional), o timeout é por chamada HTTP e a continuidade da consulta/offset é responsabilidade do chamador. Memória, latência e reprodução em Android permanecem sem validação nesta etapa.


## Checkpoint de reprodução exata na Busca universal

Esta etapa sucede as decisões históricas de somente navegação. Resultados `TRACK` e `VIDEO` do YouTube Music e `VIDEO` do YouTube WEB oferecem `PLAY` quando o catálogo fornece um `videoId` válido. O filtro visual Spotify continua consultando o YouTube Music. A identidade de catálogo e `AudioIdentity("youtube", videoId)` mantêm o identificador original, inclusive em lives, demos, remixes e covers.

O toque chama `PlayerRepository.playExactSearchResult`. A persistência usa somente o identificador YouTube, confirma a linha retornada e encaminha o ID ao resolvedor YouTube existente. O cache de URLs por ID de biblioteca é ignorado para esse fluxo, inclusive na renovação da URL durante a reprodução. Se a linha mudar de identidade ou o vídeo selecionado estiver indisponível, o fluxo falha sem procurar outra gravação. O item mantém o ID persistido para o mini-player e a fila de um item. A última solicitação da Busca prevalece e os toques repetidos durante a preparação são ignorados.

A precisão se aplica ao toque na Busca e às renovações do item que permanece carregado. O estado persistido de retomada após encerramento do processo guarda apenas o ID de biblioteca e reconstitui o item pelas regras gerais da biblioteca. Estender a garantia de identidade à retomada fria exigiria persistir o modo de resolução junto à fila e propagá-lo pelas entradas de retomada, inclusive comandos de mídia do sistema.

## Contrato de interface V217

| Estado | Apresentação |
| --- | --- |
| Consulta vazia ou apenas espaços | Título, campo, botão de filtros e histórico recente ou orientação. |
| Consulta ativa | Chips horizontais Top → Artistas → Álbuns → YouTube → Músicas. A elegibilidade da busca e o debounce continuam sob responsabilidade do motor. |
| Top | O primeiro item da coleção ordenada é a melhor correspondência. Abaixo, cada categoria com outros resultados mostra até três itens na ordem recebida e oferece “Ver tudo”. As seções seguem o primeiro item de cada grupo na coleção ordenada. |
| Artistas e Álbuns | Cada categoria lista seu tipo de resultado na ordem recebida. Retratos circulares para artistas e capas quadradas para álbuns. |
| YouTube | Vídeos do catálogo YouTube WEB. A categoria continua acessível com a fonte desligada e orienta a ativá-la nos filtros. |
| Músicas | Faixas e vídeos musicais do YouTube Music, com o enriquecimento de capas já disponível. |
| Falha parcial, carregamento ou ausência de resultados | Top considera as fontes ativas. As categorias consideram os catálogos pertinentes e oferecem retry por fonte quando disponível. |
| Configuração | O botão do campo abre a folha de fontes. A seleção fixada, a migração de preferências legadas e a restauração continuam nas rotinas existentes. |

A seleção de categoria e “Ver tudo” usam apenas a coleção disponível. Mudanças de fontes e retry continuam delegados à sessão. Playlists passam a ser elegíveis como categoria quando o motor expuser esse tipo de resultado. As linhas mantêm as ações declaradas pelo resultado e o ID original. A folha contextual mostra metadados, proveniência e ações executáveis.

A janela da Busca usa uma base grafite no escuro, preto em AMOLED e neutra no claro. `StashScaffold` pinta essa base na rota Busca para cobrir também a área atrás da barra de status e deixa a própria tela consumir os insets do teclado. A navegação inferior e o mini-player continuam sob a composição do aplicativo.

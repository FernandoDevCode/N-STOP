# Sonora — especificação vigente e contrato do rework da Busca

**Status:** base documental para desenvolvimento paralelo; ainda sem implementação

**Data da consolidação:** 10/11/2026

**Repositório:** `FernandoDevCode/N-STOP`

**Destino canônico:** `nonstop`

**Autoridade:** este documento prevalece, para a Busca, sobre planos históricos e experimentais. `AGENTS.md` e `docs/SONORA_CONSOLIDATION.md` continuam prevalecendo nas regras gerais do repositório.

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

1. A Busca é **universal**: uma consulta produz **uma lista única de resultados**, sem abas e sem seções obrigatórias por serviço.
2. As fontes de catálogo são selecionadas individualmente por filtros. **Spotify** e **YouTube convencional** começam ativos; **YouTube Music** começa inativo.
3. O alfinete fica no cabeçalho e é um controle separado dos filtros.
4. Com o alfinete ativo, a seleção atual é persistida, restaurada entre sessões e atualizada a cada alteração. Com o alfinete inativo, a seleção só vale para a sessão atual.
5. “Restaurar padrão” é uma ação explícita. O padrão é `{SPOTIFY, YOUTUBE}`; YTM permanece desligado.
6. Spotify sem autenticação fica `unavailable` com explicação clara e ação de conexão já existente. Não se inventa fallback, resultado ou identidade Spotify.
7. Cada linha identifica discretamente sua fonte de catálogo. A origem não depende somente de cor.
8. Vídeos raros, demos, covers, lives, remixes e versões escolhidas pelo usuário são resultados legítimos. Não substituir silenciosamente um vídeo selecionado por uma gravação “canônica” e não deduplicar agressivamente versões alternativas.
9. A seleção de fontes visíveis na Busca não controla nem descreve, por si só, a infraestrutura interna usada posteriormente para resolver áudio. Fonte de catálogo e fonte/ID de áudio são identidades diferentes.
10. Mini-player, bottom navigation, perfis de artista e identidade visual Sonora são preservados. Este rework não redesenha chrome global nem perfis.
11. O mecanismo de reprodução, Media3, downloads, fila e resolução de stream não mudam nesta tarefa. A Busca apenas entrega identidades separadas e aciona as fronteiras existentes.
12. A busca do YouTube convencional usará InnerTube WEB independente, sem YouTube Data API v3, NewPipeExtractor ou yt-dlp na pesquisa. O cliente e o adaptador são isolados; integração com a UI e reprodução continuam pendentes.
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
- novas funcionalidades de recomendação, categorias, charts ou personalização;
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
- cada filtro alterna somente a sua fonte; os demais não mudam;
- seleção vazia é permitida e mostra um estado orientando o usuário a ativar ao menos uma fonte, sem disparar provedores;
- ao ativar o alfinete, persistir imediatamente o conjunto atual e `isPinned = true`;
- enquanto fixado, toda mudança de filtro atualiza a preferência persistida;
- ao desativar o alfinete, manter a seleção atual apenas durante a sessão e persistir `isPinned = false`; na próxima sessão, usar o padrão;
- navegar para um resultado e voltar à mesma instância da Busca conserva a seleção temporária em memória; criar uma nova instância da Busca inicia outra sessão e usa o padrão, salvo seleção fixada;
- “Restaurar padrão” define explicitamente `{SPOTIFY, YOUTUBE}`. Se fixado, também atualiza o valor persistido; se temporário, altera somente a sessão atual;
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
)
```

Semântica:

- `idle`: consulta ainda não elegível ou fonte não consultada;
- `loading`: consulta em andamento; `previous` só pode ser não vazio em retry explícito da mesma `query` (`isRetry = true`);
- `data`: um ou mais itens válidos;
- `empty`: a fonte respondeu sem itens;
- `unavailable`: a consulta não pode ser feita nas condições atuais, por exemplo Spotify sem autenticação;
- `error`: tentativa falhou; contém retry somente quando seguro.

Cada fonte selecionada evolui de forma independente. Uma fonte indisponível ou com erro não derruba dados das outras. A lista universal contém resultados `data` das fontes bem-sucedidas; a UI mostra aviso discreto e ação por fonte para falhas parciais. Só existe erro de página inteira quando nenhuma fonte selecionada fornece dados e pelo menos uma falha; só existe vazio global quando todas as fontes selecionadas concluíram como `empty`. Fontes desmarcadas não são consultadas e seus resultados não aparecem.

Todos os estados não ociosos carregam a consulta que os produziu. `UniversalSearchUiState` só aceita estados cuja `query` coincide com sua consulta atual. Ao mudar a consulta, resultados anteriores são removidos da lista universal; eles não podem ser publicados como dados nem como `Loading.previous` da nova consulta. Reutilização de `previous` é exclusiva de retry da mesma consulta.

Cada item de `Data.items` ou `Loading.previous` deve pertencer à `CatalogSource` usada como chave daquele estado em `sourceStates`. Cada item de `orderedResults` deve ser integralmente igual a um item visível do estado da mesma fonte; igualdade apenas de `stableKey` não basta e não autoriza trocar identidade, tipo, título, áudio ou ações no agregado.

### 4.6 Ordenação universal

O Motor entrega `orderedResults`; a Interface renderiza a ordem recebida.

1. ordenar por `rankingScore` decrescente;
2. em empate, preservar `providerRank` crescente;
3. persistindo o empate, usar a ordem estável `SPOTIFY`, `YOUTUBE`, `YOUTUBE_MUSIC` e depois `stableKey`;
4. não criar blocos por fonte ou tipo;
5. chegada tardia de uma fonte pode recalcular a lista, mas itens com a mesma identidade e score mantêm desempate determinístico.

A normalização de relevância entre provedores precisa de teste com consultas representativas antes de congelar pesos. O contrato de mocks deve incluir scores variados, mas não transforma pesos simulados em evidência de qualidade real.

### 4.7 Duplicatas e versões

- Remover automaticamente apenas repetição do mesmo `(source, type, catalogId)` na mesma resposta.
- Resultados de fontes diferentes permanecem separados na primeira entrega, mesmo quando título e artista parecem iguais.
- Título, artista, duração aproximada ou artwork não bastam para colapsar itens.
- Um eventual detector pode marcar candidatos a duplicata para telemetria/teste, mas não escondê-los sem identidade forte e aprovação de produto.
- Demos, covers, lives, remixes, remasters, edits, versões acústicas e vídeos raros permanecem visíveis e selecionáveis.

### 4.8 Histórico de pesquisas

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
    fun onConnectSource(source: CatalogSource)
    fun onResultAction(result: UniversalSearchResult, action: SearchResultAction)
    fun onRecentSelected(item: RecentSearchItem)
    fun onRecentRemoved(item: RecentSearchItem)
    fun onRecentCleared()
}
```

- A Interface não chama clientes de rede, DataStore ou resolvedores diretamente.
- `onResultAction` só é executável quando `action in result.supportedActions`; preserva a identidade escolhida e delega à fronteira existente.
- As ações existentes representadas são reprodução, tocar a seguir, adicionar à fila, rádio, adicionar à playlist, download e navegação. O contrato declara disponibilidade, mas não implementa essas integrações.
- `PlayerRepository.playTrack(Track)` é candidato a reaproveitamento para reprodução após adaptação validada na integração. Isso ainda não foi comprovado e não autoriza alteração em `PlayerRepository`, `Track`, Media3 ou no mecanismo de reprodução.
- As fixtures compartilhadas fornecem somente DTOs e estados puros. A implementação fake de `UniversalSearchActions`, com no-ops ou gravação de eventos, é responsabilidade do Codex Visual em seu componente isolado.

## 5. Comportamentos de referência para dados simulados

O Codex Visual deve conseguir demonstrar, sem integração real:

- consulta vazia com e sem histórico;
- Spotify `unavailable` por autenticação, YouTube com `data` e YTM desmarcado;
- duas fontes em `loading` e uma com dados anteriores;
- dados intercalados de faixa, vídeo, artista e álbum;
- falha parcial com resultados preservados;
- vazio global e seleção vazia;
- filtros temporários, alfinete ativado/desativado e restauração explícita;
- artwork ausente, texto longo, explícito e versões live/demo/cover/remix;
- mini-player presente/ausente e bottom navigation preservada.

Fixtures não podem ser apresentadas como prova de integração, relevância, latência ou viabilidade de reprodução.

Fixtures do YouTube convencional servem somente para renderização e invariantes do DTO. Elas não simulam cliente, rede, autenticação, disponibilidade ou reprodução e não permitem declarar a fonte pronta.

## 5.1 Compatibilidade temporária durante o paralelo

- O Motor implementa componentes isolados e não quebra nem substitui a `SearchScreen` atual durante seu workstream.
- O Visual implementa um componente Compose isolado, alimentado exclusivamente pelos DTOs, callbacks e fixtures compartilhados; não depende do novo Motor nem altera o `SearchViewModel` atual.
- A `SearchScreen`/`SearchViewModel` atuais permanecem compatíveis até a etapa de integração.
- A conexão definitiva entre Motor e Visual acontece somente após os dois workstreams, na ordem da seção 7.
- O contrato usa um único tipo para cada conceito. Não criar enums de fase paralelos ao sealed state, modelos duplicados de seleção/resultado ou adapters que representem exatamente o mesmo estado sem necessidade de fronteira.

## 6. Divisão de trabalho

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

## 7. Ordem de integração

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

- [ ] defaults: Spotify e YouTube ativos, YTM inativo, alfinete inativo;
- [ ] seleção temporária não reaparece em nova sessão;
- [ ] ativar alfinete persiste imediatamente; alteração fixada atualiza persistência; desativar volta ao padrão na sessão seguinte;
- [ ] restaurar padrão só ocorre por ação explícita e respeita o estado fixado/temporário;
- [ ] uma chamada por fonte selecionada e nenhuma por fonte desmarcada;
- [ ] Spotify sem autenticação resulta em `Unavailable(AUTHENTICATION_REQUIRED)`, sem fallback fictício;
- [ ] sucesso parcial preserva e ordena resultados; retry afeta só a fonte pedida;
- [ ] query nova não publica resposta antiga; cancelamento e erro não misturam consultas;
- [ ] lista universal segue score e desempates definidos, sem seções por serviço;
- [ ] dedupe remove só identidade idêntica na mesma fonte e preserva versões alternativas;
- [ ] IDs de catálogo e áudio nunca são intercambiados;
- [ ] histórico preserva consulta/resultado, fonte e remoções existentes;
- [ ] nenhuma resolução de áudio ocorre por digitação, carregamento ou scroll;
- [ ] baseline e p50/p95 distinguem debounce, rede, parsing, agregação e primeira linha.

### Interface

- [ ] filtros individuais e alfinete separado no cabeçalho, com semântica acessível;
- [ ] fonte discreta em cada resultado sem depender apenas de cor;
- [ ] todos os seis estados por fonte e falha parcial representados;
- [ ] lista mista de faixa, vídeo, artista e álbum usa keys estáveis e a ordem recebida;
- [ ] seleção vazia, histórico, limpar, IME Search, retry, conexão e restauração funcionam;
- [ ] live/demo/cover/remix aparecem sem aviso de substituição ou canonicalização;
- [ ] texto 150%, 280/390/480 dp, teclado aberto, artwork ausente e tema AMOLED não quebram layout;
- [ ] animações não escondem mudanças de fonte/estado e respeitam redução de movimento;
- [ ] mini-player, bottom navigation, insets, perfis e identidade visual Sonora não regrediram.

### Integração

- [ ] fixtures e estado real usam os mesmos DTOs e callbacks, sem adaptador paralelo de produto;
- [ ] Spotify + YouTube podem aparecer juntos na lista; YTM só aparece quando selecionado;
- [ ] indisponibilidade de uma fonte não apaga resultados das demais;
- [ ] tocar um vídeo conserva exatamente seu `catalogId` e sua versão até a fronteira existente;
- [ ] mecanismo de reprodução, fila, downloads e Media3 não tiveram diff;
- [ ] testes JVM/Compose pertinentes, `git diff --check` e build do módulo passam;
- [ ] validação em emulador é reportada separadamente de eventual validação no Redmi.

## 9. Pendências técnicas ainda não comprovadas

1. **YouTube convencional:** InnerTube WEB aprovado; validar disponibilidade regional, SafeSearch, manutenção e comportamento Android antes da integração. Apenas vídeos são expostos nesta versão.
2. **Reprodução de item YouTube:** confirmar, sem mudar o player nesta tarefa, quais IDs a fronteira atual aceita e quais limitações existem. Catálogo disponível não significa áudio reproduzível.
3. **Spotify:** confirmar comportamento real sem sessão, refresh de 401, 429/`Retry-After`, artwork e cobertura de tipos além de faixa antes de prometer esses tipos.
4. **YouTube Music:** confirmar se a implementação atual distingue com segurança faixa e vídeo e quais IDs de artista/álbum abrem as rotas preservadas.
5. **Normalização de ranking:** medir escalas dos provedores e validar consultas conhecidas antes de fixar pesos. A ordem estrutural está definida; a calibração não está provada.
6. **Cancelamento HTTP:** verificar se cancelar a coroutine encerra cada request real, em vez de apenas ignorar a resposta tardia.
7. **Performance e jank:** não há baseline numérica suficiente para abertura, digitação, primeira linha e rolagem da lista universal.
8. **Autenticação e disponibilidade:** definir copy e ação de conexão usando as rotas existentes, sem expandir o escopo de auth.
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


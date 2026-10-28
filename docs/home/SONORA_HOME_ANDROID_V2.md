# Sonora — Home Android V2

Implementação restrita à Home do aplicativo Android principal. Referência: imagem e pedido do usuário em 28/10/2026. Aprovação artística depende de capturas reais do Redmi Note 14.

## Inventário funcional e fontes

| Conteúdo/controle | Contrato real e dependências | Ações mantidas |
|---|---|---|
| Música / Podcasts / Audiobooks | Estado local salvo do filtro; pedidos de abertura do controlador de podcasts | Trocar categoria; Podcasts mantém catálogo Apple, RSS, programas seguidos, episódios e player existentes; Audiobooks mantém aviso informativo |
| Daily Discover | Playlist builtin materializada, receitas e fila de descoberta observadas pelo HomeViewModel; só aparece com faixas e sem `hideFromHome` | Tocar com a regra online/offline atual, abrir playlist, criar mix, atualizar e minimizar na sessão |
| Daily Discover em preparação | `dailyDiscoverBuilding` avalia receita ativa, playlist, contagem e trabalho pendente reais | Mensagem informativa, sem reprodução ou progresso fictício |
| Pager de mixes próprios | `yourMixes`, playlists materializadas e estado READY/BUILDING/EMPTY das receitas; exclui builtin e playlists ocultas | Tocar, abrir, atualizar, editar/excluir mixes próprios, ocultar; indicadores apenas com várias páginas |
| Suas playlists | Playlists fixadas por timestamp; favoritos mesclados somente com preferência ativa | Abrir, tocar todas, remover da Home; favoritos encaminham ao foco existente da Biblioteca |
| Feito para você, Rádios, Redescoberta | Playlists sincronizadas classificadas por `mixRail`, ordenadas por fixação/adição recente | Abrir, menus existentes, ver todos; limite de 12 nos rails preservado |
| Novos lançamentos, Playlists Qobuz, Álbuns em destaque | `HomeDiscoveryRepository`, provedor externo e rede; opt-out global, filtros de gênero e seções visíveis controlam consultas já existentes | Abrir detalhes e reproduzir pelos destinos existentes; ver playlists; expandir/recolher ranking |
| Comunidade | Slot fornecido pelo host, habilitado por preferência própria | Conteúdo e callbacks existentes do módulo Comunidade |
| Personalize seu início | Estado inicial sem hero, preparação, mixes, playlists, favoritos ou catálogo | Abrir configuração; texto explica conexão, sincronização e alimentação da descoberta |
| Avisos lossless/backfill | Preferências, saúde das fontes e progresso real do worker observados | Configurar, dispensar e confirmar conclusão, sem alterar serviços |
| Serviços e apoio | Controle streaming, divulgação de privacidade, Discord, diagnóstico/tradução e apoiadores existentes | Ações preservadas abaixo do conteúdo musical; controles quebram linha em largura pequena |

A ordem e a visibilidade de `HomeSectionsPreference.visibleSections` são mantidas. O destaque de descoberta não integra essa enumeração e fica após os filtros, respeitando minimizar/ocultar. Não foi criada preferência ou persistência nova.

## Apresentação

- `SonoraHomeTheme`: paleta local grafite, texto branco-frio e lavanda pontual; respeita tema claro e AMOLED. Não muda o tema nem os tokens dos outros destinos.
- `NonstopMediaFilters`: nota, microfone e livro; grupo centralizado quando cabe, rolagem horizontal em largura/fonte restrita, alvos mínimos de 48dp. Halo estático discreto no selecionado, sem animação contínua.
- `SonoraContentCard`: artwork quadrado de 136dp, raio compartilhado, duas linhas para metadados, altura natural e sem reserva de subtítulo vazio. Usa `VisualCover`/Coil para imagens existentes; falta de imagem recebe ícone neutro. Nenhuma capa foi criada ou substituída.
- `SonoraSectionTitle`: reutiliza tipografia e espaçamentos do sistema visual; ação passa para outra linha quando necessário.
- `SonoraHeroCard`: artwork real com scrim, superfície grafite e contorno discreto; texto e ações aumentam a altura conforme a fonte. Sem glow, partículas, seek ou progresso. Botão de opções abre o menu verdadeiro; botão de criar mix usa seu callback próprio.
- `DailyDiscoverBuildingCard` e `PersonalizeCard`: superfícies neutras com conteúdo adaptativo. Preparação não simula capa nem progresso.
- `LazyColumn` e `LazyRow` continuam responsáveis pela rolagem. O host mantém os insets Android; a Home não adiciona barra de status. Padding inferior continua `bottomChromePadding(24.dp)`.

O componente `NonstopListeningCard`, os dados e a lógica de reprodução foram preservados, mas sua inserção foi retirada da Home. **A faixa atual não é duplicada em banner; permanece no mini-player existente.** Sem descoberta real não há item/altura reservado para hero. Loading usa indicador compacto do estado existente.

## Reutilização e limites

Reutilizados: `core/ui/visual` (tipografia, raios, espaçamentos, tema aplicável e artwork), HomeViewModel/HomeUiState, repositórios e fluxos, pager/lists Compose, `RankedAlbumList`, banners de serviço, menus e callbacks Android. Componentes específicos da Home foram adaptados; não há um segundo design system global.

O cabeçalho antigo não é renderizado na Home. Os destinos de configurações e demais funções continuam nos fluxos já existentes. Mini-player, navegação inferior, players V7/V8, Art Lab, Explorar, Buscar, Rádio, Biblioteca, banco, autenticação, streaming e sincronização não foram editados. Não houve commit, push ou merge. Arquivos locais preexistentes não rastreados foram preservados.

Não foram adicionados: histórico/Tocadas recentemente (não há contrato de histórico nesta Home), artistas/álbuns da imagem, recomendações artificiais, novas consultas, hero de faixa atual ou Atrito Cinético. Não foi inventada ação “Mostrar tudo” para lançamentos, pois não há callback desse rail.

## Validação

Execução final em 28/10/2026:

- `:feature:home:testDebugUnitTest`: **129 testes, zero falhas, zero erros, zero ignorados**.
- `SonoraHomeLayoutTest`: **54 combinações**, cobrindo 280/320/360/390/412/480dp × fontes 100/125/150% × escuro/claro/AMOLED. Verifica largura, área mínima do filtro, seleção, ações do hero e acesso ao último conteúdo por rolagem.
- `SonoraHomeScreenTest`: quatro testes da Home Android com ViewModel controlado: configuração inicial, loading/preparação real, abrir/tocar/minimizar hero, ordem/ocultação de seções e menu de playlist. O menu de minimizar é verificado por sua ação semântica de acessibilidade após aguardar a exibição; a injeção de toque entre janelas do Robolectric não acionou o callback e NÃO é apresentada como teste físico aprovado.
- Testes de HomeUiState e HomeViewModel incluem ausência de hero em loading/vazio, catálogo suprimindo personalização, preparação real, minimizar mantendo páginas de mix e playlist Daily Discover oculta. A suíte existente de reprodução e filtros foi preservada.
- `:app:assembleDebug`: **BUILD SUCCESSFUL**. `apksigner verify --print-certs`: sucesso, certificado Android Debug existente.
- Pacote confirmado por `aapt`: `com.stash.app.debug`, versão `0.9.114` (150), minSdk 26 / targetSdk 35, rótulo preexistente `Stash Debug`.
- `git diff --check`: sem erros.

Os testes usam Android Compose/Robolectric API 34, não validam GPU, blur nativo, fidelidade estética ou reprodução num aparelho. Fixtures permanecem apenas em `src/test`; não foram introduzidos dados ilustrativos no fluxo real.

APK principal gerado: `C:/Users/User/Documents/GitHub/Spotify/Stash/app/build/outputs/apk/debug/app-debug.apk` (185.686.835 bytes).

SHA-256: `C63414DEFE355C6764673B7150673409BC44C5E77426CBCA776BA2BE1A2AD540`.

Relatório: `feature/home/build/reports/tests/testDebugUnitTest/index.html`. Log final: `build/home-v2-final.log`.

Reprodução na raiz do repositório (SDK local utilizado nesta máquina):

```powershell
$env:ANDROID_HOME = (Resolve-Path 'labs/app-desktop/build/android-sdk').Path
.\gradlew.bat :feature:home:testDebugUnitTest :app:assembleDebug --max-workers=2 '-Pkotlin.incremental=false'
```

A compilação sem incremental contorna os símbolos de funções de topo não resolvidos encontrados no cache incremental inicial. Não exigiu alteração de serviços, build global, SDK salvo ou credenciais.

ADB local consultado: nenhum dispositivo conectado; sem emulador no SDK utilizado. Não foi possível medir a largura real do Redmi, testar os insets do aparelho, produzir suas capturas ou aprovar paridade visual. Permanecem pendentes teste manual de reprodução online/offline, menus/navegação completos, rolagem sob o mini-player, desempenho e inspeção artística nesse dispositivo.

O contrato atual da Home não expõe erro de catálogo separado: os serviços existentes falham para listas vazias. A implementação preserva esse comportamento; não foi inventado um estado de erro que o backend não fornece.

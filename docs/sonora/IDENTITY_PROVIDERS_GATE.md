# Sonora — identidade visível e provedores

**Fonte de verdade:** `AGENTS.md`, `docs/sonora/SEARCH_REWORK_PLAN.md` e este gate.

## Contrato

- Marca visível: Sonora. `Stash` e `NØNSTOP` são históricos/internos, não publicidade ou nomes de UI.
- Catálogos de música expostos: Spotify, YouTube convencional e YouTube Music. O Spotify fornece identidade e descoberta, não um extrator de arquivo de áudio.
- A reprodução/baixa atual usa os mecanismos YouTube disponíveis. Não prometer FLAC nem chamar Qobuz, JioSaavn ou servidores do Stash para esse fluxo.
- Credenciais, backups e arquivos anteriores continuam intactos; chaves de banco, pacote `com.stash`, URIs legadas e avisos obrigatórios GPL preservados por compatibilidade.
- Recursos da infraestrutura antiga sem substituto Sonora (partilha de links, sync web) permanecem **desativados**, nunca apontados para um serviço fingindo ser o Sonora.

## Gate de revisão

`python3 tools/check_sonora_identity.py`: examina textos localizados, literais de apresentação, lista de resolvers e flags. O CI rejeita regressões nesses pontos.

**Limites do gate:** não garante a ausência de todos os textos montados dinamicamente, dependências transversais ou inconsistências visuais. Antes de aprovar, capturar manualmente Home, Explorar, Rádio, Busca, Biblioteca, Player, Configurações e cada submenu/diálogo em PT-BR, EN, modo escuro/claro e com/sem contas; testar aparelho Android 16 e teclado com mini-player. Conferir também todo destino de link externo acionável.

## Débitos explícitos

- Sem FLAC/Hi-Res novo ao excluir Qobuz; arquivos FLAC já baixados continuam reproduzíveis.
- Compartilhamento remoto por links requer infraestrutura Sonora própria; compartilhar informações por Android permanece.
- Sincronização web via serviço original não é permitida; a integração Sonora precisará de backend separado e aprovado.
- A busca universal com filtros por Spotify/YouTube/YTM permanece regida pelo plano oficial; não chamar UI de 3 fontes de concluída sem testar o motor integrado.

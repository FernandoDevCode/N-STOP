# Sonora — identidade visível e provedores

**Fonte de verdade:** `AGENTS.md`, `docs/sonora/SEARCH_REWORK_PLAN.md` e este gate.

## Contrato

- Marca visível: Sonora. `Stash` e `NØNSTOP` são históricos/internos, não publicidade ou nomes de UI.
- Catálogos da Busca: YouTube Music para Top/Artistas/Álbuns/Músicas; YouTube WEB somente no chip YouTube. MusicBrainz complementa edições nos perfis sem distribuir áudio. Spotify fica fora da Busca, preservado somente para fotos de artistas e sincronização pessoal fora dessa tela.
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
- A Busca tem duas fontes efetivas, sem painel de fontes, regidas pelo plano oficial. Não declarar reprodução de edições MusicBrainz verificada até realizar testes em aparelho com uma conta YTM real.

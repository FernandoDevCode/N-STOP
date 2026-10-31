# Sonora — alvo visual Apple Music iOS (referência aprovada)

**Escopo exclusivo:** páginas do catálogo online de artista e álbum. `ArtistHero.kt`, `AlbumHero.kt`, `ArtistProfileScreen.kt`, `PopularTracksSection.kt`, `AlbumDiscoveryScreen.kt` e modo editorial opt-in de `SongRow.kt`. Não alterar mini-player, navegação principal nem a Search global.

## Autoridade visual

A imagem composta aprovada na conversa contém três capturas: **artista U2 aberto**, **álbum The Joshua Tree** e **perfil U2 rolado**. Cada painel mede aproximadamente 500 px de largura por 1024 px de altura na composição. A especificação abaixo registra as decisões mesmo quando a imagem ainda não foi anexada ao repositório.

A imagem é um **mock visual**, não um SDK do iOS. Copiar as proporções e o ritmo, ajustando apenas insets do Android, dados realmente disponíveis, acessibilidade e ações suportadas.

### Artista — tela inicial
- Fotografia full-bleed desde o topo, com scrim grafite/negro progressivo na parte inferior.
- Nome do artista ou wordmark real na região inferior esquerda; abaixo, contagem real do catálogo (nunca inventar "ouvintes mensais").
- Linha de ações **dentro da própria fotografia**: reprodução circular clara, ação secundária em pílula, coração contextual e menu.
- Nenhuma fileira de redes sociais sob a foto. Redes e links externos ficam em **Sobre**.
- Abas editoriais com sublinhado branco-frio; começam logo após o bloco visual.
- Seção **Popular** imediatamente após as abas. A referência a posiciona em ~52% da altura total do mock, sujeito a barras do Android e dimensões do artista.
- Música popular: **índice, capa pequena, título, informação secundária verdadeira e menu ⋮**. Downloads no menu quando disponíveis; sem cards grandes nem download gigante na superfície.
- Evitar novos campos de número de plays se o provider não os entregar.

### Álbum
- Atmosfera suave derivada da capa, sem acento cromático de interface fixo.
- Capa central ~220–225 dp, cantos discretos, sem grande vazio entre a imagem e o título.
- Nome do álbum ~30 sp, artista/ano em uma linha, número de faixas/duração abaixo.
- Controles: **Play circular**, shuffle em pílula, coração Salvar e menu. Shuffle permanece desabilitado quando a implementação só consegue embaralhar arquivos baixados.
- Adicionar à fila, baixar tudo e controle de streaming permanecem acessíveis no menu, sem reinventar callbacks.
- Faixas: **índice, título, artista, duração e menu**; não repetir miniaturas de capa. Manter reprodução pelo toque e download no menu.

### Artista — após rolar
- Navegação de abas sticky com fundo grafite opaco e seleção correta por seção realmente visível.
- Organização do catálogo real (Álbuns, Singles e EPs, relacionados e Sobre), sem dados fictícios de shows/apresentações.
- Foco em densidade editorial, fotografia usada onde fornece valor real e espaçamentos consistentes.

### Paleta imutável

`#0E1115` fundo, `#191D23` superfície, `#232831` superfície elevada, `#343944` seleção neutra, `#F4F5F7` texto/ação primária, `#B4B8C4` secundário, `#D4D8E0` iluminação.

## Aceite antes de release/merge
- [ ] Compilação `:app:assembleDebug` e testes pertinentes aprovados no commit final.
- [ ] Capturas reais no Redmi Note 14 de artista aberto, álbum e artista rolado, comparadas lado a lado com a composição aprovada.
- [ ] Conferência de layout do menu e de todas as funções (play, rádio, fila, salvar, downloads, Sobre, navegação, mini-player).
- [ ] Teste de artista sem wordmark, imagem ausente, título longo, download indisponível e album sem tracks.
- [ ] Nenhum merge automático; a primeira implementação é **candidata visual**, não "1:1 validado" sem a comparação.

**Origem das wordmarks:** TheAudioDB (arte da comunidade, API de desenvolvimento) não comprova oficialidade nem licença comercial dos logos. A ausência ou falha continua a mostrar texto.

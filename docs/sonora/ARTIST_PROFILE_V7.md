# Sonora V7 — Perfil de artista, primeira dobra editorial

**Origem:** referência visual “Reimagined Artist Profiles” apresentada no projeto Sonora em 30/10/2026.  
**Implementação:** branch `feat/sonora-artist-album-neutral` (PR #2, draft).  
**Escopo:** perfil de artista somente; a página de álbum, mini-player, navegação, Media3, reprodução e download não mudam nesta rodada.

## Decisões visuais

- O hero cresce de **345 dp para 382 dp**: a fotografia tem protagonismo e o álbum em destaque começa mais baixo. Não há exigência de ver todas as faixas na primeira dobra.
- A fotografia dissolve-se com scrim gradual ao fundo atmosférico derivado da foto. A parte final do hero e o começo do conteúdo usam **exatamente a mesma cor**, evitando uma barra de separação.
- Nome/wordmark e **Play único** permanecem centralizados; o conjunto tem respiro inferior aumentado. Voltar, compartilhar e menu continuam com os seus callbacks reais no topo, sem ações laterais em torno do Play.
- O card de álbum destacado fica **mais translúcido, baixo e sutil**: borda com menor contraste, miniatura 64 dp, ação Abrir álbum menos pesada e espaçamento de 13 dp acima.
- “Mais ouvidas” começa com mais respiro em relação ao card. A tipografia e miniatura de músicas populares são refinadas em um **modo exclusivo do artista** (`artistEditorial`), mantendo o formato da busca e do álbum inalterados.
- Fundo cromático permanece o classificador da V6 (cobertura significativa de tons, fallback grafite para fotografias escuras/monocromáticas). Sem cor fixa por banda, sem IA, sem extensão da paleta às demais telas.

## Arquivos de interface alterados

- `feature/search/src/main/kotlin/com/stash/feature/search/ArtistHero.kt`
- `feature/search/src/main/kotlin/com/stash/feature/search/ArtistProfileScreen.kt`
- `feature/search/src/main/kotlin/com/stash/feature/search/ArtistFeaturedAlbum.kt`
- `feature/search/src/main/kotlin/com/stash/feature/search/PopularTracksSection.kt`
- `feature/search/src/main/kotlin/com/stash/feature/search/SongRow.kt` (novo parâmetro opt-in, default `false`).

## Aceite e limitações

- [ ] `:feature:search:testDebugUnitTest` e `:app:assembleDebug` concluídos sem falhas para a versão 157.
- [ ] Confirmar identificador `com.stash.app.debug`, versionCode `157`, versionName `0.9.121-sonora-artist-v7` e assinatura do APK.
- [ ] Instalar no Redmi Note 14 e comparar o topo, a foto, o card e a dobra com a referência.
- [ ] Testar artistas com foto escura, colorida, monocromática, nome longo, sem logo e sem álbum.
- [ ] Testar Play, Compartilhar, menu Sobre, abrir álbum e rolagem; mini-player deve continuar inalterado.
- [ ] Aprovar visualmente antes de merge. **Um build verde não garante 1:1 visual.**

**Não executado aqui:** composição manual de fotos de artistas e validação em hardware. Fotografias já cortadas pela fonte não podem recuperar integrantes ausentes.

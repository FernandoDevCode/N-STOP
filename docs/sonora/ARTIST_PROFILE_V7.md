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

## APK de teste publicado

- **Release:** [Sonora Artista V7 — 0.9.121](https://github.com/FernandoDevCode/N-STOP/releases/tag/sonora-artist-v7-20261030)
- **Arquivo:** `Sonora-Artista-V7-v0.9.121.apk` (182.520.573 bytes).
- **SHA-256:** `38d7f85ef7a2ed6f3962b9a42a896ec84a7d1741a57618575da0d1f812a88d67`.
- **Commit compilado:** `64c83160bc8dc59945c90e3a081d673779d55a49`.
- **CI:** testes do módulo Search, compilação Android, identidade do pacote, assinatura e publicação do arquivo: **aprovados**. A suíte geral do repositório executa separadamente.
- **Aprovação visual:** **pendente** de captura no Redmi Note 14. Não considerar 1:1 antes dessa comparação.

## Aceite e limitações

- [x] `:feature:search:testDebugUnitTest` e `:app:assembleDebug` concluídos sem falhas no [workflow do APK V7](https://github.com/FernandoDevCode/N-STOP/actions/runs/41088644717).
- [x] Identidade `com.stash.app.debug`, versionCode `157`, versionName `0.9.121-sonora-artist-v7` e assinatura APK verificadas no GitHub Actions.
- [ ] Instalar no Redmi Note 14 e comparar o topo, a foto, o card e a dobra com a referência.
- [ ] Testar artistas com foto escura, colorida, monocromática, nome longo, sem logo e sem álbum.
- [ ] Testar Play, Compartilhar, menu Sobre, abrir álbum e rolagem; mini-player deve continuar inalterado.
- [ ] Aprovar visualmente antes de merge. **Um build verde não garante 1:1 visual.**

**Não executado aqui:** composição manual de fotos de artistas e validação em hardware. Fotografias já cortadas pela fonte não podem recuperar integrantes ausentes.

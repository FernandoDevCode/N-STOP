# SONORA — contrato canônico das fotografias de artistas

## Checklist desta entrega (v195)

- [x] Spotify como autoridade para a fotografia automática de perfil.
- [x] Tentativa autenticada e fallback sem login: MusicBrainz (identidade única) → Spotify Artist API / oEmbed.
- [x] Sem adivinhar identidade a partir de nomes homônimos; Spotify ID persistente quando encontrado.
- [x] Busca de candidatos em Deezer XL e Wikimedia Commons, com comparação de pixels contra a fotografia do Spotify.
- [x] Seleção de outra URL somente para a mesma fotografia em resolução efetivamente maior; nenhum upscaling ou troca por outra pose.
- [x] Manter a foto do Spotify quando não encontrar equivalência melhor; catálogo apenas enquanto o Spotify oficial não pode ser verificado.
- [x] Persistir escolha por ID do artista e registrar qual foto Spotify já teve busca de resolução concluída.
- [x] Revalidar a referência do Spotify apenas na abertura do perfil; não repetir comparação visual enquanto a referência não mudar.
- [x] Mesmo repositório reativo alimenta hero, miniaturas da Busca, Top Result e histórico.
- [x] Preservar foto por URL/importação local e permitir restaurar o padrão.
- [x] Rede indisponível não apaga Spotify já salvo; pesquisa de resolução indisponível não recebe falso estado "checado".
- [ ] Conferir correspondência no Redmi em condições reais (não substituível pelo CI).

## Semântica

1. Spotify decide a fotografia; outras fontes apenas oferecem arquivos que devem passar na comparação visual. Nomes, popularidade, gênero ou estética não autorizam substituir a foto.
2. Um perfil novo só adota uma identidade Spotify se a busca autenticada trouxer exatamente um artista, ou se o MusicBrainz retornar um único artista e um único vínculo oficial ao Spotify. IDs ficam fixos até evidência suficiente de mudança.
3. Fonte Spotify existente é reaproveitada imediatamente. Uma solicitação falha não apaga a referência persistida. Sem foto Spotify verificada, a foto do catálogo continua temporária, não é promovida.
4. Ao receber foto oficial nova, zera a verificação de resolução anterior. Busca de candidatos é acionada uma única vez para cada referência nova, fora da thread principal.
5. A comparação exige proximidade de cores e estrutura de bordas e ganho real de dimensões. Pode rejeitar equivalências reais em caso de cortes distintos (preferimos falso negativo a foto errada).
6. A verificação usa duas fontes públicas sem chave: Deezer (fotografia XL) e Wikimedia Commons (imagens por nome filtradas por comparação). Isto não indexa toda a Web; não confundir com Google Lens/Cloud Vision, que exigiria serviço e credenciais separadas. Nenhuma substituição é fabricada.
7. Uma escolha manual continua prioritária. Restaurar volta à versão automática previamente selecionada (com alta resolução, caso validada).
8. Não adicionar histórico de fotos, buscas gerais automáticas, reconhecimento facial ou alteração de UI.

## Fronteiras

- Spotify: data/spotify/.../SpotifyArtistPhoto.kt e SpotifyApiClient.kt.
- Identidade MusicBrainz: core/data/.../musicbrainz/MusicBrainzClient*.
- Persistência única: core/data/.../artistphotos/SelectedArtistPhotoRepository.kt.
- Resolução sob demanda: feature/search/.../SpotifyArtistPhotoLookup.kt, ArtistPhotoResolutionFinder.kt e ArtistPhotoPixelMatch.kt.
- Publicação em perfil: ArtistProfileViewModel.kt. Busca e histórico não consultam serviços externos para fotos; observam a seleção central existente.
- Testes: core:data, data:spotify, feature:search. O aceite do CI exige compilação, testes e APK assinado do commit integrado na branch nonstop.

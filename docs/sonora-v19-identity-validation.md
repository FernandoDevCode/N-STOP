# Sonora V19 — Seleção automática da identidade do artista

## Política (duas opções)
1. Imagem representativa de alta confiança, sem IA: identidade vinculada por MusicBrainz a uma entidade Wikidata exata. Preferir símbolo oficial (P2238, quando tiver imagem elegível), em seguida logotipo compacto P154 e fotografia representativa P18. Verificar MIME e dimensões via Wikimedia Commons antes de usar. Não escolher imagem somente pelo nome ou pelo número de curtidas.
2. Se nenhuma imagem for elegível, ou se a escolhida falhar ao carregar, executar o **ArtistPhotoLookup** existente sem modificar seu algoritmo: data-piso, categoria/resolução, maior rating, empate livre. Caso o seletor original também não tenha imagem, preservar o avatar já existente no catálogo.

## Implementação
- `core/data/.../ArtistIdentityRules.kt`: identidade, regras e validações puras.
- `core/data/.../ArtistIdentityLookup.kt`: requisições limitadas a MusicBrainz, Wikidata e Commons; busca por demanda, cache isolado, erros de rede não geram cache negativo.
- `feature/search/.../ArtistProfileViewModel.kt`: decisão das duas opções e fallback quando o renderizador falha.
- `feature/search/.../ArtistHero.kt`: símbolos com proporção preservada, fotografias com o layout anterior.
- Sem IA, tabelas de exceção por artista, mudanças em reprodução, catálogo ou no algoritmo original.

## Casos de validação (fontes reais consultadas em 2026-10-31)
- **The Rolling Stones:** Wikidata Q11036 declara símbolo oficial P2238 além de P18; o seletor tenta seguir P2238 primeiro. A seleção real depende da imagem do item associado e do Commons.
- **Queen:** Wikidata Q15862 publica P18 (fotografia de divulgação de 1975, 1798 × 1146), candidata representativa caso as verificações de mídia passem; P154 é um wordmark largo e não é tratado como símbolo de hero.
- **Pink Floyd:** Wikidata Q2306 tem P154 (símbolo vetorial quadrado) e P18 (foto de 1971); um símbolo quadrado renderizado em PNG pode ser priorizado.
- **Nirvana:** Wikidata Q11649 tem P154 em formato horizontal (288 × 106) e P18 pequeno (367 × 346); os dois são inelegíveis, então usa a seleção anterior. Não inventar o famoso smiley sem relação explícita verificável.
- **Sem Wikidata correspondente / arquivo inválido / dimensões ruins:** não presume identidade; fallback.

Os casos acima são verificação documental dos metadados públicos, **não execução ponta a ponta do aplicativo**. Testes automatizados validam seleção/fallback com dados sintéticos e precisam passar em CI. A avaliação cultural de “mais icônica” continua aproximativa, não uma garantia de curadoria perfeita.

## Validação manual antes de lançamento
Conferir fotografias e símbolos reais em Android, especialmente carregamento do Commons, permissões/licenças e legibilidade do nome sobre imagens claras. Não promover branch automaticamente.

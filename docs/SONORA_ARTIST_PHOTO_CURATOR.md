# Sonora — Curadoria visual de fotografias de artistas

## Decisão de produto (2026-11-01)
- Fonte desejada de relevância: ranking **Most popular** da galeria Last.fm.
- Avaliar apenas as **primeiras 20** fotos para escolher uma fotografia colorida,
  expressiva, de composição interessante, sem exigir produção profissional,
  alta saturação, aparência recente ou a formação atual da banda.
- Preto e branco: rejeitar; fotos de cores discretas podem entrar.
- Evitar logo como hero. Preservar imagem inteira, sem recorte no CDN ou na UI.
- **Alta resolução:** depois de decidir qual foto venceu, encontrar o **mesmo
  enquadramento/fotografia** em resolução maior, não outra foto parecida.
- Sem IA local, sem exceções manuais por banda. Seleção deve ocorrer fora do
  caminho de renderização imediato e ser armazenada com procedência.

## O que está implementado
`core:data/artistphotos/ArtistVisualRanker.kt`: algoritmo determinístico,
puro e testável. Recebe lista já classificada pela fonte com classificação
`popularityRank`, dimensões originais e pixels ARGB de uma miniatura limitada
a 256×256. Rejeita monocromia, pontua cor, contraste, detalhe visual e formato,
combina os indicadores com popularidade, desempata de maneira determinística.
Sem downloads, sem requests, sem alteração do hero atual.

Os pesos são hipóteses. Os testes sintéticos garantem invariantes, **não**
demonstram fidelidade ao gosto humano. Deve-se aferir em amostra de fotografias
aprovadas e rejeitadas, especialmente Slipknot, Slaughter to Prevail, Radiohead,
System of a Down e Limão Rosa.

## Bloqueios de integração
1. O Last.fm **não disponibiliza** as fotografias de artista pela API pública
   e seus termos não autorizam coleta por scraping / exploração de artwork.
   Não executar scraping ou contornar medidas de acesso no app. Uma fonte
   autorizada de imagens/ranking precisa fornecer as candidatas.
2. A API Google Custom Search JSON está fechada a novos clientes. Busca
   textual por nome do artista não verifica correspondência da imagem.
3. Uma futura busca de original deve ser por **imagem**: verificar
   equivalência visual/perceptual entre miniatura e original, dimensões,
   licenças e domínio de origem. Google Cloud Vision Web Detection pode
   fornecer full/partial matches quando expressamente contratado; não
   incluir tokens pagos no APK.
4. Se nenhuma candidata autorizada válida existir, preservar o hero/fallback
   existente até uma substituição completa e aprovada.

## Para ativar futuramente
Implementar um provedor autorizado que forneça URL, posição no ranking e
miniatura em memória; processar no IO/worker sob demanda; gravar URL de alta
resolução validada + origem + timestamp; invalidar no botão já existente
"Limpar cache de imagens". Exigir testes reais e validação no Redmi.

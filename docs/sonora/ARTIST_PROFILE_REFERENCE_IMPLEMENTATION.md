# Sonora — Perfil de artista | referência visual final

**Escopo:** apenas o perfil de artista do catálogo online (não alterar a página de álbum).
**Base:** referências finais fornecidas pelo usuário em 29/10/2026, com U2, Pink Floyd, Radiohead, Nirvana, The Strokes e my bloody valentine.

## Composição de referência

1. Fotografia real da banda/artista em largura total e sem cortes abruptos. A imagem é **do artista**, não a capa do álbum.
2. Cabeçalho: voltar à esquerda, compartilhar e opções à direita, círculos escuros sobre a fotografia.
3. Nome central próximo à base da foto; usar wordmark real, quando disponível e com qualidade suficiente. Não gerar wordmark artificial. Fallback: nome tipográfico centrado, respeitando nomes curtos ou longos.
4. Controles alinhados ao centro em três círculos: **Informações / Play / Marcar artista**, com play branco e ícone escuro. Info leva ao Sobre; marcar artista salva uma preferência **local ao dispositivo** (não cria um item de biblioteca e não promete sincronização na conta). Rádio e opções existentes ficam no menu.
5. Primeiro bloco após a foto: card de lançamento real com miniatura, ano, título, total de músicas quando o catálogo informa, e ação **Abrir álbum**. Não há operação de salvar álbum exposta pelo ViewModel do perfil; não simular tal ação com um botão '+' enganoso.
6. Logo abaixo: título 'Mais ouvidas', com faixas reais em ordem recebida do catálogo, número à esquerda, miniatura, título, álbum quando disponível e ⋮ funcional. **Não é requisito exibir cinco músicas simultaneamente:** as demais devem aparecer por rolagem, com mini-player e navegação reais preservados. Sem cards grandes nem controles de download chamativos.
7. Outras seções (Álbuns, Singles, artistas relacionados e Sobre) continuam abaixo, acessíveis pela rolagem; redes permanecem em Sobre. **Sem faixa de abas imediatamente abaixo da foto**.
8. O fundo após a imagem vem da paleta extraída da **foto do artista** via extrator existente do Sonora, com saturação restringida e fallback grafite quando a foto é monocromática/indisponível. Nunca aplicar gradiente roxo fixo ou escolher uma paleta manual para cada banda.
9. Status bar, mini-player e navegação inferior reais do Android devem continuar operacionais; não reproduzir um mock sem esses elementos como se ele fosse screenshot de produção.

## Refinamento após capturas reais da V4 (30/10/2026)

A referência é uma composição 1:1 de **ritmo e proporções**, não uma exigência de cinco músicas visíveis simultaneamente. O conteúdo da captura real do Redmi é o teste de aceite.

- Transição: a imagem da banda deve se dissolver progressivamente em **exatamente** a mesma cor usada atrás do card; a cor de fundo permanece contínua por todo o card antes de aprofundar gradualmente o tom. Sem linha horizontal entre hero e conteúdo.
- Cabeçalho: os controles Voltar, Compartilhar e Menu usam superfície **38 dp**, opacidade escura leve, ícones de 18–20 dp, e *área de toque 48 dp*; sem discos opacos de 48–50 dp. Os círculos ficam nas extremidades como na referência, com distância consistente entre Share e Menu.
- Três controles sob o nome: **42 / 50 / 42 dp**, com espaços simétricos de **28 dp**; play central mantém maior peso visual, mas menos exagerado que os 57 dp anteriores. Nome e controles devem formar um grupo único.
- Sem reabrir as páginas de álbum, Search, mini-player, bottom nav, playback ou download por esses ajustes.
- Preview Android: `0.9.119-sonora-artist-refine` (código **155**). Revisar screenshots reais antes do merge.


## Critérios de aceite
- [x] Estrutura e componentes implementados na branch do redesign.
- [x] Álbum em destaque escolhido deterministicamente a partir do catálogo com teste unitário.
- [ ] CI da implementação final: Kotlin, testes e build Android.
- [ ] Comparação em Redmi Note 14: abertura U2, Pink Floyd, Radiohead, artista com nome longo, artista sem foto/wordmark e rolagem até Sobre.
- [ ] Testar menu, compartilhar, rádio, marcação local, reproduzir, abrir álbum, faixa e downloads em ⋮.
- [ ] Aprovação visual da referência **antes de merge**.

**Limitação declarada:** o screenshot de referência não tem o mini-player/navegação reais. Sem comprometer a acessibilidade e os componentes compartilhados, as proporções internas devem ser as mesmas; o número de faixas visíveis simultaneamente pode variar conforme a altura livre no dispositivo.

**Licenciamento de wordmarks:** a busca experimental TheAudioDB oferece imagens comunitárias e não comprova autorização oficial ou licença para distribuição comercial.

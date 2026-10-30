# Sonora — Plano V6: hero do artista fiel à referência

**Status:** código V6 aplicado à branch de redesign; **CI Android e validação visual no dispositivo ainda pendentes**. Workflow de pré-release V6 já configurado para publicar somente se todos os testes, pacote e assinatura forem aprovados.  
**Referência:** montagem aprovada de U2, Pink Floyd, Radiohead e my bloody valentine, comparada às capturas reais V5 de Queens of the Stone Age, Slipknot e Megadeth (30/10/2026).  
**Branch de trabalho:** `feat/sonora-artist-album-neutral` (PR #2, draft).  
**Escopo:** somente o **hero do perfil de artista** (imagem, logotipo/nome, controles, transição e cor do fundo). Não redesenhar página de álbum, card de destaque, "Mais ouvidas", mini-player nem navegação inferior.

## Diagnóstico confirmado pelas capturas

1. O trio Info / Play / Estrela parece flutuar sobre a fotografia; os botões laterais competem com o play. Na referência a hierarquia é mais silenciosa.
2. Os wordmarks renderizados em `fillMaxWidth(.82f).height(68.dp)` têm presença horizontal insuficiente em vários artistas; aumentar só a altura agravaria o problema.
3. A extração atual mistura `palette.vib` com `palette.dom`, permitindo que um tom quente localizado se espalhe por toda a interface (ex.: laranja/marrom no Slipknot). A decisão cromática precisa refletir a **fotografia inteira**, não um detalhe.
4. `ContentScale.Crop` com `Alignment.TopCenter` pode cortar integrantes nas laterais. `ContentScale.Fit` simples também não é aceitável se criar moldura, achatamento ou grandes barras.

## Ordem de implementação — patches pequenos, fechados

### 1. Composição e ações do hero

**Arquivos previstos:** `feature/search/src/main/kotlin/com/stash/feature/search/ArtistHero.kt`; teste da tela, se aplicável.

- [x] Manter **um único botão central: Play**, circular branco-frio, acionando o callback de reprodução existente.
- [x] **Remover Info e Estrela da faixa central.** Colocar as ações **Sobre** e **Marcar/desmarcar artista neste aparelho** como itens explícitos no menu superior direito já existente. Estado local da estrela preservado; não insinuar que é um favorito sincronizado.
- [x] Manter Voltar à esquerda, Compartilhar e Menu no topo à direita, com superfícies discretas e alvos de toque de ao menos 48 dp; não adicionar segunda fileira de ações.
- [x] Centralizar visualmente o par **wordmark + Play** no terço inferior útil da foto. O deslocamento é definido por padding/medidas consistentes, sem condicionais específicas para artistas.
- [x] Preservar Rádio, site e configuração de streaming no menu. Compartilhar mantém ação real existente.

**Aceite:** só Play compete por atenção no centro; todas as ações anteriores continuam acessíveis; menus sem itens falsos ou duplicados; labels e área de toque preservados.

### 2. Proporção horizontal do wordmark, sem deformação

**Arquivos previstos:** `ArtistHero.kt` e, apenas se necessário, um utilitário local e teste.

- [x] Ampliar **a largura máxima** utilizável do wordmark para aproximadamente **90–94%** da área do hero; **não aumentar automaticamente sua altura** (usar limite próximo dos atuais 68 dp).
- [x] Usar `ContentScale.Fit`/preservação da razão de aspecto; nunca `FillBounds` e nunca cortar letras.
- [ ] Se o asset PNG tiver margens transparentes exageradas, avaliar recorte **somente da área transparente**, com limites conservadores, em memória/cache. Não recortar a arte opaca nem editar arquivos originais.
- [x] Usar fallback tipográfico quando não houver wordmark válido ou o carregamento falhar.
- [ ] Avaliar legibilidade sobre fotografia clara e escura por scrim suave atrás do logo, não com brilho/contorno artificial exagerado.

**Aceite:** wordmarks longos ficam mais presentes nas laterais, sem crescer desproporcionalmente na altura; wordmarks compactos e nomes em texto não estouram a tela.

### 3. Cor de fundo baseada na representatividade da fotografia

**Arquivos previstos:** `ArtistAtmosphere.kt` e testes unitários para classificação de imagens/paleta.

- [x] Substituir a mistura automática de `vib + dom` por uma regra determinística simples: **validar se a cor candidata é representativa de parcela significativa da foto** antes de usá-la como cor ambiente.
- [x] Se a foto for monocromática, majoritariamente escura, tiver baixa saturação ou a cor saturada for apenas um detalhe, escolher **grafite/preto frio** como fundo neutro. Não mapear artista pelo nome.
- [x] Se a foto for genuinamente colorida, derivar o tom dominante, **reduzindo saturação e luminosidade** para manter contraste e evitar fundo de cor viva competindo com a imagem.
- [x] Garantir transição contínua do fim da fotografia para o fundo do card: mesmo tom no pixel final do hero e no início da área de conteúdo, sem faixa horizontal. A mistura deve mudar suavemente quando a foto for trocada/carregada.
- [x] Fallback estável para foto ausente, preto e branco, falha de rede e imagem de capa usada incorretamente como foto.

**Aceite:** Slipknot e Megadeth em fundo neutro/frio, sem laranja artificial; foto azulada do U2 pode manter azul profundo; casos vibrantes mantêm personalidade com tom contido. Não introduzir ajustes por banda.

### 4. Enquadramento de fotografias sem cortar a banda nem achatar imagens

**Arquivos previstos:** `ArtistHero.kt` e utilitário local **apenas se a avaliação de proporção exigir**. Separar mudança de origem/seleção da foto em outro patch, se necessário.

- [ ] Verificar dimensões e proporção da **imagem de origem**, antes de atribuir culpa ao recorte da UI.
- [x] Para imagens largas com enquadramento adequado, usar crop proporcional (`Crop`) e ponto focal conservador; nunca esticar.
- [x] Quando o corte horizontal remover pessoas nas bordas, usar composição **duas camadas da mesma foto**: fundo ampliado/desfocado e escurecido + fotografia principal proporcionalmente reduzida, com bordas dissolvidas no fundo. Não deixar retângulo rígido nem barras de letterbox evidentes.
- [x] Se a fonte já vier cortada, o layout **não inventa integrantes ausentes**: registrar limitação e tratar melhoria de seleção de imagens em tarefa separada (fontes confiáveis, sem IA, com cache).
- [ ] Testar banda com muitos integrantes, duo, artista solo, foto vertical, horizontal e foto com texto promocional embutido.

**Aceite:** ninguém fica deformado; reduzir cortes laterais sem reduzir a foto a um cartão pequeno; falhas de fonte são identificadas em vez de mascaradas.

## Registro de implementação

- **Patch A/B/D:** `ArtistHero.kt` — Play único, secundários no menu, wordmark em 94%, `FillWidth` no primeiro plano e segunda camada suavizada em Android 12+.
- **Patch C:** `ArtistAtmosphere.kt` — classificação por cobertura e predominância da foto, fallback neutro; testes novos em `ArtistAtmosphereTest.kt`.
- **Transição:** `ArtistProfileScreen.kt` — `animateColorAsState` exclusivo do fundo do artista.
- **Versão de prévia:** `0.9.120-sonora-artist-v6`, versionCode `156`, `com.stash.app.debug`.
- **Workflow:** [Sonora Artist V6 Preview](https://github.com/FernandoDevCode/N-STOP/actions/workflows/sonora-artist-v6-preview.yml); publicação condicionada a testes Android, Workers, build, pacote e assinatura.
- **Limite:** a foto de origem já cortada não pode recuperar integrantes ausentes; recorte de margens transparentes de wordmarks continua opcional e depende de evidência visual.
- **Status de aceite:** checkboxes de implementação marcados não equivalem a aprovação visual; V6 somente será aprovada após capturas do Redmi.

## Invariantes / fora de escopo

- A linguagem visual continua Sonora: fundo grafite, branco-frio e atmosfera retirada da imagem do artista, não do álbum ou do mini-player.
- Não mexer em `AlbumHero.kt`, página de álbum, `ArtistFeaturedAlbum.kt`, lista "Mais ouvidas", Home, Search global ou bottom navigation.
- Não mudar Media3, reprodução, fila, downloads, autenticação, rádio ou integração de catálogo.
- Não gerar imagens de artistas/logos com IA; não atribuir oficialidade a wordmarks comunitários.
- Não adicionar dependências pesadas, pipelines de visão computacional ou serviços novos apenas para corrigir o crop.
- Não otimizar para caberem cinco músicas na primeira dobra. A lista continua acessível por rolagem.
- Não criar release nem fazer merge só porque um teste compilou; a decisão de aprovação permanece visual e funcional.

## Critérios de validação e evidências

- [x] Testes de unidade para seleção da cor: foto monocromática; foto escura com pequeno detalhe quente; foto realmente azulada; foto predominantemente colorida; imagem sem dados.
- [ ] Testes de proporções/fallback do wordmark e manutenção das ações no menu.
- [ ] `:app:assembleDebug` e testes relevantes verdes.
- [ ] Capturas reais do Redmi Note 14: **Slipknot**, **Megadeth**, **Queens of the Stone Age** e **U2**, observando especialmente fundo/crop/wordmark e botões.
- [ ] Comparação lado a lado com a referência do usuário; anotar divergências por item, sem declarar “1:1” antes de verificar.
- [ ] Regressão manual: Play, Compartilhar, Sobre, marcação local, Rádio, menu e rolagem; mini-player não modificado.
- [ ] Após CI verde, incrementar a versão e publicar **um único APK de prévia identificável** para obter screenshots do Redmi. **Aprovação visual/funcional vem depois da instalação**, e o PR #2 permanece em draft até essa aprovação.

## Sequência de trabalho

**Patch A:** ações e equilíbrio do hero → **Patch B:** wordmark → **Patch C:** seleção cromática → **Patch D:** enquadramento por proporção → **CI e testes automatizados** → **APK de prévia** → **screenshots reais e aceite visual/funcional**. Cada patch deve respeitar os arquivos permitidos e deixar a aplicação compilável. Não iniciar mudanças em álbuns nesta rodada.

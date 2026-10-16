# NØNSTOP — Player v7: especificação visual fechada

Estado: aprovado para implementação da interface, sem alteração de backend/áudio.
Branch de trabalho: `nonstop`. Escopo: **player de música expandido**; o player de podcasts fica fora.
Base real: `NowPlayingScreen.kt`, `NowPlayingActionsUi.kt`, `LiveLyricsBar.kt`.

## Objetivo

Transformar o player atual de painel de ações em uma experiência de escuta
imersiva. A capa e o ambiente visual precisam formar uma composição, não um
card solto sobre um fundo escuro. O transporte deve continuar simples de usar.
As letras entram como continuação vertical do player, como conteúdo editorial
tocável, e não como uma fileira adicional de ícones.

## Estrutura final — de cima para baixo

1. **Topo compacto**: fechar à esquerda, Cast contextual se disponível,
   ações personalizadas existentes somente quando couberem sem congestionamento,
   menu `⋮` à direita. Acessibilidade mínima de 48dp de toque.
2. **Arte protagonista**: quadrada, centralizada, com raio de canto de 14–16dp.
   Largura útil = largura de tela − 48dp; teto de 375dp. Reduzir a arte
   proporcionalmente apenas quando necessário para preservar controles/preview.
3. **Título e identificação**: alinhados à esquerda da capa, título de 24–26sp
   até duas linhas; artista · álbum em 13–14sp, uma linha, truncado.
   Coração de curtida na mesma área, mas como ação independente. Acesso ao
   artista pelo bloco de título permanece funcional.
4. **Progresso**: trilho fino com posição e duração, cor acentuada pela arte,
   seek acessível. Mantém estado de escuta compartilhada e bloqueios reais.
5. **Transporte**: aleatório, anterior, play/pause, próximo, repetir. Botão
   principal opticamente centrado, botões secundários discretos.
6. **Preview de letras**: cartão de vidro sutil de 18dp de raio com cabeçalho
   “Letras” e affordance de expansão. Até **duas linhas reais** de texto:
   linha atual em destaque e próxima atenuada, quando há sincronização.
   O cartão inteiro abre a tela completa de letras já existente.

Não adicionar uma barra fixa de botões sob o transporte. Não mostrar
`Up Next` como uma segunda faixa permanente sob os controles:
o acesso à fila continua disponível pelo topo/menu. Quando não houver
letras apresentáveis, o cartão desaparece e a capa ocupa o espaço liberado.

## Menu de ações secundárias

O menu `⋮` concentra entradas acessíveis para **Fila, Áudio/diagnóstico,
Temporizador e Ouvir Juntos**, respeitando as condições atuais em que cada
ação pode estar indisponível; manter ainda salvar, compartilhar, baixar,
rádio e demais ações existentes. O temporizador sai da fileira de título
e do cabeçalho da leitura de letras; suas opções existentes continuam iguais.
Preservar as preferências de ações fixadas dos usuários, sem redefinir suas
configurações armazenadas — ações que não couberem migram ao overflow.

## Atmosfera / composição

- Fundo AMOLED/escuro: base grafite muito profunda, próxima de `#06060C`.
- Fundo claro: preservar lavagem pastel e contraste com tinta `#241C36`.
- Usar a paleta dinâmica já extraída da capa. Preferir uma halo difusa,
  assimétrica e orgânica atrás da capa, com intensidade controlada, em vez
  de um retângulo de sombra claramente separado do artwork.
- Capa com scrim/contorno muito discreto; tipografia limpa e alto contraste.
- Cartão de letras translúcido, gradiente vertical suave da cor da capa para
  a base, borda da cor de destaque com baixa opacidade; não competir com a arte.
- Nenhum brilho forte ou permanente em múltiplos elementos simultaneamente.

## Regras do preview

- Letra sincronizada disponível + visualização ao vivo ativada:
  mostrar trecho atual e próxima linha real, se existir; preservar TTML
  por palavra e LRC por linha, inclusive pausas instrumentais.
- Letra sem timestamps: exibir até duas linhas iniciais, sem fingir
  sincronismo. Tocar abre a letra completa.
- Visualização ao vivo desativada: manter preview estático tocável,
  nunca sincronizar à força contra a preferência do usuário.
- Instrumental, ausência confirmada, carregando ou erro: não renderizar
  letras inventadas, texto genérico cantável nem um cartão vazio.
- Linhas longas: 1 linha visível por trecho, com truncamento visual;
  conteúdo integral acessível na tela completa.
- O preview usa exatamente o mesmo estado e relógio de letra existentes.
  Nunca iniciar requisições adicionais por conta da renderização.

## Responsividade e animação

- Telas comuns: preview com título + duas linhas; capa recebe o espaço restante.
- Altura reduzida, landscape ou fonte ampliada: reduzir margens decorativas
  primeiro; depois diminuir capa; se ainda necessário, mostrar só uma
  linha no preview. Nunca cortar botões ou sobrepor barra do sistema.
- Respeitar a configuração Android de redução de movimento: nenhum
  shimmer/glow pulsante obrigatório, transições instantâneas quando ativado.
- Entrada e saída do cartão: fade discreto de até 240ms. Mudanças de
  faixa seguem a transição de paleta já implementada, sem efeitos duplicados.
- Preservar as transições atuais arte ↔ tela de letras e as ações do player.

## Limites da implementação

Alterações previstas:
- `feature/nowplaying/src/main/kotlin/com/stash/feature/nowplaying/NowPlayingScreen.kt`
- `feature/nowplaying/src/main/kotlin/com/stash/feature/nowplaying/ui/LiveLyricsBar.kt`
- `feature/nowplaying/src/main/kotlin/com/stash/feature/nowplaying/NowPlayingActionsUi.kt`
- `feature/nowplaying/src/main/res/values/strings.xml`
- `feature/nowplaying/src/main/res/values-pt-rBR/strings.xml`
- Somente se inevitável, ajustes na lógica **de apresentação** em
  `NowPlayingActions.kt`.

Não modificar: `NowPlayingViewModel`, player/streaming, downloads,
persistência, login, Spotify/YouTube, catálogo de músicas, fila real,
sincronização compartilhada ou modelo de dados. Não trazer conteúdo mockado
e não instalar APK intermediário.

## Aceite

1. Topo sem congestionamento; todas as ações atuais continuam encontráveis.
2. Arte e fundo formam composição consistente em tema escuro e claro.
3. Coração não intercepta toque do título; abrir artista e curtir funcionam.
4. Fila, diagnóstico de áudio, timer e Ouvir Juntos abrem os destinos reais.
5. Progresso, play/pause, seek, anterior/próxima, shuffle e repeat preservados.
6. Preview apresenta apenas letras reais em até duas linhas, sem atrapalhar o
   transporte; entra/sai corretamente por estado e ao trocar de música.
7. Toque no preview abre o leitor completo, preservando sincronismo e retorno.
8. Responsivo com telas curtas, fontes maiores, temas e redução de movimento.
9. Testes do módulo e CI Android passam. Sem solicitação de instalação até
   o checkpoint final do produto.

**Ponto de implementação**: não redesenhar serviços existentes nem criar
novos botões duplicados; adaptar os componentes de UI já encontrados no repo.

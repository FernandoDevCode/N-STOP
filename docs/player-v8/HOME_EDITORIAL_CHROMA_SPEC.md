# NØNSTOP — Home Editorial + paleta contextual v1

**Status:** trabalho incremental; aprovação estética e validação Android pendentes.
**Branch:** `feat/nonstop-home-adaptive-v1`.
**Prioridade:** não mexer no Player v8, Media3, mini-player funcional, downloads, persistência ou autenticação.

## Decisões do produto
- Home escura e neutra em todas as músicas; sem roxo ou verde fixo.
- Home não tem logo nem saudação, conforme última referência aprovada.
- Categorias superiores: **Música / Podcasts / Audiobooks**, sem “Tudo”.
- Abas inferiores **Início · Explorar · Rádio · Buscar · Biblioteca**, Rádio na terceira posição.
- Manter mini-player real com sua geometria e interação existentes; mudanças de cor devem ser mínimas.
- Cards, álbuns, playlists, artistas, recomendações e capa do hero só podem vir do catálogo/repositórios reais. **Não fabricar dados ou arte**.
- Preservar preferências do usuário para ocultar/ordenar seções quando disponíveis.

## Home final: hierarquia editorial desejada
1. Topo funcional discreto, categorias de mídia.
2. Atalhos compactos baseados em dados reais (mais ouvidas, descobertas, chill, focus) apenas quando houver origem real para cada tipo; senão não exibir placeholders.
3. Banner / hero real em destaque com título, capa, subtítulo e controle, carrossel se mais de um.
4. **Feito para você:** playlist/mix reais.
5. **Redescobertas:** artistas históricos reais e imagens disponíveis, avatar circular. Não afirmar que existe histórico se ele não é coletado.
6. **Novidades que combinam com você:** catálogo real e recomendações legítimas; se houver apenas “Novos lançamentos”, manter rótulo real.
7. Outros trilhos reais organizáveis, sem duplicação.
8. Mini-player fixo existente acima da barra inferior.

## Identidade fixa
- Fundo preto/grafite neutro (#08090B a #090B0D).
- Texto primário branco, secundário cinza.
- Ícones inativos neutros.
- Cards não herdam globalmente as cores da música.
- Banner editorial pode ter sua própria imagem/paleta **restrita ao próprio banner**.
- Não usar tratamentos metálicos, glows RGB ou caixas coloridas por toda parte.

## Identidade dinâmica, fonte única
- Reutilizar `AlbumColorSource` e `LocalAlbumChrome` já existentes.
- Acento da capa atual somente no chip superior ativo, ícone e legenda da aba inferior ativa, realce sutil no card de continuar e componentes existentes do mini-player.
- Limitar opacidade da wash de fundo a ~0.16 e da borda ativa a ~0.7.
- Cor não muda ao pausar; sem capa voltar a cinza neutro.
- Cores editadas suavemente, respeitando redução de movimento; alvo futuro de 450ms onde isso não degradar a UX.
- Atenuar saturação/luminância quando necessário; nunca sacrificar contraste da seleção.

## Testes / validação pendentes
- Compilar `assembleDebug` e executar testes direcionados no Windows/Redmi Note 14.
- Verificar screenshots Android com capas azul, quente, escura e sem capa, com play e pause.
- Verificar ordem/semântica/rotas de 5 abas, foco e TalkBack.
- Verificar última seção acima da barra inferior/mini-player e o scroll do banner.
- Verificar se o acesso a podcasts, audiobooks, configurações, streaming e comunidade permanece funcional.
- Verificar diversidade dos dados da Home, sem hero falso ou seção vazia.

## Entregue no primeiro commit desta branch
- Filtros de mídia e chips com acento contextual em vez de roxo fixo.
- Aba inferior com cor da capa, glow no traço em vez de preenchimento RGB e nova ordem das cinco abas.
- Card “Continue ouvindo” com base neutra e acento real.
- Home sem logotipo e sem mensagem de saudação.
- Atalhos compactos de mixes reais, prontos e com capa disponível, logo após os filtros (sem dados falsos).
- Hero existente (dados reais) promovido para depois da escuta atual.
- Código Android *não compilado nem validado visualmente* neste ambiente remoto.

## Próxima implementação (não fingir pronta)
- Verificar e refinar atalhos compactos reais no topo (primeira versão já criada com listas reais de mixes).
- Criar seção “Redescobertas” apenas se houver dados reais de artistas escutados.
- Refinar tamanhos de imagens, tipografia e densidade como na referência aprovada, mantendo scroll fluido.
- Capturar screenshots do app real para aprovação estética.

## Revisão editorial incremental (2026-10-19)
- Home: card grande com capa real do Discover/mix, scrim para texto, navegação e play separados; mantém fallback de carregamento existente.
- Explorar: primeiro lançamento real lidera com banner editorial clicável; botão explicitamente **Abrir** e demais lançamentos aparecem no trilho sem duplicação.
- Rádio: estação real com arte e mix READY lidera com botão **Ouvir** (playMix); demais rádios no trilho existente.
- Buscar: campo neutro e foco cromático discreto seguindo a música atual.
- Biblioteca: cabeçalho e ação de shuffle com acento cromático real, sem aplicar wash forte ao catálogo.
- Nenhuma dessas mudanças representa player v8 final no Android; build, screenshots e teste manual continuam necessários.

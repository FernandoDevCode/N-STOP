# SONORA — vidro e trilho assimétrico

Implementação de 28/10/2026, baseada na referência visual enviada na conversa.

## Alterações

- `MiniPlayerRail.kt`: arco inferior esquerdo concêntrico ao card, raio interno de 10,5dp, horizontal em altura−2,5dp e término reto calculado na interseção com o canto interno direito. Traço de 2,5dp com arremate Butt. O término fica aproximadamente 7,7dp antes da lateral; a agulha não sobe à direita.
- Posição, tangente, comprimento, projeção do toque e geração de segmentos usam a mesma métrica analítica. PathMeasure fica apenas no teste independente de concordância com o desenho.
- `MiniPlayerProgress.kt`: mantém ownership dos gestos e fronteira superior aprovada do corpo, mesmo após mover o trilho 2,5dp para dentro. Prévia durante arrasto, commit no up, cancelamento sem seek.
- `MiniPlayerFriction.kt`: utiliza a nova geometria para contato e tangente; física, taxas, vidas, capacidade, faixa de desenho e halos da agulha preservados.
- `MiniPlayer.kt`: fundo circular do Play/Pause removido. Ícones e targets de 48dp, buffering e semântica mantidos.
- `SonoraChromeSurface.kt`: somente `sonoraMiniGlass` alterado. Tint grafite de 60%, blur de 20dp, fallback de 97% e contorno de 0,5dp com reflexo localizado. Fonte Haze existente no scaffold preservada. A função de fade inferior não foi alterada.
- Testes: MiniPlayerRailTest, MiniPlayerRenderTest e MiniPlayerInteractionTest atualizados. SonoraStage3SurfaceTest e uma dependência apenas de testes em feature/home permitem capturar Home e AlbumDetailScreen reais com o mini-player.

## Verificação

24 testes `*MiniPlayer*Test` passaram: geometria/projeção em múltiplas larguras/densidades, comparação com PathMeasure, amostras 0/1/25/50/75/99/100%, agulha circular visível, controles, abertura, seek, cancelamento, multitouch, acessibilidade e partículas/movimento reduzido.

4 testes SonoraStage3SurfaceTest passaram: 360×640 e 393×873 com fontes 1×/1,5×. Capturas de Home, Home rolada, álbum e placas de estresse clara, escura e colorida. `:app:assembleDebug` passou.

O ponto no centro da largura corresponde agora a aproximadamente 51,6% do comprimento, consequência correta da assimetria. O teste de toque usa a projeção da coordenada, não pressupõe 50%.

## Evidências locais

- Comparativo Home (antes à esquerda): `build/mini-player-asymmetric/home-antes-depois.png`.
- Comparativo 100% (antes acima): `build/mini-player-asymmetric/trilho-100-antes-depois.png`.
- Sequências representativas: `build/mini-player-asymmetric/reproducao.gif` e `arrasto-rapido.gif`.
- Amostras e frames: `feature/nowplaying/build/outputs/mini-player-render/`.
- Integração: `feature/home/build/reports/sonora-stage3/`, incluindo `album-mini-player` nas quatro combinações.
- Log final: `build/mini-player-asymmetric/validation.log`.

## Limites da validação

`adb devices` não encontrou aparelho conectado; não há emulador configurado. As capturas Robolectric são renderizações locais de composição/fallback e as animações são sequências representativas, não gravações físicas. As placas de estresse são fundos sintéticos, não fotografias de capas reais.

A hierarquia Haze permanece ligada ao conteúdo real no app, mas a translucência acelerada por GPU não foi comprovada nesta rodada. O ajuste de tint a 60% segue o plano; a aprovação artística do material sobre capas reais, com texto de fundo ilegível, continua dependente de teste Android com GPU/Redmi. Não inferir essa aprovação das capturas de fallback.

APK: `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: `2C4F4589A13437C4CED32062A78961CE18A6FA401A485773C863066EACC6CA15`.

Nenhuma alteração de produção em Home, navegação, fade, Player V8 ou reprodução. Sem commit/push/merge.

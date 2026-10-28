# Procedural Art Director no Android

Este módulo compila os mesmos arquivos de `labs/art-lab/src/commonMain/kotlin`
usados pelo Desktop: features, direção, receita, atmosfera e renderer V8.
Não copia o renderer nem acrescenta decisões por artista/álbum. Catálogo, fixtures,
inferência e código Desktop ficam fora do source set Android.

O host de produção é `feature/nowplaying/.../V8PlayerSurface.kt`:

- `NowPlayingViewModel` continua controlando a única infraestrutura de áudio.
- `AndroidArtPipeline` usa Coil para a capa completa, limitada a 768 px; analisa
  uma cópia proporcional de 256 px em worker. O hash dos pixels normalizados,
  versão do diretor e política determinam o cache.
- Até três atmosferas em memória e 32 entradas em `cacheDir/player-v8`.
  Receita e pixels persistidos têm checksum conjunto. Entradas inválidas são
  recalculadas; falha de escrita não impede a reprodução.
- Apenas a composição final de 192×342 fica na memória Android. Os campos de
  diagnóstico apontam para ela, pois o aplicativo usa somente `DebugView.FINAL`.
- Mudança de capa cancela a corrotina. A geração verifica cancelamento por linha;
  um mutex evita gerações simultâneas. Não há processamento pesado por frame.
- Capa ausente usa receita neutra. Exceções recuperáveis de inicialização,
  composição, medição, layout e desenho acionam o V7. O menu permite escolhê-lo.
- Letras reais, sincronização, fila, ações e sessões compartilhadas mantêm suas
  implementações Android. Letras de teste existem apenas em `desktopMain`.
- O fundo é estático. Microinterações respeitam visibilidade e redução de movimento.
  Não há fonte PCM conectada, pulso fictício, IA, modelo neural ou segundo player.

Compilar não comprova desempenho ou funcionamento em dispositivo. A validação
da integração está em `docs/SONORA_ANDROID_INTEGRATION.md`.

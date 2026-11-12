# Sonora — pré-release experimental do Player V8

Checkpoint de distribuição para teste no Redmi Note 14, sem merge na branch `nonstop`.

Escopo aprovado para validação:
- Timeline óptica V2, glow 100% e agulha na cor local da faixa;
- controles brancos minimalistas;
- transição atmosférica em U, enquadramento de capa preservado;
- partículas ligadas à energia PCM real, com leitura limitada e não destrutiva.

O workflow `sonora-experimental-release.yml` compila e testa novamente antes da publicação, verifica assinatura e pacote e produz uma *prerelease* isolada com tag `sonora-exp-<sha>`.

Validação no aparelho pendente: visuais de capas monocromáticas/multicoloridas/neutras, 16:9 e 20:9, gesto de seek, fluidez e áudio USB/Bluetooth. Uma pré-release não representa aprovação visual nem autorização de merge.

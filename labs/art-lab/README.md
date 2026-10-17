# NØNSTOP Art Lab v8 — Fases 0 e 1A

Laboratório Kotlin + Compose Multiplatform independente do build Android de produção.
A norma continua sendo `../../docs/player-v8/PLAYER_V8_ART_LAB_SPEC.md`.
Estado: esqueleto Desktop funcional; **aceite visual pendente / NOT PASSED**.

## Executar no Windows

Na raiz do repositório, com JDK 21 no `JAVA_HOME`:

```powershell
.\gradlew.bat -p labs/art-lab run
```

Primeira execução precisa de rede para resolver dependências Gradle/Maven. O app não usa streaming,
conta, serviços externos, pesos de IA ou módulos de produção. O wrapper existente é reutilizado;
`settings.gradle.kts` e dependências deste laboratório são independentes.

```powershell
# Testes direcionados
.\gradlew.bat -p labs/art-lab desktopTest
# Executar e capturar o renderer real em vários estados (a janela permanece aberta)
.\gradlew.bat -p labs/art-lab run --args=--capture-suite
# Gerar pasta de aplicativo Windows com runtime Java incluído
.\gradlew.bat -p labs/art-lab createDistributable
```

Executável gerado: `build/compose/binaries/main/app/NonstopArtLab/NonstopArtLab.exe`.
Para abri-lo com as saídas dentro do laboratório, partindo da raiz:

```powershell
Push-Location labs/art-lab
& .\build\compose\binaries\main\app\NonstopArtLab\NonstopArtLab.exe
Pop-Location
```

Mantenha **toda a pasta** `NonstopArtLab`, incluindo `app` e `runtime`, ao copiar o executável.
Não foi criado instalador MSI nem assinatura de distribuição.

## Interface implementada

- Galeria fixa de 12 entradas; duas disponíveis como **recortes dos mockups**, dez `missing`.
  Nenhuma aquisição de capa externa foi feita. Manifesto gerado em `build/exports/catalog-manifest.json`
  inclui proveniência, checksum do recorte e checksum do mestre.
- Player Compose: arte quadrada com crop, topo, progresso, título até duas linhas,
  artista/álbum, coração independente, transporte e alça LETRAS.
- Play/pause, favorito, aleatório e repetir alternam estados demonstrativos. Anterior/próxima
  mudam a capa; seek aceita clique/arraste e presets 0/25/50/100%. Não há reprodução de áudio.
- LETRAS contém apenas “Linha de teste 1/2/…” e indicação de simulação.
- Carregar PNG/JPEG local sem rebuild (adiciona uma entrada fora das 12 do benchmark).
  Arquivo inválido mostra causa e fallback, sem crash. Cancelar o diálogo não muda a seleção.
- Diagnóstico ocultável, exportação da receita JSON, depuração por camadas, fonte 100/150%,
  viewports 864×1536, 941×1672, **1080×2340** e 720×1280.
- Preview ajustado à janela ou **100% dos pixels** com scroll. Capturar exporta o layer do
  próprio renderer Compose em execução, na resolução selecionada, antes da escala de apresentação.
  As imagens não são um renderer alternativo nem a referência colada como fundo/player.
- Render estático: redução de movimento ativa; não há toggle de animação que finja funcionar.
  Transição animada, pipeline de features e direção por capa pertencem às fases seguintes.

## Separação para Android futuro

`commonMain` contém `catalog`, contratos `features`/`inference`, `direction` e `renderer`.
`commonTest/.../validation` contém testes de contrato. Fontes Inter regular/semibold são
cópias dos assets já presentes em `core/ui`; o laboratório não depende desse módulo.

`desktopMain` contém janela, picker, decode PNG/JPEG, textura reduzida/desfocada uma vez por
hash, cache em memória e exportação de capturas/manifesto. Esses adaptadores JVM/AWT serão
substituídos por adaptadores Android; **não há target Android nesta fase**. `V8Renderer`
recebe bitmaps, receita e estado por parâmetros; não lê arquivos nem chama modelo/feature extractor.

`ArtRecipe` é um contrato inicial serializável com fallback estável, não o motor da Fase 1B.
Todas as capas usam os mesmos parâmetros neutros. Nenhuma decisão visual depende de artista,
álbum, índice da galeria ou hash conhecido. O hash serve somente à identidade/cache.

## Evidências e limites

Medidas, auditoria, testes executados e diferenças: `../../docs/player-v8/reference-measurements.md`.
Capturas da sessão: `../../docs/player-v8/validation/phase-1a/`.
Suite completa e relatórios JUnit/HTML: `build/captures/` e `build/reports/tests/desktopTest/`.
Após terminar a suite, `./validation/Compare-Captures.ps1` preserva as capturas, monta
comparações na mesma escala e verifica uma coordenada fixa da barra nos estados de seek.

**IA indisponível — fallback**. Nenhum modelo foi baixado/executado. Não há escores, confiança
ou features inventadas. O cache é de bitmaps/textura, não um cache persistente de inferência.
Timings exibidos são elapsed wall time de decode + textura; não são CPU time nem benchmark de FPS.

Iluminação cinematográfica, gradação por capa, persistência/invalidação de receitas e inferência
não foram implementadas. A aparência não está 1:1 e aguarda validação do usuário.

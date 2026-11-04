# Sonora — estado canônico e consolidação (2026-11-01)

## Regra operacional
- Repositório: `FernandoDevCode/N-STOP`.
- **Branch principal existente: `nonstop`** (não existe branch `main` nesse fluxo).
- O checkpoint consolidado é **V22.2 — `0.9.141`, versionCode 177**; histórico contém as entregas anteriores.
- Esta consolidação foi feita **sem rebase, squash, cherry-pick indiscriminado ou force-push**: a linha `nonstop` precisa avançar somente por *fast-forward* do último checkpoint auditado.
- Futuras tarefas: partir de `nonstop` → PR com base em `nonstop` → testar → merge → publicar APK do commit integrado. Não usar PRs encadeados entre branches de prévia como mecanismo de releases.
- As branches antigas e tags permanecem acessíveis como histórico, mas não são fontes alternativas da verdade.

## Incluído no checkpoint V22.2 (histórico de commits)
- Home Sonora, Explorar, Rádio, Buscar e Biblioteca, incluindo ordem de navegação e chrome.
- Player e mini-player, inclusive integração Android de `core:art` / Art Lab V8 com a experiência real do app.
- Perfis de artistas/álbuns, cores, wordmarks **apenas secundários**, botão de vidro fumê e navegação do perfil.
- Capítulos de audiolivros, tradução e datas PT-BR, correções de janela/barra de status.
- Seletor fotográfico: prioridade fotográfica P18 verificada, seletor tradicional preservado com data-piso/resolução/maior rating, e fallback de catálogo.
- Hero do artista com `Fit` sem novo recorte de imagem e fade inferior em U. O cache da busca icônica foi versionado.

## Evidências de integração e divergências avaliadas
| Branch histórica | Avaliação |
| --- | --- |
| `feat/nonstop-v8-art-lab`, `feat/sonora-v16-integration-chrome-photos`, `feat/sonora-v17-ptbr-dates-copy`, `feat/sonora-v18-transparent-statusbar`, `feat/sonora-v19-iconic-artist-identity`, `fix/sonora-v20-artist-identity-statusbar`, `fix/sonora-v21-artist-hero-statusbar`, `fix/sonora-v22-iconic-artist-hero` | Ancestrais do checkpoint V22.2. Alterações posteriores prevalecem; símbolos como hero, por exemplo, foram revertidos. |
| `feat/sonora-rated-artist-photos` | Divergência de commits, mas os blobs dos arquivos `ArtistPhotoLookup.kt`, `ArtistPhotoRules.kt` e `CommonsCaptureDate.kt` são idênticos aos do checkpoint. Não reintegrar commits antigos. |
| `fix/sonora-v18-statusbar-continuity` | Divergiu por evolução posterior, porém o background da Home é idêntico e o Scaffold/Activity do checkpoint contêm a lógica de transparência e as correções mais recentes da rota de artista. Não sobrescrever o código atual. |
| `research/sonora-apple-artist-assets` | Apenas pesquisa de ativos Apple, sem patch de produto pendente identificado. |
| `feat/nonstop-home-adaptive-v1` | **Alternativa experimental não integrada**: nove commits, com `HomeQuickMixRow`, `HomeRediscoveryRow`, `NonstopEditorialHero` e Home reestruturada. Não se estabeleceu aprovação desta versão como sucessora da Home consolidada; a importação automática seria regressão potencial. Preservar branch para avaliação de produto específica, sem merge. |

## Qualidade e pendências honestas
- GitHub Actions: [V22.2 APK assinado e publicado](https://github.com/FernandoDevCode/N-STOP/actions/runs/41275721328) (**success**), [testes de módulos e build Debug](https://github.com/FernandoDevCode/N-STOP/actions/runs/41275721301) (**success**). Sucesso de compilação não valida UX no aparelho.
- V22.2: [pré-release Debug](https://github.com/FernandoDevCode/N-STOP/releases/tag/sonora-v22-2-photo-u-fade-20261101); não substituir pela release assinada de produção do Stash.
- **Pendente de validação visual:** foto efetiva do Slipknot, recorte existente na **imagem de origem**, fade em U e aparência da barra de status no Android 16/HyperOS. Não declarar esses aspectos resolvidos só por testes unitários.
- O Art Lab V8 foi integrado tecnicamente; igualdade visual 1:1 das referências e teste real abrangente de reprodução/gestos continuam sujeitos à validação por dispositivo.
- Fluxos antigos `.github/workflows/sonora-v*.yml` publicados anteriormente são históricos; não devem disparar novas releases fora de `nonstop`.

## Pré-release única de desenvolvimento
- No GitHub: **Actions → Sonora — APK de desenvolvimento (canônico) → Run workflow → branch `nonstop`**.
- O workflow `.github/workflows/sonora-preview.yml` só permite execução em `nonstop`, executa os testes dos módulos, monta o APK Debug, confere o pacote e a assinatura, e publica uma pré-release vinculada ao SHA exato. Não dispara a cada commit.
- As 18 workflows antigas de pré-release foram **retiradas apenas da branch principal**; suas versões permanecem no histórico/tags.
- Um APK publicado anteriormente (incluindo V22.2) não é reescrito. O fluxo canônico produz novo artefato imutável por commit.
- Para atualizar o checkout Windows sem perder trabalho local: verificar `git status`, depois `git fetch origin`, `git switch nonstop` e `git pull --ff-only origin nonstop`. Se houver alterações locais, revisar antes de trocar de branch.

## Evitar reincidência
1. Trabalhar em `nonstop` como destino de todas as entregas. Branch temporária pode existir durante implementação, mas **nunca** vira nova base permanente para a tarefa seguinte.
2. Para checar implementação de uma funcionalidade, inspecionar **código de `nonstop`**, não apenas arquivos de releases, prints, tags ou PRs abertos.
3. Verificar testes e revisar resultados antes de declarar entrega. Testes de interface não substituem validação no Redmi.
4. Não apagar tags/releases/branches históricas apenas por limpeza visual; fechar PRs obsoletos com registro, mantendo rastreabilidade.

## Proposta não integrada — descoberta semanal (PR #60, 2026-11-04)
- **Branch de trabalho:** `feat/sonora-weekly-discovery-engine-20261104`, PR para `nonstop`. Não considerar esta seção prova de integração.
- Três conjuntos: lançamentos Spotify verificados e resolvidos via YouTube Music; sugestões da fase recente; sugestões de estilos presentes historicamente, mas subexpostos nos últimos 30 dias.
- Taste scorer usa biblioteca sincronizada de todas as fontes, `track_tags` persistidos e `listening_events` locais (sem confundir música salva com reprodução). Se faltarem tags, infere uma amostra limitada dos artistas via Last.fm.
- Candidatos de faixa vêm do Last.fm por tag e são confirmados contra identidade de música do YouTube Music; excluir faixas já na biblioteca, duplicatas entre seções e rejeições explícitas. No máximo dez resultados por seção, dois por artista.
- Home contém até três outdoors paginados e os trilhos equivalentes; Daily Discover mantém ações em cartão compacto. Feedback `Não foi pra mim` pode ser aplicado por gesto de pressão longa e persiste localmente.
- Cache privado por semana inclui somente metadados de catálogo e fingerprint da biblioteca; não guarda histórico de reproduções.
- **Qualidade:** ainda não validada com músicas reais do usuário; o peso artístico continua uma aproximação baseada em sinais públicos, sem modelo treinado de avaliação musical.
- **CI:** executar compilação, suítes de testes e revisão do workflow mais recente antes de qualquer merge. Quando este registro foi criado, os jobs estavam na fila (não aprovados). O APK só pode sair do SHA integrado na `nonstop`.
- **Teste no aparelho:** validar reprodução das músicas sugeridas, tempo de preenchimento no primeiro uso, variedade de gêneros e disponibilidade de dados de Spotify/Last.fm/YouTube Music antes de declarar a experiência concluída.

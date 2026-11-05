# Sonora — Roadmap de migração para uso diário (1.0)

**Atualizado:** 2026-11-04 · **Estado:** planejamento; desenvolvimento da descoberta personalizada pausado  
**Fonte de verdade:** este arquivo em `nonstop` após merge. Versões baixadas e anexadas ao ChatGPT são cópias estáticas, sem sincronização automática.  
**Repositório:** `FernandoDevCode/N-STOP` · **Dispositivo de aceite:** Redmi Note 14, Android 16.  
**Próxima ação:** F0.1 — decidir e fechar com segurança PR #61.

## Objetivo e limites

Tornar o Sonora utilizável diariamente, priorizando **Busca fluida → Spotify como catálogo principal/YouTube separado → Biblioteca → branding grafite/branco → release validado**.

**Congelado:** novos recursos e ajustes do motor semanal de descoberta da Home. O PR #60 já entrou na `nonstop`; a qualidade musical real não foi validada. O PR #61 não está integrado. Apenas resolver seu estado com segurança, sem reativar desenvolvimento de recomendações.

**Não alterar incidentalmente:** player, mini-player, Media3, autenticação, downloads, IDs/assinatura do app, dados locais, perfil de artistas ou Art Lab V8. Respeitar `AGENTS.md` e `docs/SONORA_CONSOLIDATION.md`.

## Regras de acompanhamento

- `[ ]` pendente; `[x]` concluído **apenas após evidência**; `[-]` em andamento, ainda não concluído.
- Registro de cada entrega exige **PR/commit integrado, CI, validação no Redmi quando necessária e pendências**. Nunca declarar concluído só pelo relato do executor ou build APK.
- Alterar checkboxes e histórico **no mesmo PR da implementação**. A documentação acompanha a versão entregue, sem se tornar uma lista de promessas.
- O usuário decide produto/UX e avalia no aparelho; ChatGPT coordena/revisa; Codex/Work podem executar dentro de escopo aprovado; CI prova somente o que de fato testou.
- Não avançar uma fase não aprovada só por estar no roadmap; não solicitar autorização para cada comando trivial dentro de uma tarefa autorizada.

| Fase | Resultado esperado | Estado |
| --- | --- | --- |
| F0 | Branch limpa e PR #61 resolvido | PENDENTE |
| F1 | Busca rápida e com UI Sonora | PENDENTE |
| F2 | Spotify priorizado; YouTube isolado | PENDENTE |
| F3 | Biblioteca funcional e coerente | PENDENTE |
| F4 | Identidade Sonora grafite/branco | PENDENTE |
| F5 | APK consolidado testado no Redmi | PENDENTE |

## F0 — Estabilizar ponto de partida

- [ ] **F0.1** Revisar [PR #61](https://github.com/FernandoDevCode/N-STOP/pull/61), que combina Home sem vitrine Qobuz e fallback de descoberta, embora esta última frente tenha sido pausada. Corrigir/limitar e integrar apenas o que está aprovado, **ou fechar sem merge** com motivo registrado. [CI 41945240577](https://github.com/FernandoDevCode/N-STOP/actions/runs/41945240577): **falhou**, seis testes da Home; não declarar verde.
- [ ] **F0.2** Registrar HEAD da `nonstop`, versão instalada, configuração de fontes e baseline visual/funcional da Busca e Biblioteca.
- [ ] **F0.3** Confirmar invariantes: appId, assinatura, banco, login, player e dados preservados.

**Aceite:** PR #61 resolvido, estado da `nonstop` verificável e testes aplicáveis aprovados. Não publicar APK só por documentação.

## F1 — Busca: performance antes de redesenho

- [ ] **F1.1** Reproduzir a lentidão no Redmi: entrada na Busca, foco no campo, digitação, primeira consulta, segunda consulta, resultados e rolagem.
- [ ] **F1.2** Instrumentar frames/jank, atraso de input, tempo de rede, composição/recomposição e carregamento de imagens; distinguir causa, não presumir “rede lenta”.
- [ ] **F1.3** Aplicar correção **mínima e mensurável** com base no diagnóstico; não reescrever o módulo sem necessidade.
- [ ] **F1.4** Testar entradas rápidas, rede lenta/offline, erros de API, busca `Icronic`/Polyphia, resultados extensos e playback/download.
- [ ] **F1.5** Fechar especificação de UX da Busca no padrão Sonora: campo, destaque, lista, estados, tipografia, densidade e acessibilidade.
- [ ] **F1.6** Implementar visual aprovado sem bloquear digitação, com regressão e capturas.

**Aceite:** Busca visivelmente fluida no Redmi, métricas comparativas, resultados corretos, reprodução/download preservados; compilação isolada não basta.

## F2 — Spotify como catálogo principal; YouTube em aba

**Decisão:** a identidade musical padrão é a versão de áudio do Spotify (artista, álbum, edição, lançamento), **não** um vídeo achado por similaridade. Spotify como referência de catálogo não garante disponibilização de seu próprio stream: reprodução real depende de fonte permitida e disponível.

- [ ] **F2.1** Mapear busca → identificação de obra/versão → resolução de fonte → player/download, verificando autorizações existentes.
- [ ] **F2.2** Fechar dois modos de Busca: **Música** (padrão, Spotify-first) e **YouTube** (separado, vídeos/lives/covers, sem contaminar resultados principais).
- [ ] **F2.3** Evitar misturar original, cover, live, lyric video, reupload e versões incompatíveis; informar quando playback usa outra fonte ou está indisponível.
- [ ] **F2.4** Implementar em escopo mínimo; preservar pipeline de áudio e playback.
- [ ] **F2.5** Testar single/álbum, versões duplicadas, artista homônimo, live e falhas na fonte principal.

**Aceite:** resultado musical principal não é poluído por vídeos, aba YouTube clara, correspondência correta e sem falsas garantias de reprodução.

## F3 — Biblioteca

**Observação:** screenshot mostra tela vazia e texto parcialmente em inglês; isso **não prova** erro de sincronização. É preciso distinguir músicas baixadas/localmente visíveis da biblioteca salva nas contas.

- [ ] **F3.1** Auditar as fontes e estados reais de curtidas, músicas, playlists, artistas, álbuns, downloads e conteúdo sincronizado.
- [ ] **F3.2** Especificar separação clara entre biblioteca sincronizada e itens offline; corrigir textos PT-BR.
- [ ] **F3.3** Aprovar hierarquia visual Sonora: cabeçalho, filtros, ordenação, empty states, capa/card, ações e mini-player.
- [ ] **F3.4** Implementar e testar coleção vazia, conta conectada, downloads, sincronização parcial, acessibilidade, fonte grande e rolagem longa.

**Aceite:** acesso claro ao conteúdo existente sem perda de dados, UX coerente e comportamento comprovado no Redmi.

## F4 — Identidade visual

**Decisão:** preservar desenho do símbolo Sonora; eliminar matiz lavanda/rosa do ícone, preferindo **grafite/preto/cinza + branco**. Não mudar a paleta de todo app só por isso.

- [ ] **F4.1** Aprovar paleta exata em preview do **símbolo atual**, sem redesenhar seu contorno.
- [ ] **F4.2** Corrigir launcher adaptativo, variantes/densidades e monocromático; validar no Android.
- [ ] **F4.3** Padronizar splash e rótulos visíveis **Sonora** em vez de **NØNSTOP**, sem renomear IDs internos por estética.
- [ ] **F4.4** Auditar contraste, tipografia, cabeçalhos e PT-BR de Busca e Biblioteca.

**Aceite:** ícone e splash em grafite/branco, nome correto, atualização sobre app instalado preserva dados/assinatura.

## F5 — Release de migração

- [ ] **F5.1** Integrar PRs aprovados à `nonstop`, confirmar SHA e preservar histórico.
- [ ] **F5.2** CI verde (build, testes e regressões pertinentes); registrar URLs.
- [ ] **F5.3** Publicar APK pelo workflow canônico `.github/workflows/sonora-preview.yml` sobre SHA integrado, conferindo versão, pacote e assinatura.
- [ ] **F5.4** Testar no Redmi: abrir → buscar e digitar → identificar versão correta → escolher aba YouTube → reproduzir → baixar → abrir Biblioteca → fechar/reabrir → splash/ícone.
- [ ] **F5.5** Registrar bugs, limites e decisão explícita: pronto ou não para migração.

**Aceite:** uso básico sólido sem regressão, perda de dados nem bloqueios críticos; pendências menores documentadas.

## Checkpoints (registro vivo)

Use sempre o formato:

```md
### AAAA-MM-DD — F?.? — Título
- Estado: PENDENTE / EM ANDAMENTO / BLOQUEADO / CONCLUÍDO
- Decisão/escopo: ...
- PR/commit integrado: ...
- CI (URL/resultado): ...
- Redmi (versão/resultado): ...
- Limitações: ...
- Próxima ação objetiva: ...
```

### 2026-11-04 — Baseline
- F0–F5 **pendentes**; este arquivo não prova implementação das novas fases.
- PR #60: integrado; versão 0.9.157 instalada; Home personalizada sem recomendações na captura.
- PR #61: aberto/draft e não integrado; execução 41945240577 falhou em seis testes de `feature:home`.
- Descoberta personalizada: **pausada pelo usuário**.
- Bloqueadores do uso diário: Busca perceptivelmente lenta; resultados misturando fontes; Biblioteca vazia/visual antigo e copy em inglês; NØNSTOP visível; ícone lavanda.
- **Próximo item:** F0.1, depois F1.1.

## Trabalho contínuo: Project / Work / Codex

1. Este `docs/SONORA_ROADMAP_MIGRACAO_1_0.md` **integrado na `nonstop`** é a referência oficial. Download e upload ao ChatGPT Project produzem uma **cópia**, não sincronização.
2. No início de cada tarefa, consultar `AGENTS.md` e este roadmap **ao vivo** na `nonstop` antes de escolher o próximo item; não confiar no histórico da conversa como confirmação de estado.
3. Implementador (Codex/Work ou execução direta) cria branch baseada em `nonstop`, faz mudanças circunscritas, testa, abre PR com update do item; revisor valida código/CI. Somente após merge o checkbox passa a valer no documento canônico.
4. O Work pode executar uma sequência maior de inspeção, edição, PR e CI dentro do escopo aprovado, interrompendo somente para bloqueio real, autorização necessária ou validação de produto. Ele não tem acesso mágico ao aparelho.
5. No ChatGPT Project, ao pedir “atualize o roadmap”, o assistente pode consultar a fonte GitHub e editar a versão do repositório se tiver permissão; também pode entregar arquivo atualizado. **Nunca presumir que mexer numa conversa atualiza o anexo do Project.**
6. Cada plano futuro pode ter seu arquivo específico em `docs/roadmaps/` com escopo, decisões, checklist, critérios, evidências e checkpoints; um **único canônico**, sem duplicar o progresso em vários arquivos.

**Prompt de retomada:** “Leia o `AGENTS.md` e `docs/SONORA_ROADMAP_MIGRACAO_1_0.md` atuais na `nonstop`, confira PRs e CI, identifique o primeiro item pendente e execute só o escopo aprovado. Atualize checklist e evidências no PR. Não reative a descoberta personalizada nem marque conclusão sem validação.”

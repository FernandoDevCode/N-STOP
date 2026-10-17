# Referências mestras — contrato de proveniência

**Somente as duas imagens abaixo determinam a aparência pretendida do Player v8.**
Não confundir com PNGs em `docs/visual-reviews/checkpoint-2/`: estes representam a interface implementada no Android, não as imagens aprovadas.

| Destino obrigatório | Fonte original (pasta local `02_CHECKPOINT_2/`) | Resolução original | SHA-256 |
|---|---|---|---|
| `MASTER_MEGAD...png` | `03_PLAYER_MEGAdeth_PALETA-AZUL.png` | 864 × 1536 | `3547184210edca28399a0945dc28192e65341ebd1545be070873593004e3d68d` |
| `MASTER_PINK_FLOYD.png` | `02_PLAYER_PINK_FLOYD_PALETA-SELETIVA.png` | 941 × 1672 | `6b7cbd4da0fa601379b2d8d790d70aac2b532d22bb35739370fc3958fec430f0` |

**Status inicial:** os PNGs foram identificados no ZIP original compartilhado pelo usuário, mas ainda precisam ser importados para a branch, porque a interface de edição do GitHub não transfere os binários automaticamente. Não alegar que os PNGs estão no GitHub até verificar os caminhos.

Importador: `docs/player-v8/IMPORT_REFERENCES_V8.ps1`. Procura primeiro `02_CHECKPOINT_2` no repositório; se não encontrar, procura o ZIP original nos locais habituais. Confere SHA-256 e falha se a imagem não for a aprovada. A opção `-Publish` faz commit e push **somente desses dois arquivos**.

A referência Pink Floyd enviada de novo em foto de celular contém barra de status/barras pretas: use o PNG original listado, mais adequado para medir geometria. A referência Megadeth JPEG reenviada é uma derivação comprimida do PNG original.

Não baixar outras capas como substituto das mestres, não sobrescrever silenciosamente e não gerar variações por IA.

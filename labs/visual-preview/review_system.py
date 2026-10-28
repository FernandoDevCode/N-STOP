"""Review actual captured dp/density cases; does not modify any artwork asset."""
from pathlib import Path
import hashlib
import html
import json
import sys
import shutil
from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'app-desktop/build/validation/sonora-system'
REPO = Path(__file__).resolve().parents[2]


def main():
    if '--record-baseline' in sys.argv:
        target = ROOT / 'baseline'
        if target.exists():
            raise SystemExit('Baseline já existe; preserve-a em outra pasta antes de registrar uma nova aprovação.')
        target.mkdir(parents=True)
        for name in ('home', 'navigation-home', 'navigation-explore', 'navigation-search', 'navigation-library'):
            shutil.copy2(ROOT.parent / 'remodel' / f'{name}.png', target / f'{name}.png')
        assets = REPO / 'labs/visual-preview/res'
        (ROOT / 'artwork-hashes.json').write_text(json.dumps({str(p.relative_to(REPO)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in assets.rglob('*') if p.is_file()}, indent=2), encoding='utf-8')
        print(target)
        return
    matrix = ROOT / 'matrix'
    cases = json.loads((matrix / 'matrix.json').read_text())
    for case in cases:
        with Image.open(matrix / case['file']) as image:
            assert image.size == (case['pixelWidth'], case['pixelHeight'])
            assert abs(image.width / case['density'] - case['widthDp']) <= 1
    frozen = json.loads((ROOT / 'artwork-hashes.json').read_text()) if (ROOT / 'artwork-hashes.json').exists() else None
    assets_unchanged = None if frozen is None else all(hashlib.sha256((REPO / p).read_bytes()).hexdigest() == digest for p, digest in frozen.items())
    baseline = {}
    approved = ROOT.parent / 'remodel'
    for name in ('home', 'navigation-home', 'navigation-explore', 'navigation-search', 'navigation-library'):
        if not (ROOT / 'baseline' / f'{name}.png').exists():
            baseline[name] = None
            continue
        with Image.open(ROOT / 'baseline' / f'{name}.png') as before, Image.open(approved / f'{name}.png') as after:
            baseline[name] = ImageChops.difference(before.convert('RGB'), after.convert('RGB')).getbbox() is None
    summary = {'captures': len(cases), 'assetsUnchanged': assets_unchanged, 'approvedPixelEquality': baseline,
               'widthsDp': sorted({c['widthDp'] for c in cases}), 'fonts': sorted({c['fontScale'] for c in cases}),
               'densities': sorted({c['density'] for c in cases}), 'themes': sorted({c['theme'] for c in cases}),
               'scenarios': sorted({c['scenario'] for c in cases}),
               'note': 'Desktop captures, not Android runtime or a perceptual fidelity score.'}
    (ROOT / 'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    cards = []
    for case in cases:
        label = f"{case['widthDp']}dp · fonte {case['fontScale']} · densidade {case['density']} · {case['theme']} · {case['scenario']} · {case['page']}"
        filename = html.escape(case['file'])
        cards.append(f'<figure><a href="matrix/{filename}"><img loading="lazy" src="matrix/{filename}" alt="{html.escape(label)}"></a><figcaption>{html.escape(label)}</figcaption></figure>')
    (ROOT / 'index.html').write_text('''<!doctype html><html lang="pt-BR"><meta charset="utf-8"><title>Sonora · Sistema visual</title>
<style>body{background:#101116;color:#f2f1f7;font:14px system-ui;margin:24px}p{color:#b3b1c8;line-height:1.6}.grid{display:flex;flex-wrap:wrap;gap:20px}figure{margin:0;width:240px}img{width:100%;display:block}figcaption{padding:8px 0;line-height:1.5}a{color:inherit}</style>
<h1>Sonora · Matriz visual executada</h1><p>Cada arquivo registra largura lógica, densidade e fonte separadamente. Clique para abrir os pixels originais.
Insets simulados: 24dp superior/inferior; casos com inset lateral são identificados no nome.
As capas são recursos de desenvolvimento existentes, preservados. Esta galeria não comprova paridade visual Android.</p>
<div class="grid">''' + ''.join(cards) + '</div></html>', encoding='utf-8')
    # Contact sheet only scales whole UI captures for review; original PNGs and all artwork remain intact.
    picks = [next(c for c in cases if c['widthDp'] == width and c['fontScale'] == 1.5 and c['scenario'] == 'Approved'
                  and c['theme'] == 'dark' and c['density'] == 1 and c['page'] == 'Home' and c['heightDp'] == 900)
             for width in (280, 320, 360, 390, 412, 480)]
    board = Image.new('RGB', (1260, 730), '#101116')
    draw = ImageDraw.Draw(board)
    for index, case in enumerate(picks):
        x = 10 + index * 208
        draw.text((x, 10), f"{case['widthDp']} dp / 150%", fill='#f2f1f7')
        with Image.open(matrix / case['file']) as image:
            thumb = image.convert('RGB')
            thumb.thumbnail((196, 690))
            board.paste(thumb, (x, 34))
    board.save(ROOT / 'overview.png')
    print(json.dumps(summary, indent=2))
    print(ROOT / 'index.html')


if __name__ == '__main__':
    main()

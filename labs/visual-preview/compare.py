"""Generate a self-contained visual review with aligned overlays and raw pixel measurements.

Run after --capture-visual. Pillow/numpy are used for inspection only; originals are preserved.
MAE is NOT a perceptual fidelity percentage, nor an approval threshold.
"""
from pathlib import Path
import base64
import json
import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
OUTPUT = HERE.parent / 'app-desktop/build/validation/remodel'


def data_url(path):
    return 'data:image/png;base64,' + base64.b64encode(path.read_bytes()).decode()


def main():
    measurements = []
    cards = []
    overview = Image.new('RGB', (1274, 774), '#101116')
    labels = {'home': 'Inicio', 'explore': 'Explorar', 'search': 'Buscar', 'library': 'Biblioteca'}
    for index, page in enumerate(('home', 'explore', 'search', 'library')):
        reference = HERE / f'res/drawable/reference_{page}.png'
        rendered = OUTPUT / f'{page}.png'
        ImageDraw.Draw(overview).text((12 + index * 316, 12), labels[page], fill='#eeeaf8')
        overview.paste(Image.open(rendered).convert('RGB'), (12 + index * 316, 38))
        a = np.asarray(Image.open(reference).convert('RGB')).astype(float)
        b = np.asarray(Image.open(rendered).convert('RGB')).astype(float)
        assert a.shape == b.shape
        # The phone frame, system status bar and gesture area are not app UI.
        valid = np.zeros(a.shape[:2], dtype=bool)
        valid[34:-8, 8:-8] = True
        error = np.abs(a - b)[valid]
        result = {'page': page, 'width': a.shape[1], 'height': a.shape[0],
                  'mask': 'x:8..width-8, y:34..height-8',
                  'rgb_mae_0_255': round(float(error.mean()), 2),
                  'rgb_rmse_0_255': round(float(np.sqrt(np.square(error).mean())), 2)}
        measurements.append(result)
        cards.append(f'''<article><h2>{page.title()}</h2>
<p>{a.shape[1]} × {a.shape[0]} px · erro RGB médio: {result['rgb_mae_0_255']}/255</p>
<div class="pair"><figure><img src="{data_url(reference)}"><figcaption>Referência</figcaption></figure>
<figure><img src="{data_url(rendered)}"><figcaption>Compose Desktop</figcaption></figure>
<figure><div class="overlay"><img src="{data_url(reference)}"><img class="generated" src="{data_url(rendered)}"></div>
<figcaption>Sobreposição · <input aria-label="Opacidade da sobreposição {page}" type="range" min="0" max="100" value="50" oninput="this.closest('figure').querySelector('.generated').style.opacity=this.value/100"></figcaption></figure></div></article>''')
    (OUTPUT / 'measurements.json').write_text(json.dumps(measurements, indent=2), encoding='utf-8')
    overview.save(OUTPUT / 'overview.png')
    (OUTPUT / 'comparison.html').write_text('''<!doctype html><html lang="pt-BR"><meta charset="utf-8">
<title>NØNSTOP · Revisão visual</title><style>
body{background:#101116;color:#eeeaf8;font:15px system-ui;max-width:1120px;margin:32px auto;padding:0 20px}
h1{font-size:26px}p{color:#b8b2c9;line-height:1.6}.pair{display:flex;gap:20px;flex-wrap:wrap}figure{margin:0;width:310px}
img{display:block;width:100%}.overlay{position:relative}.generated{position:absolute;inset:0;opacity:.5}
figcaption{padding:10px 0;color:#b8b2c9}article{margin:32px 0;border-top:1px solid #393544}input{width:130px;accent-color:#b7a7ee}
</style><h1>NØNSTOP · Referência e implementação</h1>
<p>Mesmas dimensões de captura e escala de fonte fixa. O slider alterna a sobreposição para inspecionar desalinhamentos.
Medições excluem moldura, barra de status e área de gestos; incluem diferenças de arte e rasterização.
Erro RGB não é uma porcentagem de fidelidade. Estas capturas são Desktop; não comprovam renderização Android.</p>
<p>Diferenças conhecidas: fonte Inter aproximada, marca tipográfica sem arquivo original, ícones Material,
superfícies com gradientes sem blur de fundo real, arte do banner reconstruída de regiões disponíveis, sem animações de referência.</p>
''' + ''.join(cards) + '</html>', encoding='utf-8')
    print(OUTPUT / 'comparison.html')
    print(json.dumps(measurements, indent=2))


if __name__ == '__main__':
    main()

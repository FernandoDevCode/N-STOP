#!/usr/bin/env python3
"""Last.fm top-five Most popular gallery preview. Read-only network, no bypass.

This is an experiment: no API image license is implied; preserve attributed sources.
Never confuse the artist-page link with a ranked gallery image.
"""
import io
import json
import re
import html
from pathlib import Path
from urllib.parse import quote

import requests
from bs4 import BeautifulSoup
from PIL import Image, ImageStat

ARTISTS = ("Slipknot", "Radiohead", "System of a Down")
OUT = Path("docs/research")
HEADERS = {"User-Agent":"Mozilla/5.0 (compatible; SonoraLastfmTop5/0.1)"}
HASH = re.compile(r"/\+images/([0-9a-fA-F]{32})(?:[/?#]|$)")
TIMEOUT = (7,18)

def image_for_hash(hash_id):
    # ar0 is the intrinsic uncropped photo variant; the id pins photo identity.
    for variant in ("ar0", "770x0"):
        url = "https://lastfm-img.freetls.fastly.net/i/u/"+variant+"/"+hash_id+".jpg"
        try:
            r=requests.get(url, headers=HEADERS,timeout=TIMEOUT)
            r.raise_for_status()
            if not r.headers.get("Content-Type","").startswith("image/") or len(r.content)>6_000_000:
                continue
            image=Image.open(io.BytesIO(r.content)).convert("RGB")
            if image.width < 200 or image.height < 200:
                continue
            # Saturation is observed, not used for ranking in this stage.
            thumb=image.copy();thumb.thumbnail((90,90))
            stat=ImageStat.Stat(thumb)
            pixels=list(thumb.getdata())
            color_fraction=round(sum((max(p)-min(p))>25 for p in pixels)/len(pixels),3)
            return {
                "url":url, "variant":variant, "width":image.width,"height":image.height,
                "bytes":len(r.content),"color_fraction":color_fraction
            }
        except (requests.RequestException,ValueError, OSError):
            pass
    return None

def get_top5(artist):
    gallery="https://www.last.fm/music/"+quote(artist,safe="")+"/+images"
    result={"artist":artist,"gallery_url":gallery,"status":"unknown","top5":[]}
    try:
        response=requests.get(gallery,headers=HEADERS,timeout=TIMEOUT)
        soup=BeautifulSoup(response.text,"html.parser")
        title=" ".join(soup.title.get_text(" ",strip=True).split()) if soup.title else ""
        text=" ".join(soup.get_text(" ",strip=True).split())
        result.update({"http":response.status_code,"page_title":title,
                       "html_bytes":len(response.content)})
        if response.status_code in (403,429) or "client challenge" in title.lower() or "captcha" in title.lower():
            result["status"]="access_challenge";return result
        if response.status_code!=200 or not ("photos" in title.lower() and "last.fm" in title.lower()):
            result["status"]="invalid_gallery_page";return result
        if "Sorted by: Most popular" not in text:
            result["status"]="unverified_sort_order";return result
        # This is the actual ranked grid. Nav includes an unrelated /+images/<hash>.
        anchors=soup.select("ul.image-list > li.image-list-item-wrapper > a.image-list-item[href]")
        hashes=[]
        for a in anchors:
            match=HASH.search(a["href"])
            if not match:continue
            h=match.group(1).lower()
            if h not in hashes:hashes.append(h)
            if len(hashes)>=5:break
        result["ranked_grid_count"]=len(anchors)
        if len(hashes)<5:
            result["status"]="insufficient_gallery_photos";return result
        result["status"]="top5_verified"
        for index,h in enumerate(hashes,1):
            image=image_for_hash(h)
            result["top5"].append({
                "rank":index,"hash":h,
                "lastfm_photo_url":gallery+"/"+h,
                "image":image,
            })
        return result
    except requests.RequestException as exc:
        result.update({"status":"network_error","reason":str(exc)[:140]})
        return result

def make_html(results):
    css="""
    :root {color-scheme:dark}
    body{font-family:system-ui,-apple-system,Segoe UI,sans-serif;margin:0;background:#101115;color:#f2f2f3}
    main{max-width:1120px;margin:0 auto;padding:26px 19px 80px}
    h1{font-size:clamp(24px,4vw,38px);margin:0 0 8px}h2{font-size:23px;margin:0 0 16px}
    p{color:#abb0b7;line-height:1.5}.intro{margin-bottom:42px}
    section{border-top:1px solid #33353b;margin-top:34px;padding-top:26px}
    .grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:13px}
    article{border:1px solid #353940;border-radius:13px;overflow:hidden;background:#1a1c22}
    .photo{aspect-ratio:4/5;background:#09090b;display:flex;align-items:center;justify-content:center}
    .photo img{height:100%;width:100%;object-fit:contain}
    .meta{padding:12px;font-size:12px;line-height:1.5}
    .rank{font-size:17px;font-weight:700;display:block}.dim{color:#a3abb6}
    a{color:#a7bffb;text-decoration:none}a:hover{text-decoration:underline}
    .notice{padding:14px;border:1px solid #52493b;border-radius:9px;background:#272118;color:#e9d9bf}
    @media(max-width:850px){.grid{grid-template-columns:repeat(3,minmax(0,1fr))}}
    @media(max-width:520px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
    """
    page=["<!doctype html><html lang='pt-BR'><head><meta charset='utf-8'>",
          "<meta name='viewport' content='width=device-width,initial-scale=1'>",
          "<title>Sonora — Top 5 Last.fm</title><style>"+css+"</style></head><body><main>",
          "<h1>Sonora / Galerias Top 5 do Last.fm</h1>",
          "<p class='intro'>Ordem real das cinco primeiras imagens no grid “Most popular”. "
          "Sem reordenação estética, IA ou substituição por fotos da homepage. "
          "Visualização experimental: o acesso do Last.fm é instável.</p>"]
    for res in results:
        esc=html.escape
        name=esc(res["artist"])
        page.append("<section><h2>"+name+"</h2>")
        if res["status"]!="top5_verified":
            page.append("<p class='notice'>Galeria indisponível nesta execução: "+
                        esc(res["status"])+". A ordem não foi inferida.</p>")
        else:
            page.append("<div class='grid'>")
            for photo in res["top5"]:
                img=photo["image"]
                page.append("<article><div class='photo'>")
                if img:
                    page.append("<img loading='lazy' src='"+esc(img["url"],quote=True)+
                                "' alt='"+name+" foto "+str(photo["rank"])+"'>")
                else:
                    page.append("<p>Imagem indisponível</p>")
                page.append("</div><div class='meta'><span class='rank'>#"+
                            str(photo["rank"])+"</span>")
                if img:
                    page.append("<span class='dim'>"+str(img["width"])+" × "+str(img["height"])+" px</span><br>")
                page.append("<a target='_blank' rel='noopener noreferrer' href='"+
                            esc(photo["lastfm_photo_url"],quote=True)+"'>Ver fotografia no Last.fm</a></div></article>")
            page.append("</div>")
        page.append("</section>")
    page.append("<p>Protótipo somente de leitura. Os direitos das fotografias pertencem aos respectivos titulares.</p></main></body></html>")
    return "\n".join(page)

def main():
    results=[]
    for artist in ARTISTS:
        res=get_top5(artist)
        results.append(res)
        print("ARTIST "+artist+" status="+res["status"]+
              " grid="+str(res.get("ranked_grid_count",0))+
              " count="+str(len(res["top5"])),flush=True)
        for p in res["top5"]:
            img=p["image"]
            print("  #"+str(p["rank"])+" "+p["hash"]+
                  " "+(str(img["width"])+"x"+str(img["height"]) if img else "image-unavailable")+
                  " "+(img["url"] if img else ""),flush=True)
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/"lastfm-top5-results.json").write_text(json.dumps(results,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    (OUT/"lastfm-top5-preview.html").write_text(make_html(results),encoding="utf-8")
    print("RESULTS: docs/research/lastfm-top5-results.json")
    print("PREVIEW: docs/research/lastfm-top5-preview.html")

if __name__=="__main__":
    main()

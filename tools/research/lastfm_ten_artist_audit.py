#!/usr/bin/env python3
"""Ten artist audit of public Last.fm gallery order; no authentication or bypass."""
import io,json,re,time
from datetime import datetime,timezone
from pathlib import Path
from urllib.parse import quote
import requests
from bs4 import BeautifulSoup
from PIL import Image

ARTISTS=["Metallica","Pink Floyd","Megadeth","Deftones","Nirvana",
         "Linkin Park","Rammstein","Muse","Slaughter to Prevail","Limão Rosa"]
HEADERS={"User-Agent":"Mozilla/5.0 (Sonora-Feasibility-Audit/0.2)"}
PAT=re.compile(r"/\+images/([0-9a-f]{32})(?:[/?#]|$)",re.I)
OUT=Path("docs/research")
def gallery(artist,round_no):
    url="https://www.last.fm/music/"+quote(artist,safe="")+"/+images"
    item={"artist":artist,"round":round_no,"url":url,"status":"error","top5":[]}
    try: r=requests.get(url,headers=HEADERS,timeout=(7,18))
    except requests.RequestException as e:
        item["error"]=type(e).__name__
        return item
    soup=BeautifulSoup(r.text,"html.parser")
    title=" ".join(soup.title.get_text(" ",strip=True).split()) if soup.title else ""
    item.update(http=r.status_code,html_bytes=len(r.content),title=title)
    if r.status_code in (403,429) or "client challenge" in title.lower() or "captcha" in title.lower():
        item["status"]="client_challenge"
    elif r.status_code!=200 or "photos" not in title.lower() or "last.fm" not in title.lower():
        item["status"]="unavailable"
    elif "sorted by: most popular" not in soup.get_text(" ",strip=True).lower():
        item["status"]="sort_unverified"
    else:
        seen=[]
        anchors=soup.select("ul.image-list > li.image-list-item-wrapper > a.image-list-item[href]")
        item["grid_count"]=len(anchors)
        for a in anchors:
            m=PAT.search(a["href"])
            if m and m.group(1).lower() not in seen:seen.append(m.group(1).lower())
            if len(seen)==5:break
        if len(seen)!=5:item["status"]="too_few_photos"
        else:
            item["status"]="top5_verified"
            for index,h in enumerate(seen,1):
                item["top5"].append({
                    "rank":index,"hash":h,
                    "image_url":"https://lastfm-img.freetls.fastly.net/i/u/ar0/"+h+".jpg",
                    "detail_url":url+"/"+h
                })
    return item

def validate(photos):
    for photo in photos:
        try:
            resp=requests.get(photo["image_url"],headers=HEADERS,timeout=(7,16))
            resp.raise_for_status()
            if len(resp.content)>6000000 or not resp.headers.get("Content-Type","").startswith("image/"):
                photo["image_status"]="invalid"
                continue
            image=Image.open(io.BytesIO(resp.content))
            image.verify()
            photo.update(image_status="valid",width=image.width,height=image.height,bytes=len(resp.content))
        except (requests.RequestException,OSError,ValueError) as e:
            photo.update(image_status="failed",error=type(e).__name__)

def main():
    entries={a:[] for a in ARTISTS}
    for round_no in (1,2):
        if round_no==2:time.sleep(12)
        print("ROUND "+str(round_no),flush=True)
        for pos,artist in enumerate(ARTISTS):
            if pos:time.sleep(1.0)
            outcome=gallery(artist,round_no)
            if outcome["status"]=="top5_verified" and not any(
                prev["status"]=="top5_verified" for prev in entries[artist]
            ):
                validate(outcome["top5"])
            entries[artist].append(outcome)
            print(artist+" | "+outcome["status"]+" | HTTP "+
                  str(outcome.get("http","ERR"))+" | "+str(outcome.get("html_bytes",0))+
                  " bytes | 5 photos="+str(len(outcome["top5"])==5),flush=True)
    summary={}
    for a, attempts in entries.items():
        ok=[x for x in attempts if x["status"]=="top5_verified"]
        summary[a]={"rounds":[x["status"] for x in attempts],"verified_rounds":len(ok),
                    "top5":ok[0]["top5"] if ok else [],
                    "valid_originals":sum(p.get("image_status")=="valid" for p in ok[0]["top5"]) if ok else 0}
    artists_ok=sum(s["verified_rounds"]>0 for s in summary.values())
    rounds_ok=sum(s["verified_rounds"] for s in summary.values())
    originals=sum(s["valid_originals"] for s in summary.values())
    data={"at":datetime.now(timezone.utc).isoformat(),"summary":summary,"raw":entries,
          "artists_ok":artists_ok,"rounds_ok":rounds_ok,"originals_ok":originals}
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/"lastfm-ten-artist-audit.json").write_text(json.dumps(data,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    md=["# Sonora — Last.fm audit of 10 new artists","",
        "Two independent GET requests per artist, 12 seconds between rounds; no cookies, login, proxies or CAPTCHA handling.",
        "Success requires the Most popular sorting label and first five unique hashes from the actual ranked gallery grid.","",
        "| Artist | Round 1 | Round 2 | Valid images |",
        "|---|---|---|---|"]
    for name,s in summary.items():
        md.append("| "+name+" | "+s["rounds"][0]+" | "+s["rounds"][1]+" | "+
                  (str(s["valid_originals"])+"/5" if s["top5"] else "—")+" |")
    md+=["", "**At least one successful gallery:** "+str(artists_ok)+"/10",
         "**Successful requests:** "+str(rounds_ok)+"/20",
         "**Verified original image files:** "+str(originals)+"/"+str(artists_ok*5),
         "", "This is one GitHub runner at one time, not an estimate of long-term success.",""]
    for name,s in summary.items():
        if not s["top5"]:continue
        md+=["## "+name,"","| Rank | Image | Dimensions | Last.fm |",
             "|---|---|---|---|"]
        for p in s["top5"]:
            dim=(str(p["width"])+" x "+str(p["height"])) if p.get("width") else p.get("image_status","?")
            md.append("| "+str(p["rank"])+" | ![photo]("+p["image_url"]+") | "+
                      dim+" | [Detail]("+p["detail_url"]+") |")
    (OUT/"lastfm-ten-artist-audit.md").write_text("\n".join(md)+"\n",encoding="utf-8")
    print("RESULT "+str(artists_ok)+"/10 artists "+str(rounds_ok)+"/20 requests "+
          str(originals)+" valid ar0 images",flush=True)

if __name__=="__main__":main()

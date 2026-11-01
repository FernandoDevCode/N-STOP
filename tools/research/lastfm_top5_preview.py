#!/usr/bin/env python3
"""Diagnose Last.fm gallery DOM without attempting to bypass access checks."""
import re
import requests
from bs4 import BeautifulSoup
from urllib.parse import quote
from collections import Counter

for artist in ("Slipknot", "Radiohead", "System of a Down"):
    url="https://www.last.fm/music/"+quote(artist,safe="")+"/+images"
    try:
        response=requests.get(url,timeout=17,headers={"User-Agent":"Mozilla/5.0"})
        soup=BeautifulSoup(response.text,"html.parser")
        title=soup.title.get_text(" ",strip=True) if soup.title else ""
        is_gallery=("photos" in title.lower() and "last.fm" in title.lower() and "client challenge" not in title.lower())
        print("\nARTIST="+artist+" HTTP="+str(response.status_code)+
              " bytes="+str(len(response.content))+" title="+repr(title)+" gallery="+str(is_gallery),flush=True)
        if not is_gallery: continue
        text=soup.get_text(" ",strip=True)
        print("SORT="+repr(text[text.lower().find("sorted by"):text.lower().find("sorted by")+90]),flush=True)
        links=[]
        for a in soup.find_all("a",href=True):
            m=re.search(r"/\+images/([0-9a-f]{32})",a["href"],re.I)
            if not m:continue
            img=a.find("img")
            stack=[]
            for p in list(a.parents)[:6]:
                if getattr(p,"name",None):
                    stack.append(p.name+"."+".".join(p.get("class",[])[:3]))
            links.append((m.group(1),img.get("src","")[:95] if img else "",stack,a.get("class",[])))
        print("ANCHORS="+str(len(links))+" UNIQUE="+str(len(set(x[0] for x in links))),flush=True)
        for i,(h,img,stack,cl) in enumerate(links[:18],1):
            print(f"{i:02} {h} img={bool(img)} parents={' / '.join(stack[:5])} aClass={cl}",flush=True)
        print("PARENT COUNTER:"+repr(Counter(x[2][0] if x[2] else "" for x in links).most_common(9)),flush=True)
    except requests.RequestException as e:
        print("ERROR "+str(e)[:300],flush=True)

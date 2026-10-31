"""Arama isabet/gecikme olcumu (salt-okur GET, sorgular arasi 1 sn).

Calistirma (stdin'den, konteyner icinde; dis adres cookie 302 verir):
  ssh zima "export DOCKER_CONFIG=/tmp/dc; docker exec -i -e GOLDEN_B64=$(base64 -w0 scripts/search_golden.json) netmovies-stream python - " < scripts/search_eval.py
Yerelde: python scripts/search_eval.py --golden scripts/search_golden.json [--base URL]
Isabet = ilk 3 kartta beklenen basliktan biri (normalize, icerir) ya da tmdb_id.
"henuz_yok" kovasi isabet oranina girmez; bulunursa "BULUNDU" diye isaretlenir.
"""
import base64, json, os, sys, time, urllib.parse, urllib.request

HARF = str.maketrans("İIıŞşĞğÜüÖöÇç", "iiissgguuoocc")


def norm(s):
    s = str(s or "").translate(HARF).lower()
    return " ".join("".join(c if c.isalnum() else " " for c in s).split())


def isabet(sonuc, oge):
    for kart in sonuc[:3]:
        if oge.get("tmdb_id") and kart.get("tmdb_id") == oge["tmdb_id"]:
            return True
        baslik = norm(kart.get("title"))
        if any(norm(b) in baslik for b in oge["beklenen"]):
            return True
    return False


def sorgula(base, q):
    url = f"{base}/api/v1/search_all?query={urllib.parse.quote(q)}"
    t = time.time()
    try:
        with urllib.request.urlopen(url, timeout=40) as r:
            sonuc = json.load(r).get("result") or []
    except Exception as e:  # olcum betigi: hatayi sayiya dok, durma
        print(f"  HATA {q!r}: {e}", file=sys.stderr)
        sonuc = []
    return sonuc, time.time() - t


def yuzdelik(v, p):
    v = sorted(v)
    return v[min(len(v) - 1, int(round(p * (len(v) - 1))))] if v else 0.0


def main():
    a = sys.argv[1:]
    arg = lambda ad, varsayilan=None: a[a.index(ad) + 1] if ad in a else varsayilan
    base = arg("--base", "http://127.0.0.1:3310")
    yol = arg("--golden")
    ham = open(yol, encoding="utf-8").read() if yol else base64.b64decode(os.environ["GOLDEN_B64"]).decode("utf-8")
    g = json.loads(ham)
    sureler, vurus, kacan, toplam = [], 0, [], 0
    for kova in ("bulunabilir", "henuz_yok"):
        for oge in g[kova]:
            sonuc, sn = sorgula(base, oge["q"])
            ok = isabet(sonuc, oge)
            ilk = sonuc[0].get("title") if sonuc else None
            if kova == "bulunabilir":
                toplam += 1
                vurus += ok
                sureler.append(sn)
                if not ok:
                    kacan.append(oge["q"])
                etiket = "OK" if ok else "KACTI"
            else:
                etiket = "BULUNDU" if ok else "yok(beklenen)"
            print(f"{kova[:4]} {etiket:13} {sn:5.1f}s n={len(sonuc):2} ilk={ilk!r} q={oge['q']!r}")
            time.sleep(1)
    print(f"\nIsabet@3: {vurus}/{toplam} = {100 * vurus / max(toplam, 1):.0f}%")
    print(f"Sure p50={yuzdelik(sureler, .5):.1f}s p95={yuzdelik(sureler, .95):.1f}s max={max(sureler or [0]):.1f}s")
    print("Kacan:", kacan or "yok")


if __name__ == "__main__":
    main()

# NetMovies - acilis kaynak raporu.
# Her PC acilisinda kaynaklarin gercekten cevap verip vermedigini yazar:
# domain tasindiginda katalog sessizce kuruyor, bunu ertesi gun degil ayni
# gun gormek icin. Cikti: autostart.log + (sorun varsa) KAYNAK-UYARI.txt
import json, sys, urllib.request
from datetime import datetime

try:
    with urllib.request.urlopen("http://localhost:3310/api/v1/plugin_health?force=1", timeout=180) as r:
        data = json.load(r)["result"]
except Exception as hata:
    print(f"kaynak raporu alinamadi: {type(hata).__name__}: {hata}")
    sys.exit(2)

olu = [p for p in data["plugins"] if not p["ok"]]
damga = datetime.now().strftime("%d.%m.%Y %H:%M")
print(f"{damga} kaynak: {data['healthy']}/{data['total']} saglikli")
for p in olu:
    print(f"  SORUNLU {p['plugin']} -> {p['main_url']} ({p['status']})")
sys.exit(1 if olu else 0)

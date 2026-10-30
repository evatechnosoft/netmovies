"""Gece kanaryası: chain_scan geçmişine bakıp sessizce ölen sağlayıcıyı haber verir.

    python3 scripts/chain_scan.py --n 2 --jsonl data/canary.jsonl
    python3 scripts/source_canary.py data/canary.jsonl --days 2

Uyarı Home Assistant'a gider (HA_URL + HA_TOKEN env, HA_NOTIFY varsayılan
mobile_app_fold). Env yoksa yalnız yazdırır. Aynı ölüm bir kez bildirilir:
seri tam `days` olduğu gün.
"""
import argparse
import json
import os
import urllib.request
from datetime import datetime, timedelta, timezone

STALE_AFTER = timedelta(hours=30)


def load(path):
    try:
        with open(path, encoding="utf-8") as history:
            return [json.loads(line) for line in history if line.strip()]
    except FileNotFoundError:
        return []


def empty(run):
    """Hiçbir sağlayıcı oynamadıysa sorun ağ/yığındır, kaynak değil."""
    return not any(stat.get("ok") for stat in run["plugins"].values())


def streaks(runs):
    """Sağlayıcı -> sondan kaç koşudur oynatılabilir kaynak vermiyor (katalogdan düşmek dahil)."""
    runs = [run for run in runs if not empty(run)]
    seen = {plugin for run in runs for plugin in run["plugins"]}
    result = {}
    for plugin in seen:
        count = 0
        for run in reversed(runs):
            if run["plugins"].get(plugin, {}).get("ok", 0) > 0:
                break
            count += 1
        result[plugin] = count
    return result


def alerts(runs, days, now):
    if not runs:
        return ["Kanarya geçmişi boş: tarama hiç yazmadı."]
    messages = []
    last_ts = datetime.fromisoformat(runs[-1]["ts"])
    if now - last_ts > STALE_AFTER:
        messages.append(f"Kanarya bayat: son tarama {last_ts:%d.%m %H:%M} UTC.")
    elif empty(runs[-1]):
        messages.append("Son tarama hiç oynatılabilir kaynak bulamadı: yığın/WARP/ağ kontrol.")
    dead = sorted(plugin for plugin, count in streaks(runs).items() if count == days)
    if dead:
        messages.append(f"{days} taramadır kaynak vermiyor: {', '.join(dead)}.")
    return messages


def notify(message):
    url, token = os.getenv("HA_URL"), os.getenv("HA_TOKEN")
    if not (url and token):
        return
    service = os.getenv("HA_NOTIFY", "mobile_app_fold")
    body = json.dumps({"title": "NetMovies kanarya", "message": message}).encode()
    request = urllib.request.Request(
        f"{url.rstrip('/')}/api/services/notify/{service}", data=body, method="POST",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
    urllib.request.urlopen(request, timeout=30).read()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("history")
    parser.add_argument("--days", type=int, default=2, help="kaç tarama üst üste 0 olunca uyarı")
    args = parser.parse_args()
    found = alerts(load(args.history), args.days, datetime.now(timezone.utc))
    for line in found:
        print(line)
    if found:
        notify("\n".join(found))
    else:
        print("kanarya: sorun yok")

# Gece kanaryası (ZimaOS)

Her gece 04:30 `chain_scan.py --n 2` koşar, sağlayıcı özetini `data/canary.jsonl`'a ekler;
`source_canary.py` bir sağlayıcı 2 tarama üst üste oynatılabilir kaynak vermezse,
tarama boş dönerse ya da 30 saattir yazılmadıysa Home Assistant'a (`notify.mobile_app_fold`) bildirir.

Kurulum (Zima, dal birleştikten ve `git pull`'dan sonra):

```bash
cd /DATA/AppData/netmovies
printf 'HA_URL=http://127.0.0.1:8123\nHA_TOKEN=<uzun ömürlü jeton>\n' > canary.env && chmod 600 canary.env
sudo cp scripts/systemd/source-canary.* /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now source-canary.timer
sudo systemctl start source-canary.service && tail -1 data/canary.jsonl   # ilk koşu
```

`canary.env` yoksa uyarı yalnız günlüğe yazılır (`journalctl -u source-canary`).

# Gece yedeği (ZimaOS)

03:30 `scripts/backup.sh take && verify`: `/data` → `/DATA/Backup/netmovies/netmovies-<tarih>.tar.gz`,
son 14 kopya; `.env`, cache ve apk tar'a girmez (`.env` Bitwarden'da). `verify` son tar'ı açıp
SQLite `integrity_check` koşar.

```bash
sudo cp scripts/systemd/netmovies-backup.* /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now netmovies-backup.timer
sudo systemctl start netmovies-backup.service && journalctl -u netmovies-backup -n 5
# geri alma: sudo systemctl disable --now netmovies-backup.timer
```

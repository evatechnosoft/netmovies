## DEVİR — 2026-10-03 akşam (EN GÜNCEL — tek geçerli blok)
Ayrıntı kök `HANDOFF.md`'de (commit `715f77f`). Kısa baton:

**Hedef:** Reklamsız TV uygulaması (client-tv, Mi Box). Dean'in kusurlarını düzelt, onayla üç yere yayınla.

**Durum (`fix/general-stability` @ `715f77f`, push'lu):**
- 0.9.46 kodda hazır, YAYINLANMADI (auto-mode GitHub release/apps.json'u reddetti, Dean "yayınla" demedi). İçerik: açılış paneli, Seriler sekmesi, Gözat'ta M3U gizli, ZimaOS WOL, widget güç/ses, ses 9/kaldığı yer + BT hoparlör, yapışkan yerel (`ab04aed`), üst barda güncellemede "NM" (`b9fa8e2`). 80 test yeşil, cihazda hiçbiri denenmedi.
- Canlıda (Zima): film_serileri ucu, atv güç köprüsü (:3311, kutu .105), proxy jeton reddi günlüğü (sebep+host+istemci).
- Ağ: 17:01'de Mi Box 5 GHz'de trafiksiz kaldı, telefon da yavaştı; 18:0x TP-Link Dean onayıyla yeniden başlatıldı, Mi Box .105'e döndü. Sonra TV yerel↔tünel arasında gidip geliyordu, Lioness tünelden 47-49. sn'de takıldı → yapışkan yerel düzeltmesi (0.9.46'da).

**Kararlar:** Mi Box ağdan açılamaz (köprü yalnız kapatır). Kalıcı ağ çözümü Ethernet. Benim değişikliklerim kopmanın sebebi değil (zaman çizelgesi).

**Tekrarlama:** Yayını alt ajana devretme, aynı izne takılır. Zima compose için `export DOCKER_CONFIG=/tmp/dc`. TV izlerken rebuild yok (`client_log`/`remote/status` bak).

**Tek sonraki iş:** Dean'in cevapları: (1) 0.9.46 "yayınla"; (2) Rec kararı: joystick SOL (ℹ yerine) ⏺ kaydet; (3) takip/favori yeni bölümlerini Zima'ya önden indirme — yeni oturumda tasarla.

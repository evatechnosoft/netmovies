# Açılış/jenerik işaret çıkarımı — altyazı metni girdi, üç sayı çıktı.

import unittest

from Public.API.v1.Libs.markers import cue_ayristir, acilis_bul, jenerik_bul, isaretleri_cikar


def _vtt(cueler):
    """(baş_sn, bit_sn, gövde) listesinden WEBVTT metni üretir."""
    def ts(s):
        return f"{int(s // 3600):02d}:{int(s % 3600 // 60):02d}:{int(s % 60):02d}.{int(s % 1 * 1000):03d}"
    bloklar = [f"{ts(b)} --> {ts(e)}\n{g}" for b, e, g in cueler]
    return "WEBVTT\n\n" + "\n\n".join(bloklar) + "\n"


class CueAyristirma(unittest.TestCase):
    def test_vtt_ve_srt_ayni_okunur(self):
        vtt = "WEBVTT\n\n00:00:10.500 --> 00:00:12.000\nmerhaba\n"
        srt = "1\n00:00:10,500 --> 00:00:12,000\nmerhaba\n"
        self.assertEqual(cue_ayristir(vtt), cue_ayristir(srt))
        self.assertEqual(cue_ayristir(vtt)[0][:2], (10.5, 12.0))

    def test_ters_zamanli_cue_atlanir(self):
        self.assertEqual(cue_ayristir("00:00:12.000 --> 00:00:10.000\nbozuk\n"), [])


class AcilisTespiti(unittest.TestCase):
    def test_muzik_blogu_acilis_sayilir(self):
        # 40-120 arası şarkı, sonrası diyalog. Bölüm 45 dk.
        cueler = [(5, 8, "Selam.")]
        cueler += [(t, t + 4, "♪ la la la ♪") for t in range(40, 120, 5)]
        cueler += [(t, t + 3, "Diyalog.") for t in range(200, 2600, 30)]
        acilis = acilis_bul(cue_ayristir(_vtt(cueler)), 2700)
        self.assertIsNotNone(acilis)
        self.assertAlmostEqual(acilis[0], 40, delta=1)
        self.assertAlmostEqual(acilis[1], 119, delta=2)

    def test_kisa_sahne_muzigi_acilis_sayilmaz(self):
        cueler = [(60, 66, "♪ kısa ♪"), (200, 203, "Diyalog.")]
        self.assertIsNone(acilis_bul(cue_ayristir(_vtt(cueler)), 2700))

    def test_gec_gelen_muzik_acilis_degil(self):
        # Bölümün ortasında uzun müzik: arama penceresi (%30) dışında.
        cueler = [(t, t + 4, "♪ konser ♪") for t in range(1500, 1600, 5)]
        self.assertIsNone(acilis_bul(cue_ayristir(_vtt(cueler)), 2700))


class JenerikTespiti(unittest.TestCase):
    def test_son_replikten_sonraki_sessizlik_jeneriktir(self):
        cueler = [(t, t + 3, "Diyalog.") for t in range(60, 2400, 30)]
        self.assertAlmostEqual(jenerik_bul(cue_ayristir(_vtt(cueler)), 2700), 2373, delta=2)

    def test_sona_kadar_konusuluyorsa_jenerik_yok(self):
        cueler = [(t, t + 3, "Diyalog.") for t in range(60, 2690, 30)]
        self.assertIsNone(jenerik_bul(cue_ayristir(_vtt(cueler)), 2700))

    def test_jenerik_sonrasi_promo_cuesu_yanitmaz(self):
        # Gerçek desen (DiziYou/One Piece): diyalog 59:15'te biter, jenerik boyunca
        # sessizlik, 1:02:00'de "TÜM BÖLÜMLERİ ŞİMDİ İZLEYİN" tanıtımı. Jenerik
        # tanıtımın değil, diyaloğun bittiği yerde başlar.
        cueler  = [(t, t + 3, "Diyalog.") for t in range(60, 3557, 30)]
        cueler += [(3720.4, 3725.0, "TÜM BÖLÜMLERİ ŞİMDİ İZLEYİN")]
        jenerik = jenerik_bul(cue_ayristir(_vtt(cueler)), 3782)
        self.assertIsNotNone(jenerik)
        self.assertAlmostEqual(jenerik, 3549, delta=15)

    def test_sessiz_sahneler_jenerigi_bastirmaz(self):
        # Ölçülmüş gerçek desen (DiziYou/One Piece, 63 dk): son çeyrekte 75 sn ve
        # 89 sn'lik sessiz SAHNELER var; jenerik 160 sn'lik boşluk. En erken aday
        # seçilirse 48:25'teki sahne jenerik sanılıyordu.
        cueler = []
        t = 60.0
        for kesme in (2905.6, 3014.8, 3555.3):          # sessizlikten önceki son cue
            while t < kesme - 3:
                cueler.append((t, t + 3, "Diyalog."))
                t += 30
            cueler.append((kesme - 3, kesme, "Diyalog."))
            t = kesme + {2905.6: 74.9, 3014.8: 89.4, 3555.3: 160.2}[kesme]
        cueler.append((3720.4, 3725.0, "TÜM BÖLÜMLERİ ŞİMDİ İZLEYİN"))
        self.assertAlmostEqual(jenerik_bul(cue_ayristir(_vtt(cueler)), 3782), 3555.3, delta=1)

    def test_ortadaki_sessiz_sahne_jenerik_degil(self):
        # Bölümün ortasında 3 dakikalık diyalogsuz montaj — jenerik değil.
        cueler  = [(t, t + 3, "Diyalog.") for t in range(60, 1200, 30)]
        cueler += [(t, t + 3, "Diyalog.") for t in range(1400, 2690, 30)]
        self.assertIsNone(jenerik_bul(cue_ayristir(_vtt(cueler)), 2700))

    def test_asiri_bosluk_eksik_altyazidir(self):
        # Altyazı 10. dakikada kesilmiş: jenerik değil, eksik dosya.
        cueler = [(t, t + 3, "Diyalog.") for t in range(60, 600, 30)]
        self.assertIsNone(jenerik_bul(cue_ayristir(_vtt(cueler)), 2700))


class TumCikarim(unittest.TestCase):
    def test_bos_altyazi_bos_isaret(self):
        sonuc = isaretleri_cikar("", 2700)
        self.assertIsNone(sonuc["intro_start"])
        self.assertIsNone(sonuc["credits_start"])
        self.assertEqual(sonuc["cue_count"], 0)

    def test_suresiz_icerik_isaret_uretmez(self):
        cueler = [(t, t + 3, "Diyalog.") for t in range(60, 600, 30)]
        sonuc  = isaretleri_cikar(_vtt(cueler), 0)
        self.assertIsNone(sonuc["intro_start"])
        self.assertIsNone(sonuc["credits_start"])


if __name__ == "__main__":
    unittest.main()

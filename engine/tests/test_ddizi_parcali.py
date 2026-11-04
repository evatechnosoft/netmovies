# DDizi "Full Tek Parça" bölümleri 1..N parça (Yeraltı 21: 9 parça, ~2 sa 7 dk); ilk parça
# oynatılınca izleyici bölümün ~13. dakikada bittiğini görüyordu.

import re
import unittest

from Plugins.__ddizi_parcali import birlestir, en_iyi_varyant, master_olustur, parca_adresleri

URL = "https://www.ddizi.tel/izle/92070/yeralti-21-bolum-izle-hd2.htm"
SAYFA = (
    f'<a href="{URL}">1.Parça</a>'
    + "".join(f'<a href="/izle/92070/yeralti-21-bolum-izle-hd2.htm/{n}">{n}.Parça</a>' for n in range(2, 5))
    + '<a href="/izle/99999/baska-dizi-1-bolum-izle.htm/2">kenar çubuğu</a>'
)
MASTER = (
    '#EXTM3U\n'
    '#EXT-X-MEDIA:NAME="Audio",TYPE=AUDIO,GROUP-ID="audio-64000",AUTOSELECT=YES,URI="/pl/mp4a/64000/a.m3u8"\n'
    '#EXT-X-MEDIA:NAME="Audio",TYPE=AUDIO,GROUP-ID="audio-128000",AUTOSELECT=YES,URI="/pl/mp4a/128000/a.m3u8"\n'
    '#EXT-X-STREAM-INF:BANDWIDTH=501050,RESOLUTION=640x360,CODECS="mp4a.40.2,avc1.4D401E",AUDIO="audio-64000"\n/pl/360/v.m3u8\n'
    '#EXT-X-STREAM-INF:BANDWIDTH=5878448,RESOLUTION=1920x1080,CODECS="mp4a.40.2,avc1.640032",AUDIO="audio-128000"\n/pl/1080/v.m3u8\n'
)


def _parca(n: int, adet: int) -> tuple[str, str]:
    govde = "".join(f"#EXTINF:3.000,\n/vid/{n}/{i}.m4s\n" for i in range(adet))
    return f'#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-MAP:URI="/vid/{n}/init.mp4"\n{govde}#EXT-X-ENDLIST\n', "https://video.twimg.com/pl/x.m3u8"


class DDiziParcaliTest(unittest.TestCase):
    def test_parca_adresleri_sirali_ve_yalniz_bu_bolum(self):
        self.assertEqual(parca_adresleri(SAYFA, URL), [URL] + [f"{URL}/{n}" for n in (2, 3, 4)])

    def test_parcasiz_bolum_bos_liste(self):
        self.assertEqual(parca_adresleri(f'<a href="{URL}">x</a>', URL), [])

    def test_en_yuksek_cozunurluk_ve_eslesen_ses(self):
        video, ses, nitelik = en_iyi_varyant(MASTER, "https://video.twimg.com/amplify_video/1/pl/m.m3u8?tag=29")
        self.assertEqual(video, "https://video.twimg.com/pl/1080/v.m3u8")
        self.assertEqual(ses, "https://video.twimg.com/pl/mp4a/128000/a.m3u8")
        self.assertEqual(nitelik["RESOLUTION"], "1920x1080")

    def test_birlestir_sure_discontinuity_ve_map(self):
        metin = birlestir([_parca(1, 2), _parca(2, 3)])
        self.assertEqual(sum(float(x) for x in re.findall(r"#EXTINF:([\d.]+)", metin)), 15.0)
        self.assertEqual(metin.count("#EXT-X-DISCONTINUITY"), 1)
        self.assertEqual(metin.count("#EXT-X-ENDLIST"), 1)
        self.assertIn('#EXT-X-MAP:URI="https://video.twimg.com/vid/2/init.mp4"', metin)
        self.assertLess(metin.index("/vid/1/1.m4s"), metin.index("/vid/2/init.mp4"))
        self.assertFalse([s for s in metin.splitlines() if s and not s.startswith("#") and not s.startswith("https://")])

    def test_master_tek_varyant_ve_ses(self):
        _, _, nitelik = en_iyi_varyant(MASTER, "https://t/m.m3u8")
        master = master_olustur("ddizi_birlesik?t=v", "ddizi_birlesik?t=a", nitelik)
        self.assertEqual(len(re.findall("#EXT-X-STREAM-INF", master)), 1)
        self.assertIn('CODECS="mp4a.40.2,avc1.640032",AUDIO="a"', master)
        self.assertIn('URI="ddizi_birlesik?t=a"', master)


if __name__ == "__main__":
    unittest.main()

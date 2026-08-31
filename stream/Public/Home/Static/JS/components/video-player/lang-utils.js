// Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

// Türkçe kaynak / ses izi / altyazı tespiti.
// Kaynak isimleri serbest metindir ("Türkçe", "Türkçe Dublaj", "Forced",
// "İngilizce Altyazı", "DiziYou | Türkçe Dublaj", "HDFilmCehennemi | TR", "OpenSub | TUR"...).
// Türkçe'nin büyük İ / küçük ı harfleri düz toLowerCase() ile bozulduğundan
// tr-lokali + ı→i normalizasyonu şart.

const normalize = (value) => (value || '').toLocaleLowerCase('tr').replace(/ı/g, 'i');

// Genel Türkçe işareti (ses veya altyazı adı için).
export const isTurkish = (name) => {
    const n = normalize(name);
    return /türk|turk|turkce|turkish|\btr\b|\btur\b/.test(n);
};

// "Forced" altyazı: dil adı taşımasa da zorlanmış altyazıdır; kendiliğinden AÇILMAMALI.
export const isForced = (name) => /forced|zorla/.test(normalize(name));

// Türkçe dublaj KAYNAĞI:
// 1. Adında "dublaj", "dubbed", "tr dub", "turkce dub", "türkçe ses" geçiyorsa ve yabancı dublaj değilse
// 2. Veya adında "tr", "türkçe", "turkce" geçip "altyazı/altyazi/sub/en/eng/english" içermiyorsa
export const isTurkishDub = (name) => {
    const n = normalize(name);
    if (/dublaj|dubbed|\bdub\b|turkce ses|türkçe ses/.test(n)) {
        return !/ingilizce|english|almanca|german|fransızca|fransizca|french|ispanyolca|spanish|japonca|japanese|korece|korean/.test(n);
    }
    // "Kaynak | TR" veya "TR Dub" gibi altyazısız TR ses belirteçleri
    if (/\btr\b|\bturkce\b|\btürkçe\b/.test(n)) {
        return !/altyaz|altyazi|sub|subtitle|orijinal|original|eng|english/.test(n);
    }
    return false;
};

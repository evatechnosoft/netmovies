// Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

// Türkçe kaynak / ses izi / altyazı tespiti.
// Kaynak isimleri serbest metindir ("Türkçe", "Türkçe Dublaj", "Forced",
// "İngilizce Altyazı", "DiziYou | Türkçe Dublaj"...). Türkçe'nin büyük İ / küçük ı
// harfleri düz toLowerCase() ile bozulduğundan tr-lokali + ı→i normalizasyonu şart.

const normalize = (value) => (value || '').toLocaleLowerCase('tr').replace(/ı/g, 'i');

// Genel Türkçe işareti (ses veya altyazı adı için).
export const isTurkish = (name) => {
    const n = normalize(name);
    return /türk|turk|turkce|\btr\b/.test(n);
};

// "Forced" altyazı: dil adı taşımasa da zorlanmış altyazıdır; kendiliğinden AÇILMAMALI.
export const isForced = (name) => /forced|zorla/.test(normalize(name));

// Türkçe dublaj KAYNAĞI (ayrı bir source; ses izi değil). Adında dublaj geçiyor ve
// açıkça yabancı dublaj (İngilizce/Almanca...) değilse Türkçe dublaj kabul edilir —
// TR platformunda "Dublaj" pratikte Türkçe dublajı ifade eder.
export const isTurkishDub = (name) => {
    const n = normalize(name);
    if (!/dublaj|dubbed/.test(n)) return false;
    return !/ingilizce|english|almanca|german|fransızca|fransizca|french|ispanyolca|spanish/.test(n);
};

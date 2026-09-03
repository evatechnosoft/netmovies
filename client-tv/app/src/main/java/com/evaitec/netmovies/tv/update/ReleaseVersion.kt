package com.evaitec.netmovies.tv.update

/**
 * Sürüm etiketi karşılaştırması — saf fonksiyon, test edilebilir.
 *
 * Eski OTA kontrolü yalnız "tag farklı mı" diye bakıyordu. Bu iki yönden yanlıştı:
 * GitHub listesinin başındaki release yayın sırasına göre değişebildiği için ESKİ
 * bir sürüm de "güncelleme" sanılabiliyordu, ve elde derlenmiş bir APK sonsuza dek
 * "güncelleme var" gösteriyordu. Artık yalnız SAYICA DAHA YENİ tag güncelleme sayılır.
 *
 * "v0.1.35-poc" -> [0, 1, 35]; tireden sonrası (-poc, -rc1) yok sayılır.
 */
object ReleaseVersion {

    private val NUMBER = Regex("""\d+""")

    fun parse(tag: String): List<Int> =
        NUMBER.findAll(tag.substringBefore('-'))
            .mapNotNull { it.value.toIntOrNull() }
            .toList()

    /**
     * [candidate], yüklü [installed] sürümünden yeni mi?
     * Taraflardan biri ayrıştırılamıyorsa false döner — bilinmeyen etikette
     * kullanıcıyı sonsuz güncelleme uyarısına boğmaktansa sessiz kalmak yeğdir.
     */
    fun isNewerThan(candidate: String, installed: String): Boolean {
        val new = parse(candidate)
        val cur = parse(installed)
        if (new.isEmpty() || cur.isEmpty()) return false
        for (i in 0 until maxOf(new.size, cur.size)) {
            val a = new.getOrElse(i) { 0 }
            val b = cur.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}

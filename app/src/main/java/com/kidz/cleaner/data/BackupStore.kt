package com.kidz.cleaner.data

import android.content.Context
import com.kidz.cleaner.backend.CleanerEngine
import org.json.JSONArray
import org.json.JSONObject

/** Isi berkas backup daftar app yang dibekukan / di-debloat / dibatasi. */
data class BackupData(
    val frozen: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val restricted: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
)

/** Ekspor / impor daftar aksi (freeze, debloat, hemat baterai) ke JSON. */
object BackupStore {

    /** Kumpulkan keadaan sekarang dari perangkat. */
    fun snapshot(engine: CleanerEngine): BackupData {
        val frozen = runCatching { engine.frozenPackages() }.getOrDefault(emptyList())
        val removed = runCatching { engine.removedPackages() }.getOrDefault(emptyList())
        val restricted = runCatching {
            engine.batteryRestrictedPackages().toList()
        }.getOrDefault(emptyList())
        return BackupData(
            frozen = frozen.sorted(),
            removed = removed.sorted(),
            restricted = restricted.sorted(),
        )
    }

    /** Ubah [BackupData] menjadi teks JSON. */
    fun toJson(data: BackupData): String {
        val o = JSONObject()
        o.put("version", data.version)
        o.put("createdAt", data.createdAt)
        o.put("frozen", JSONArray(data.frozen))
        o.put("removed", JSONArray(data.removed))
        o.put("restricted", JSONArray(data.restricted))
        return o.toString(2)
    }

    /** Baca [BackupData] dari teks JSON. Kembalikan null bila tidak valid. */
    fun fromJson(text: String): BackupData? = runCatching {
        val o = JSONObject(text)
        BackupData(
            frozen = o.optJSONArray("frozen").toStringList(),
            removed = o.optJSONArray("removed").toStringList(),
            restricted = o.optJSONArray("restricted").toStringList(),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            version = o.optInt("version", 1),
        )
    }.getOrNull()

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val out = ArrayList<String>(length())
        for (i in 0 until length()) out += optString(i)
        return out.filter { it.isNotBlank() }
    }
}

package com.kidz.cleaner.work

import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kidz.cleaner.R
import com.kidz.cleaner.backend.CleanerEngine
import com.kidz.cleaner.backend.ShizukuRunner
import com.kidz.cleaner.data.Aggressiveness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Tombol "Bersihkan" di panel Quick Settings (tarik dari atas).
 *
 * Satu sentuhan langsung membersihkan RAM + cache, tanpa membuka app.
 */
class QuickCleanTile : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartListening() {
        super.onStartListening()
        updateTile("Bersihkan RAM & cache", Tile.STATE_INACTIVE)
    }

    override fun onClick() {
        super.onClick()
        updateTile("Membersihkan…", Tile.STATE_ACTIVE)

        scope.launch {
            val result = runCatching {
                val runner = ShizukuRunner()
                if (!runner.isReady()) {
                    return@launch updateTile("Shizuku belum aktif", Tile.STATE_UNAVAILABLE)
                }
                val engine = CleanerEngine(applicationContext, runner)
                val level = Aggressiveness.MEDIUM
                val rep = engine.clearRamByLevel(level)
                val (_, avail) = engine.ramInfo()

                val cacheApps = runCatching {
                    engine.listApps(includeSystem = false).map { it.packageName }
                }.getOrDefault(emptyList())
                val cacheRep = if (cacheApps.isNotEmpty()) {
                    runCatching { engine.clearCache(cacheApps) }.getOrNull()
                } else null

                val freed = (rep.freedBytes.coerceAtLeast(0)) +
                    (cacheRep?.freedBytes?.coerceAtLeast(0) ?: 0)
                val text = if (freed > 0) "Dibebaskan ${fmt(freed)}" else "Selesai · ${fmt(avail)} bebas"
                Notifier.showResult(applicationContext, "Bersihkan cepat", text)
                text
            }.getOrElse { "Gagal: ${it.message?.take(40)}" }

            updateTile(result, Tile.STATE_INACTIVE)
        }
    }

    private fun updateTile(label: String, state: Int) {
        runCatching {
            qsTile?.apply {
                this.state = state
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    subtitle = label
                }
                icon = Icon.createWithResource(this@QuickCleanTile, R.drawable.ic_tile_clean)
                updateTile()
            }
        }
    }

    private fun fmt(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes / 1024.0 / 1024.0
        return if (mb >= 1024) String.format("%.2f GB", mb / 1024) else String.format("%.0f MB", mb)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Minta sistem menyegarkan tampilan tile (dipanggil dari UI). */
        fun requestRefresh(context: android.content.Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, QuickCleanTile::class.java),
                )
            }
        }
    }
}

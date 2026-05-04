package com.example.tile

import android.content.Context
import android.media.AudioManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Volume Tile — tugasnya hanya SATU:
 * Tampilkan native Android volume bar saat tile ditekan.
 *
 * Tidak mengubah volume sendiri, tidak menyimpan state.
 * Tile selalu STATE_INACTIVE (tidak menyala), berfungsi seperti tombol biasa.
 * Volume bar muncul berkat FLAG_SHOW_UI — persis seperti menekan tombol fisik.
 */
class VolumeTileService : TileService() {

    private lateinit var audioManager: AudioManager

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartListening() {
        super.onStartListening()
        // Tile tampil sebagai tombol biasa — tidak menyala/aktif
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_volume)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // Hanya tampilkan volume bar bawaan sistem, tanpa mengubah nilai volume
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_SAME,   // Tidak ubah volume
            AudioManager.FLAG_SHOW_UI   // Tampilkan native volume bar
        )
        // Tidak ada perubahan state tile — tetap INACTIVE
    }
}

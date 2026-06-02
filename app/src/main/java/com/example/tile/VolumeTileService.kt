package com.aarash.idin

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Volume Tile Service.
 *
 * Mengatasi isu di mana panel Quick Settings yang sedang terbuka menutupi
 * atau memblokir tampilan volume bar bawaan sistem.
 * 
 * Alur Kerja:
 * 1. Tutup panel Quick Settings secara aman.
 * 2. Berikan jeda waktu singkat (250ms) agar animasi tutup selesai.
 * 3. Panggil native volume bar untuk muncul di layar.
 */
class VolumeTileService : TileService() {

    private lateinit var audioManager: AudioManager

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_volume)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        // 1. Tutup panel Quick Settings terlebih dahulu
        collapseStatusBar()

        // 2. Tampilkan volume bar bawaan sistem setelah jeda animasi tutup panel (250ms)
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_SAME,   // Tidak mengubah nilai volume
                    AudioManager.FLAG_SHOW_UI   // Tampilkan bar volume bawaan sistem
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 250)
    }

    @SuppressLint("WrongConstant")
    private fun collapseStatusBar() {
        var isCollapsed = false

        // Lapis 1: StatusBarManager Reflection
        try {
            val statusBarService = getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val collapsePanels = statusBarManager.getMethod("collapsePanels")
            collapsePanels.invoke(statusBarService)
            isCollapsed = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Lapis 2: Perintah BACK dari Layanan Aksesibilitas (Android 9+)
        if (!isCollapsed) {
            val accessibilityService = ScreenshotAccessibilityService.activeInstance
            if (accessibilityService != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    accessibilityService.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    isCollapsed = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Lapis 3: Fallback Broadcast untuk versi Android lama (di bawah Android 12)
        if (!isCollapsed) {
            try {
                @Suppress("DEPRECATION")
                val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                sendBroadcast(closeIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

package com.aarash.idin

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.TextUtils
import android.util.Log
import android.widget.Toast

/**
 * Screenshot Tile Service.
 *
 * Logika Izin (Aksesibilitas):
 * - Cek apakah ScreenshotAccessibilityService sudah aktif.
 *   → Ya : Langsung panggil fungsi screenshot bawaan sistem.
 *   → Tidak: Buka halaman pengaturan Aksesibilitas agar user mengaktifkannya.
 *
 * Tile selalu INACTIVE — tidak pernah menyala/glowing.
 */
class ScreenshotTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_screenshot)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        Log.d("ScreenshotTile", "Tile clicked")

        val accessibilityService = ScreenshotAccessibilityService.activeInstance
        val isEnabledInSettings = isAccessibilityEnabled()

        if (isEnabledInSettings && accessibilityService != null) {
            Log.d("ScreenshotTile", "Service is enabled and active. Taking screenshot.")
            safeCollapsePanel(accessibilityService)
            
            // Jeda waktu yang cukup lama (750ms)
            // Ini memberi waktu OS untuk menyelesaikan animasi tutup panel,
            // atau memberi pengguna waktu untuk menutup manual jika device tidak mendukung auto-close
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    accessibilityService.takeScreenshot()
                } catch (e: Exception) {
                    Log.e("ScreenshotTile", "Error taking screenshot", e)
                }
            }, 750)
        } else if (isEnabledInSettings && accessibilityService == null) {
            Log.d("ScreenshotTile", "Service is enabled in settings but instance is null. Attempting to wake up.")
            try {
                startService(Intent(this, ScreenshotAccessibilityService::class.java))
            } catch (e: Exception) {
                Log.e("ScreenshotTile", "Error waking up service", e)
            }
            
            safeCollapsePanel(null)
            Handler(Looper.getMainLooper()).postDelayed({
                val retryInstance = ScreenshotAccessibilityService.activeInstance
                if (retryInstance != null) {
                    Log.d("ScreenshotTile", "Service woke up successfully. Taking screenshot.")
                    try {
                        retryInstance.takeScreenshot()
                    } catch (e: Exception) {
                        Log.e("ScreenshotTile", "Error taking screenshot after retry", e)
                    }
                } else {
                    Log.e("ScreenshotTile", "Service failed to wake up.")
                    Toast.makeText(this, "Layanan tidak merespon. Harap matikan dan nyalakan kembali Aksesibilitas.", Toast.LENGTH_LONG).show()
                }
            }, 1000)
        } else {
            Log.d("ScreenshotTile", "Service is NOT enabled. Prompting user.")
            Toast.makeText(this, R.string.notif_enable_accessibility, Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
        }
    }

    @SuppressLint("WrongConstant")
    private fun safeCollapsePanel(accessibilityService: ScreenshotAccessibilityService?) {
        Log.d("ScreenshotTile", "Attempting to collapse panel")
        
        var isCollapsed = false

        // Pendekatan utama: Menggunakan reflection ke StatusBarManager
        // Ini membutuhkan permission EXPAND_STATUS_BAR di AndroidManifest
        try {
            val statusBarService = getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val collapsePanels = statusBarManager.getMethod("collapsePanels")
            collapsePanels.invoke(statusBarService)
            Log.d("ScreenshotTile", "Panel collapsed via StatusBarManager")
            isCollapsed = true
        } catch (e: Exception) {
            Log.e("ScreenshotTile", "Error closing panels via StatusBarManager", e)
        }

        // Pendekatan kedua: Aksesibilitas aksi tombol BACK
        // Jika panel QS terbuka, menekan tombol back akan menutupnya.
        // Hanya dilakukan jika pendekatan pertama gagal.
        if (!isCollapsed && accessibilityService != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                accessibilityService.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                Log.d("ScreenshotTile", "Panel collapsed via Accessibility BACK action")
                isCollapsed = true
            } catch (e: Exception) {
                Log.e("ScreenshotTile", "Error closing panels via Accessibility BACK", e)
            }
        }

        // Pendekatan cadangan (Fallback 3): Menggunakan Broadcast
        // Ini bekerja sangat baik di Android 11 ke bawah, tapi diblokir di Android 12+
        if (!isCollapsed) {
            try {
                @Suppress("DEPRECATION")
                val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                sendBroadcast(closeIntent)
                Log.d("ScreenshotTile", "Panel collapsed via Broadcast")
            } catch (e: SecurityException) {
                Log.w("ScreenshotTile", "SecurityException closing system dialogs (expected on API 31+)", e)
            } catch (e: Exception) {
                Log.e("ScreenshotTile", "Error closing system dialogs via broadcast", e)
            }
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = android.app.PendingIntent.getActivity(
                    this, 0, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            Log.e("ScreenshotTile", "Error opening accessibility settings", e)
            Toast.makeText(this, "Gagal membuka pengaturan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        try {
            val expectedComponentName = ComponentName(this, ScreenshotAccessibilityService::class.java)
            val enabledServicesSetting = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            if (enabledServicesSetting.isNullOrEmpty()) return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)

            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                if (enabledComponent != null && enabledComponent == expectedComponentName) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenshotTile", "Error checking accessibility status", e)
        }
        return false
    }
}

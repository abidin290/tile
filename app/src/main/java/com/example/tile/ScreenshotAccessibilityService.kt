package com.example.tile

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Layanan Aksesibilitas yang sangat ringan.
 *
 * Tujuan SATU-SATUNYA: Menerima perintah untuk menjalankan
 * GLOBAL_ACTION_TAKE_SCREENSHOT.
 * Layanan ini TIDAK mendengarkan event apa pun, jadi tidak
 * akan boros baterai atau memori.
 */
class ScreenshotAccessibilityService : AccessibilityService() {

    companion object {
        // Menyimpan referensi statis agar Tile bisa memanggilnya langsung
        @Volatile
        var activeInstance: ScreenshotAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Tidak melakukan apa-apa. Config diset ke "" untuk event.
    }

    override fun onInterrupt() {
        // Tidak melakukan apa-apa.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        activeInstance = null
        return super.onUnbind(intent)
    }

    /**
     * Dipanggil oleh ScreenshotTileService saat tile ditekan.
     */
    fun takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9 (API 28)+
            val success = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            if (!success) {
                Toast.makeText(this, R.string.notif_screenshot_failed, Toast.LENGTH_SHORT).show()
            }
        } else {
            // Fallback jika device di bawah Android 9
            Toast.makeText(this, "Fitur ini butuh Android 9 (Pie) ke atas", Toast.LENGTH_LONG).show()
        }
    }
}

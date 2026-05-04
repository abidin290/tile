# Android Quick Settings Tile App (No Launcher UI)

## 🎯 Tujuan
Membuat aplikasi Android tanpa tampilan di home screen (tanpa launcher icon) yang hanya muncul di Quick Settings Tile dengan dua fungsi utama:

1. Screenshot layar
2. Kontrol volume

---

## ⚙️ Kebutuhan Fitur

### 1. Screenshot
- Trigger dari Quick Settings Tile
- Menggunakan MediaProjection API
- Tidak menampilkan UI (gunakan Activity transparan/invisible)
- Otomatis menyimpan hasil screenshot
- (Opsional) auto share / auto crop

### 2. Volume Control
- Trigger dari Quick Settings Tile
- Bisa:
  - Volume naik
  - Volume turun
  - Mute / unmute
- Tidak memerlukan Activity (bisa langsung via Service)

---

## 🧩 Arsitektur Aplikasi

Quick Settings Tile:
- Tile 1 → ScreenshotTileService → Activity → Capture layar
- Tile 2 → VolumeTileService → AudioManager → Ubah volume

---

## 🚫 UI Behavior
- Tidak ada launcher icon (hapus intent MAIN + LAUNCHER di manifest)
- Tidak ada tampilan UI utama
- Activity hanya digunakan untuk izin screenshot (transparan)

---

## ⚠️ Batasan Sistem Android
- Screenshot tetap membutuhkan izin user (MediaProjection)
- Tidak bisa berjalan sepenuhnya di background tanpa Activity
- Volume control bisa berjalan tanpa UI

---

## 💡 Tujuan UX
- Akses cepat dari Quick Settings
- Ringan dan minimalis
- Terasa seperti fitur bawaan Android
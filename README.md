# QuickTile: Minimalist Android Quick Settings 🚀

QuickTile adalah aplikasi Android super ringan yang berfokus pada fungsionalitas murni melalui **Quick Settings Panel**. Aplikasi ini **tidak memiliki UI (User Interface)** di layar utama dan tidak akan muncul di *App Drawer* (menu aplikasi). Semuanya dijalankan secara instan melalui sistem *Tile* bawaan Android.

## ✨ Fitur Utama

### 1. 📸 Screenshot Tile (Tanpa Dialog Izin!)
Berbeda dengan aplikasi screenshot pada umumnya yang menggunakan `MediaProjection` (yang selalu memunculkan popup konfirmasi setiap kali ditekan), QuickTile menggunakan pendekatan **Accessibility Service**. 
* **Sekali Izin, Seterusnya Instan**: Cukup aktifkan layanan Aksesibilitas satu kali, dan Anda bisa mengambil screenshot selamanya tanpa gangguan popup.
* **Auto-Collapse Cerdas**: Memiliki mekanisme 3 lapis untuk memastikan panel Quick Settings tertutup sempurna sebelum gambar diambil, sehingga hasil screenshot bersih dari bayangan menu sistem.
* **Sangat Ringan**: Layanan Aksesibilitas dikonfigurasi untuk *tidur* dan sama sekali **tidak mendengarkan/memantau** aktivitas layar Anda (`accessibilityEventTypes=""`). Baterai dan RAM Anda tetap aman!

### 2. 🔊 Volume Tile
* Berfungsi sebagai *trigger* murni untuk memunculkan **indikator volume bawaan sistem** Android.
* Tidak mengubah volume secara paksa, melainkan memberikan Anda kontrol slider volume persis seperti saat menekan tombol fisik HP.

## 🛠️ Cara Instalasi & Penggunaan

Karena aplikasi ini *headless* (tanpa UI Utama), cara menggunakannya sedikit berbeda dari aplikasi biasa:

1. **Install APK** atau *Build* proyek ini menggunakan Android Studio.
2. Usap layar dari atas ke bawah untuk membuka menu **Quick Settings**.
3. Tekan tombol **Edit** (ikon pensil ✏️).
4. Gulir ke bawah, cari *tile* bernama **Screenshot** dan **Volume** dari aplikasi QuickTile.
5. Seret (drag) kedua *tile* tersebut ke panel aktif di atas.

### Setup Awal Screenshot (Hanya Sekali)
Saat pertama kali Anda menekan Tile Screenshot:
1. Anda akan diarahkan ke halaman **Pengaturan Aksesibilitas** Android.
2. Cari aplikasi **QuickTile** di daftar layanan.
3. **Aktifkan** layanan tersebut.
4. Selesai! Mulai sekarang, tekan Tile Screenshot kapan pun untuk mengambil gambar secara instan.

## 🧠 Cara Kerja (Under the Hood)

### 🔹 Screenshot Mechanism
Aplikasi ini menangani isu klasik *Quick Settings* yang sering ikut terekam saat screenshot, menggunakan pendekatan *fallback* 3 lapis:
1. **Reflection API**: Mencoba menutup panel via `StatusBarManager.collapsePanels`.
2. **Accessibility Back Action**: Jika diblokir oleh vendor HP, aplikasi memicu `GLOBAL_ACTION_BACK` untuk menutup menu yang sedang terbuka.
3. **Broadcast Intent**: `ACTION_CLOSE_SYSTEM_DIALOGS` sebagai langkah terakhir untuk OS Android lawas (API < 31).

Setelah panel dijamin tertutup (dengan *delay* aman 750ms), aplikasi memanggil perintah sistem `GLOBAL_ACTION_TAKE_SCREENSHOT` yang menghasilkan animasi dan suara screenshot persis seperti aslinya.

### 🔹 Tech Stack
* **Bahasa**: Kotlin
* **Minimum SDK**: API 24 (Android 7.0)
* **Target SDK**: API 35
* **Core API**: `TileService`, `AccessibilityService`, `AudioManager`

## 🛡️ Keamanan & Privasi
Aplikasi ini 100% aman dan *open-source*. 
* Tidak ada izin Internet (`INTERNET`).
* Tidak ada izin baca/tulis penyimpanan (*Storage*) di luar folder publik bawaan.
* Tidak ada aktivitas di latar belakang (Background Process) yang berjalan terus-menerus.

## 🤝 Kontribusi
Punya ide untuk *Tile* minimalis lainnya? *Pull requests* sangat diterima! Silakan di- *fork* dan ajukan penambahan fitur.

---
*Dibuat dengan ❤️ untuk ekosistem Android yang lebih bersih.*

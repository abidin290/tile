# QuickTile: Minimalist Android Quick Settings 🚀

QuickTile adalah aplikasi Android super ringan yang berfokus pada fungsionalitas murni melalui **Quick Settings Panel**. Aplikasi ini **tidak memiliki UI (User Interface)** di layar utama dan tidak akan muncul di *App Drawer* (menu aplikasi). Semuanya dijalankan secara instan melalui sistem *Tile* bawaan Android.

---

## ✨ Fitur Utama

### 1. 📸 Screenshot Tile & Zero-Latency Markup Editor
Berbeda dengan aplikasi screenshot biasa, QuickTile menggunakan kombinasi **Accessibility Service** dan **WindowManager Overlay** untuk alur tangkapan layar yang instan dan mulus:
* **Sekali Izin, Seterusnya Instan**: Cukup aktifkan layanan Aksesibilitas satu kali, dan Anda bisa mengambil screenshot selamanya tanpa gangguan popup dialog konfirmasi MediaProjection.
* **Zero-Latency Editor Overlay (0ms Delay)**: Gambar hasil screenshot langsung dimuat dari memori RAM ke kanvas editor tanpa proses tulis-baca disk sementara. Panel editor kustom premium akan **meluncur naik secara instan** di atas aplikasi yang sedang aktif tanpa terasa berganti layar aplikasi!
* **Alat Markup Presisi Tinggi**:
  * **Coret (Brush)**: Menggambar bebas dengan pilihan palet warna modis dan ketebalan garis yang dinamis.
  * **Bentuk (Shapes)**: Membuat Persegi, Persegi Bulat, Oval/Lingkaran, Garis, dan Panah petunjuk secara presisi.
  * **Potong (Crop)**: Frame kisi-kisi pemotongan (*Rule of Thirds*) dengan dukungan rasio instan (Bebas, 1:1, 4:3, 16:9, dan Full Image).
  * **Zoom & Pan 2 Jari**: Memperbesar dan menggeser layar edit secara instan kapan saja menggunakan cubitan 2 jari.
* **Penghancur Pop-up Kasar**:
  * Mengintersepsi tombol BACK fisik HP untuk memunculkan popup konfirmasi pembatalan edit yang didesain kustom melayang di atas layar.
  * Tombol **Bagikan (Share)** secara otomatis menutup kanvas melayang agar menu chooser berbagi bawaan Android ("Bagikan via") dapat muncul di depan layar tanpa terhalang.

### 2. 🔊 Volume Tile (Auto-Collapse)
* Berfungsi sebagai *trigger* instan untuk memunculkan **indikator volume bawaan sistem** Android.
* Secara otomatis menutup laci Quick Settings terlebih dahulu dengan jeda animasi mulus 250ms, sehingga bar volume dapat tergambar jelas di layar utama tanpa terhalang oleh menu sistem.

---

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
2. Cari aplikasi **QuickTile** di daftar layanan terinstal.
3. **Aktifkan** layanan tersebut (menyetujui izin kemampuan screenshot).
4. Selesai! Mulai sekarang, tekan Tile Screenshot kapan pun untuk mengambil gambar secara instan.

---

## 🧠 Cara Kerja (Under the Hood)

### 🔹 Screenshot & Overlay Mechanism
Aplikasi ini menangani isu klasik *Quick Settings* yang sering ikut terekam saat screenshot, menggunakan pendekatan *fallback* 3 lapis untuk menutup panel laci sebelum pengambilan gambar:
1. **Reflection API**: Mencoba menutup panel via `StatusBarManager.collapsePanels`.
2. **Accessibility Back Action**: Jika diblokir oleh vendor HP, aplikasi memicu `GLOBAL_ACTION_BACK` untuk menutup menu yang sedang terbuka.
3. **Broadcast Intent**: `ACTION_CLOSE_SYSTEM_DIALOGS` sebagai langkah terakhir untuk OS Android lawas (API < 31).

Setelah laci ditutup, layanan Aksesibilitas memanggil `takeScreenshot(...)` pada Android 11+ (API 30+) untuk mendapatkan `Bitmap` mentah, yang langsung dirender ke `WindowManager.addView` dengan tipe jendela kustom `TYPE_ACCESSIBILITY_OVERLAY` untuk kecepatan respons 0ms.

### 🔹 Tech Stack
* **Bahasa**: Kotlin
* **Minimum SDK**: API 24 (Android 7.0)
* **Target SDK**: API 35
* **Core API**: `TileService`, `AccessibilityService`, `WindowManager` (Overlay), `MediaStore` (Lossless saving)

---

## 🛡️ Keamanan & Privasi

Aplikasi ini 100% aman, privat, dan *open-source*. 
* **100% Offline**: Sama sekali tidak ada izin Internet (`INTERNET`). Seluruh proses screenshot dan edit diselesaikan murni di dalam memori lokal HP Anda tanpa mengirim data ke server mana pun.
* Tidak ada izin baca/tulis penyimpanan (*Storage*) di luar folder publik bawaan (`Pictures/Screenshots` via MediaStore).
* Tidak ada aktivitas di latar belakang (Background Process) yang berjalan terus-menerus.

---

## 🤝 Kontribusi

Punya ide untuk *Tile* minimalis lainnya? *Pull requests* sangat diterima! Silakan di- *fork* dan ajukan penambahan fitur.

---
*Dibuat dengan ❤️ untuk ekosistem Android yang lebih bersih.*

# OMIBAT — aplikasi pendamping minum obat keluarga

Versi ini mengubah pendekatan aplikasi dari admin panel menjadi aplikasi dengan dua peran:
- CAREGIVER: anak yang mendampingi satu atau beberapa orang tua.
- ORANG_TUA: orang tua yang melihat jadwal dan mengonfirmasi konsumsi obat.

## Teknologi
Java 25, Spring Boot 3.5.7, Thymeleaf, JDBC, PostgreSQL, BCrypt.

## 1. Database
Gunakan database `omibat` yang sudah berisi sembilan tabel dari file `OMIBAT.sql`:
orang_tua, anak_caregiver, hubungan_keluarga, master_obat, jadwal_obat,
riwayat_kepatuhan, master_faskes, jadwal_kontrol, arsip_medis.

Jalankan SEKALI SQL tambahan `src/main/resources/db/migration/V2__akun_pengguna.sql`
pada database yang sama. File ini hanya membuat tabel `akun_pengguna`, tidak menghapus
atau mengubah sembilan tabel utama.

## 2. Atur koneksi
Edit `src/main/resources/application.properties`:
- spring.datasource.url
- spring.datasource.username
- spring.datasource.password

## 3. Jalankan
Dari folder yang berisi pom.xml:
```bash
mvn clean spring-boot:run
```
Buka http://localhost:8080

## 4. Buat akun
Pilih "Daftar akun". NIK harus sudah ada di `anak_caregiver` atau `orang_tua`.
Aplikasi menentukan peran secara otomatis:
- NIK pada `anak_caregiver` -> CAREGIVER
- NIK pada `orang_tua` -> ORANG_TUA
Password minimal 8 karakter dan disimpan sebagai BCrypt hash.

Data contoh dari OMIBAT.sql:
- Caregiver: 3201010101850002 (Budi Santoso)
- Orang tua: 3201010101500001 (Sutrisno)
- Orang tua: 3201010101550003 (Siti Aminah)

## Alur
1. Daftarkan akun caregiver menggunakan NIK caregiver yang sudah ada.
2. Daftarkan akun masing-masing orang tua menggunakan NIK orang tua.
3. Login sesuai akun; dashboard ditentukan otomatis dari peran.
4. Caregiver mengelola jadwal obat dan jadwal kontrol hanya untuk orang tua yang terhubung di `hubungan_keluarga`.
5. Orang tua melihat jadwal miliknya dan menekan "Saya sudah minum" untuk membuat catatan `SELESAI` pada `riwayat_kepatuhan`.
6. Kedua peran dapat melihat riwayat sesuai cakupan relasinya.

## Catatan cakupan
- Tabel `arsip_medis` tetap dipertahankan, tetapi UI unggah/unduh file belum termasuk pada versi ini.
- Data master obat dan fasilitas kesehatan diasumsikan disiapkan sebelumnya pada database; tidak ada menu admin.
- Fitur lupa password, OTP, notifikasi terjadwal, dan pembatasan brute-force belum tersedia.
- Ini prototipe akademik. Sebelum penggunaan nyata untuk data kesehatan, perlu hardening keamanan, audit akses, HTTPS, backup, dan persetujuan pengguna.

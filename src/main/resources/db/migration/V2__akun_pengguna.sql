-- Jalankan SEKALI pada database OMIBAT yang sudah berisi 9 tabel awal.
CREATE TABLE IF NOT EXISTS akun_pengguna (
    id_akun BIGSERIAL PRIMARY KEY,
    nik VARCHAR(16) NOT NULL UNIQUE,
    peran VARCHAR(20) NOT NULL CHECK (peran IN ('CAREGIVER','ORANG_TUA')),
    password_hash VARCHAR(100) NOT NULL,
    dibuat_pada TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_akun_anak FOREIGN KEY (nik)
        REFERENCES anak_caregiver(nik_anak) ON DELETE CASCADE
        DEFERRABLE INITIALLY DEFERRED
);
-- Akun orang tua menggunakan NIK orang tua, caregiver menggunakan NIK anak.
-- Karena NIK merujuk ke dua tabel berbeda, constraint FK tunggal di atas
-- tidak cocok untuk dua tipe akun. Hapus FK di atas dan gunakan validasi aplikasi:
ALTER TABLE akun_pengguna DROP CONSTRAINT IF EXISTS fk_akun_anak;

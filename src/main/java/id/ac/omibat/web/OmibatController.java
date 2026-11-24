package id.ac.omibat.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Controller
public class OmibatController {
  private final JdbcTemplate db;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
  public OmibatController(JdbcTemplate db) { this.db = db; }

  private String role(HttpSession s) { return (String)s.getAttribute("role"); }
  private String nik(HttpSession s) { return (String)s.getAttribute("nik"); }
  private boolean logged(HttpSession s) { return role(s) != null && nik(s) != null; }
  private String deny(HttpSession s, String required) {
    if (!logged(s)) return "redirect:/login";
    if (!required.equals(role(s))) return "redirect:/dashboard";
    return null;
  }

  @GetMapping("/") public String root(HttpSession s) { return logged(s) ? "redirect:/dashboard" : "redirect:/login"; }

  @GetMapping("/login") public String login(HttpSession s) {
    return logged(s) ? "redirect:/dashboard" : "login";
  }

  @PostMapping("/login") public String doLogin(@RequestParam String nik, @RequestParam String password,
      HttpSession session, Model model) {
    List<Map<String,Object>> rows = db.queryForList(
      "SELECT nik, peran, password_hash FROM akun_pengguna WHERE nik = ?", nik.trim());
    if (rows.isEmpty() || !encoder.matches(password, (String)rows.get(0).get("password_hash"))) {
      model.addAttribute("error", "NIK atau password tidak sesuai.");
      return "login";
    }
    session.setAttribute("nik", rows.get(0).get("nik").toString());
    session.setAttribute("role", rows.get(0).get("peran").toString());
    return "redirect:/dashboard";
  }

  @GetMapping("/daftar-akun") public String register() { return "register"; }

  @PostMapping("/daftar-akun") public String doRegister(@RequestParam String nik,
      @RequestParam String password, @RequestParam String confirmPassword, Model model) {
    nik = nik.trim();
    if (!nik.matches("\\d{16}") || password.length() < 8 || !password.equals(confirmPassword)) {
      model.addAttribute("error", "Pastikan NIK terdiri dari 16 digit, password minimal 8 karakter, dan konfirmasi sama.");
      return "register";
    }
    boolean caregiver = !db.queryForList("SELECT nik_anak FROM anak_caregiver WHERE nik_anak=?", nik).isEmpty();
    boolean parent = !db.queryForList("SELECT nik_ortu FROM orang_tua WHERE nik_ortu=?", nik).isEmpty();
    if (caregiver == parent) {
      model.addAttribute("error", "NIK tidak ditemukan atau terdaftar ganda. Pastikan data caregiver/orang tua sudah dimasukkan ke database.");
      return "register";
    }
    if (!db.queryForList("SELECT nik FROM akun_pengguna WHERE nik=?", nik).isEmpty()) {
      model.addAttribute("error", "Akun untuk NIK ini sudah terdaftar. Silakan login.");
      return "register";
    }
    db.update("INSERT INTO akun_pengguna(nik,peran,password_hash) VALUES (?,?,?)",
      nik, caregiver ? "CAREGIVER" : "ORANG_TUA", encoder.encode(password));
    return "redirect:/login?registered";
  }

  @GetMapping("/logout") public String logout(HttpSession s) { s.invalidate(); return "redirect:/login"; }

  @GetMapping("/dashboard") public String dashboard(HttpSession s, Model m) {
    if (!logged(s)) return "redirect:/login";
    String n = nik(s), r = role(s);
    m.addAttribute("role", r); m.addAttribute("nik", n);
    if ("CAREGIVER".equals(r)) {
      List<Map<String,Object>> parents = db.queryForList("""
        SELECT o.nik_ortu,o.nama_ortu,h.hubungan
        FROM hubungan_keluarga h JOIN orang_tua o ON o.nik_ortu=h.nik_ortu
        WHERE h.nik_anak=? ORDER BY o.nama_ortu
        """, n);
      m.addAttribute("parents", parents);
      m.addAttribute("scheduleCount", db.queryForObject("""
        SELECT count(*) FROM jadwal_obat j JOIN hubungan_keluarga h ON h.nik_ortu=j.nik_ortu
        WHERE h.nik_anak=?
        """, Integer.class, n));
      m.addAttribute("controlCount", db.queryForObject("""
        SELECT count(*) FROM jadwal_kontrol k JOIN hubungan_keluarga h ON h.nik_ortu=k.nik_ortu
        WHERE h.nik_anak=? AND k.tgl_kontrol >= CURRENT_DATE
        """, Integer.class, n));
      m.addAttribute("today", db.queryForList("""
        SELECT o.nama_ortu, ob.nama_obat, j.dosis, j.jam_jadwal
        FROM jadwal_obat j JOIN orang_tua o ON o.nik_ortu=j.nik_ortu
        JOIN master_obat ob ON ob.id_obat=j.id_obat
        JOIN hubungan_keluarga h ON h.nik_ortu=j.nik_ortu
        WHERE h.nik_anak=? ORDER BY j.jam_jadwal
        """, n));
      return "caregiver-dashboard";
    } else {
      Map<String,Object> parent = db.queryForMap("SELECT nama_ortu FROM orang_tua WHERE nik_ortu=?", n);
      m.addAttribute("nama", parent.get("nama_ortu"));
      m.addAttribute("today", db.queryForList("""
        SELECT j.id_jadwal, ob.nama_obat, ob.satuan, j.dosis, j.frekuensi,
               j.aturan_minum, j.jam_jadwal,
               (SELECT r.status_kepatuhan FROM riwayat_kepatuhan r
                WHERE r.id_jadwal=j.id_jadwal AND r.tgl_log=CURRENT_DATE
                ORDER BY r.id_riwayat DESC LIMIT 1) AS status_hari_ini
        FROM jadwal_obat j JOIN master_obat ob ON ob.id_obat=j.id_obat
        WHERE j.nik_ortu=? ORDER BY j.jam_jadwal
        """, n));
      m.addAttribute("controls", db.queryForList("""
        SELECT k.tgl_kontrol,k.dokter_poli,f.nama_faskes,f.tipe_faskes
        FROM jadwal_kontrol k JOIN master_faskes f ON f.id_faskes=k.id_faskes
        WHERE k.nik_ortu=? AND k.tgl_kontrol >= CURRENT_DATE
        ORDER BY k.tgl_kontrol LIMIT 5
        """, n));
      return "parent-dashboard";
    }
  }

  @GetMapping("/caregiver/jadwal") public String caregiverSchedule(HttpSession s, Model m) {
    String d=deny(s,"CAREGIVER"); if(d!=null)return d;
    m.addAttribute("rows", db.queryForList("""
      SELECT j.id_jadwal,o.nama_ortu,j.nik_ortu,ob.nama_obat,ob.satuan,j.dosis,j.frekuensi,j.aturan_minum,j.jam_jadwal
      FROM jadwal_obat j JOIN orang_tua o ON o.nik_ortu=j.nik_ortu
      JOIN master_obat ob ON ob.id_obat=j.id_obat
      JOIN hubungan_keluarga h ON h.nik_ortu=j.nik_ortu
      WHERE h.nik_anak=? ORDER BY o.nama_ortu,j.jam_jadwal
      """, nik(s)));
    m.addAttribute("parents", connectedParents(nik(s)));
    m.addAttribute("medicines", db.queryForList("SELECT id_obat,nama_obat,satuan FROM master_obat ORDER BY nama_obat"));
    return "caregiver-schedule";
  }

  private List<Map<String,Object>> connectedParents(String child) {
    return db.queryForList("SELECT o.nik_ortu,o.nama_ortu FROM hubungan_keluarga h JOIN orang_tua o ON o.nik_ortu=h.nik_ortu WHERE h.nik_anak=? ORDER BY o.nama_ortu", child);
  }

  @PostMapping("/caregiver/jadwal/simpan") public String saveSchedule(HttpSession s,
      @RequestParam String nikOrtu, @RequestParam Integer idObat, @RequestParam String dosis,
      @RequestParam String frekuensi, @RequestParam String aturanMinum, @RequestParam String jamJadwal,
      RedirectAttributes ra) {
    String d=deny(s,"CAREGIVER"); if(d!=null)return d;
    Integer allowed=db.queryForObject("SELECT count(*) FROM hubungan_keluarga WHERE nik_anak=? AND nik_ortu=?",Integer.class,nik(s),nikOrtu);
    if(allowed==null || allowed==0){ra.addFlashAttribute("error","Orang tua tersebut bukan anggota keluarga yang terhubung.");return "redirect:/caregiver/jadwal";}
    db.update("INSERT INTO jadwal_obat(nik_ortu,id_obat,dosis,frekuensi,aturan_minum,jam_jadwal) VALUES (?,?,?,?,?,?)",
      nikOrtu,idObat,dosis,frekuensi,aturanMinum,LocalTime.parse(jamJadwal));
    ra.addFlashAttribute("success","Jadwal obat berhasil ditambahkan.");
    return "redirect:/caregiver/jadwal";
  }

  @PostMapping("/caregiver/jadwal/{id}/hapus") public String deleteSchedule(HttpSession s,@PathVariable int id,RedirectAttributes ra) {
    String d=deny(s,"CAREGIVER"); if(d!=null)return d;
    db.update("""
      DELETE FROM jadwal_obat j USING hubungan_keluarga h
      WHERE j.id_jadwal=? AND h.nik_ortu=j.nik_ortu AND h.nik_anak=?
      """,id,nik(s));
    ra.addFlashAttribute("success","Jadwal dihapus jika termasuk keluarga yang terhubung.");
    return "redirect:/caregiver/jadwal";
  }

  @PostMapping("/orang-tua/konfirmasi/{id}") public String confirm(HttpSession s,@PathVariable int id,RedirectAttributes ra) {
    String d=deny(s,"ORANG_TUA"); if(d!=null)return d;
    List<Map<String,Object>> rows=db.queryForList("SELECT id_jadwal FROM jadwal_obat WHERE id_jadwal=? AND nik_ortu=?",id,nik(s));
    if(rows.isEmpty()){ra.addFlashAttribute("error","Jadwal tidak ditemukan.");return "redirect:/dashboard";}
    Integer exists=db.queryForObject("SELECT count(*) FROM riwayat_kepatuhan WHERE id_jadwal=? AND tgl_log=CURRENT_DATE AND status_kepatuhan='SELESAI'",Integer.class,id);
    if(exists==null || exists==0) {
      db.update("INSERT INTO riwayat_kepatuhan(id_jadwal,tgl_log,jam_konfirmasi,status_kepatuhan) VALUES (?,CURRENT_DATE,CURRENT_TIME,'SELESAI')",id);
    }
    ra.addFlashAttribute("success","Konfirmasi minum obat berhasil dicatat.");
    return "redirect:/dashboard";
  }

  @GetMapping("/riwayat") public String history(HttpSession s,Model m) {
    if(!logged(s))return "redirect:/login";
    if("ORANG_TUA".equals(role(s))) {
      m.addAttribute("rows",db.queryForList("""
        SELECT r.tgl_log,r.jam_konfirmasi,r.status_kepatuhan,ob.nama_obat,j.dosis
        FROM riwayat_kepatuhan r JOIN jadwal_obat j ON j.id_jadwal=r.id_jadwal
        JOIN master_obat ob ON ob.id_obat=j.id_obat
        WHERE j.nik_ortu=? ORDER BY r.tgl_log DESC,r.jam_konfirmasi DESC
        """,nik(s)));
    } else {
      m.addAttribute("rows",db.queryForList("""
        SELECT r.tgl_log,r.jam_konfirmasi,r.status_kepatuhan,ob.nama_obat,j.dosis,o.nama_ortu
        FROM riwayat_kepatuhan r JOIN jadwal_obat j ON j.id_jadwal=r.id_jadwal
        JOIN master_obat ob ON ob.id_obat=j.id_obat JOIN orang_tua o ON o.nik_ortu=j.nik_ortu
        JOIN hubungan_keluarga h ON h.nik_ortu=j.nik_ortu
        WHERE h.nik_anak=? ORDER BY r.tgl_log DESC,r.jam_konfirmasi DESC
        """,nik(s)));
    }
    m.addAttribute("role",role(s)); return "history";
  }

  @GetMapping("/caregiver/kontrol") public String controls(HttpSession s,Model m) {
    String d=deny(s,"CAREGIVER"); if(d!=null)return d;
    m.addAttribute("parents",connectedParents(nik(s)));
    m.addAttribute("facilities",db.queryForList("SELECT id_faskes,nama_faskes,tipe_faskes FROM master_faskes ORDER BY nama_faskes"));
    m.addAttribute("rows",db.queryForList("""
      SELECT k.id_kontrol,k.nik_ortu,o.nama_ortu,k.dokter_poli,k.tgl_kontrol,f.nama_faskes
      FROM jadwal_kontrol k JOIN orang_tua o ON o.nik_ortu=k.nik_ortu
      JOIN master_faskes f ON f.id_faskes=k.id_faskes
      JOIN hubungan_keluarga h ON h.nik_ortu=k.nik_ortu
      WHERE h.nik_anak=? ORDER BY k.tgl_kontrol
      """,nik(s)));
    return "caregiver-controls";
  }

  @PostMapping("/caregiver/kontrol/simpan") public String saveControl(HttpSession s,@RequestParam String nikOrtu,
      @RequestParam int idFaskes,@RequestParam String dokterPoli,@RequestParam String tglKontrol,RedirectAttributes ra) {
    String d=deny(s,"CAREGIVER"); if(d!=null)return d;
    Integer allowed=db.queryForObject("SELECT count(*) FROM hubungan_keluarga WHERE nik_anak=? AND nik_ortu=?",Integer.class,nik(s),nikOrtu);
    if(allowed==null || allowed==0){ra.addFlashAttribute("error","Orang tua tidak terhubung.");return "redirect:/caregiver/kontrol";}
    db.update("INSERT INTO jadwal_kontrol(nik_ortu,id_faskes,dokter_poli,tgl_kontrol) VALUES (?,?,?,?)",
      nikOrtu,idFaskes,dokterPoli,LocalDate.parse(tglKontrol));
    ra.addFlashAttribute("success","Jadwal kontrol berhasil disimpan.");
    return "redirect:/caregiver/kontrol";
  }

  @GetMapping("/profil") public String profile(HttpSession s,Model m) {
    if(!logged(s))return "redirect:/login";
    if("CAREGIVER".equals(role(s))) m.addAttribute("profile",db.queryForMap("SELECT nama_anak AS nama,no_telp_anak AS telepon,nik_anak AS nik FROM anak_caregiver WHERE nik_anak=?",nik(s)));
    else m.addAttribute("profile",db.queryForMap("SELECT nama_ortu AS nama,no_telp_ortu AS telepon,nik_ortu AS nik FROM orang_tua WHERE nik_ortu=?",nik(s)));
    m.addAttribute("role",role(s)); return "profile";
  }
}

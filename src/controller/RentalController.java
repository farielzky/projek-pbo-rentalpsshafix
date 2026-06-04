package controller;

import model.*;
import view.RentalView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RentalController {
    private RentalView view;

    private static class ActiveTimer {
        String idPs;
        Thread thread;
        public ActiveTimer(String idPs, Thread thread) {
            this.idPs = idPs;
            this.thread = thread;
        }
    }

    private final List<ActiveTimer> activeTimers = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService timerExecutor = Executors.newCachedThreadPool();

    // Generasi timer per PS — increment setiap sewa baru, invokeLater lama diabaikan jika generasi beda
    private final java.util.Map<String, Integer> timerGeneration = Collections.synchronizedMap(new java.util.HashMap<>());

    public RentalController(RentalView view) {
        this.view = view;
        // Set form disabled saat awal — belum ada row terpilih
        view.setMemberFormEditable(false);
        view.setPsFormEditable(false);
        resumeActiveTimers();
        refreshAllData();
        initListener();
    }

    // ===================== RESUME TIMER =====================

    private void resumeActiveTimers() {
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id_ps, waktu_mulai, durasi_jam FROM transaksi WHERE status = 'Aktif'");
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String idPs = rs.getString("id_ps");
                Timestamp waktuMulai = rs.getTimestamp("waktu_mulai");
                int durasiJam = rs.getInt("durasi_jam");

                long elapsedSeconds = (System.currentTimeMillis() - waktuMulai.getTime()) / 1000;
                int sisaDetik = (durasiJam * 3600) - (int) elapsedSeconds;

                if (sisaDetik > 0) {
                    mulaiTimerParallel(idPs, sisaDetik);
                } else {
                    updateStatusPS(idPs, "Tersedia");
                    selesaikanTransaksiBySystem(idPs);
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal memuat data sewa aktif dari database.\nDetail: " + ex.getMessage(),
                    "Error Koneksi", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ===================== REFRESH =====================

    private void refreshAllData() {
        loadDataPlaystation();
        loadDataMember();
        loadDataTransaksi();
        loadManageMemberTable();
        loadManagePSTable();
    }

    // ===================== LISTENERS =====================

    private void initListener() {
        view.getBtnSewa().addActionListener(e -> prosesSewa());
        view.getBtnSelesai().addActionListener(e -> prosesSelesai());

        view.getBtnMemberTambah().addActionListener(e -> tambahMember());
        view.getBtnMemberUbah().addActionListener(e -> ubahMember());
        view.getBtnMemberHapus().addActionListener(e -> hapusMember());

        view.getBtnPsTambah().addActionListener(e -> tambahPS());
        view.getBtnPsUbah().addActionListener(e -> ubahPS());
        view.getBtnPsHapus().addActionListener(e -> hapusPS());

        // Klik row Member → isi form + aktifkan Ubah/Hapus
        view.getTblManageMember().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = view.getTblManageMember().getSelectedRow();
                if (row != -1) {
                    view.getTxtMemberId().setText(view.getModManageMember().getValueAt(row, 0).toString());
                    view.getTxtMemberNama().setText(view.getModManageMember().getValueAt(row, 1).toString());
                    view.getCbMemberJenis().setSelectedItem(view.getModManageMember().getValueAt(row, 2).toString());
                    view.setMemberFormEditable(true);
                }
            }
        });

        // Klik row PS → isi form + aktifkan Ubah/Hapus
        view.getTblManagePS().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = view.getTblManagePS().getSelectedRow();
                if (row != -1) {
                    view.getTxtPsId().setText(view.getModManagePS().getValueAt(row, 0).toString());
                    view.getCbPsTipe().setSelectedItem(view.getModManagePS().getValueAt(row, 1).toString());
                    view.getTxtPsHarga().setText(view.getModManagePS().getValueAt(row, 2).toString());
                    view.setPsFormEditable(true);
                }
            }
        });
    }

    // ===================== TIMER HELPERS =====================

    private boolean isTimerRunning(String idPs) {
        synchronized (activeTimers) {
            for (ActiveTimer at : activeTimers) {
                if (at.idPs.equals(idPs)) return true;
            }
        }
        return false;
    }

    private void stopAndRemoveTimer(String idPs) {
        // Increment generasi dulu — semua invokeLater yang sudah di-queue langsung invalid
        timerGeneration.merge(idPs, 1, Integer::sum);
        synchronized (activeTimers) {
            for (int i = 0; i < activeTimers.size(); i++) {
                if (activeTimers.get(i).idPs.equals(idPs)) {
                    activeTimers.get(i).thread.interrupt();
                    activeTimers.remove(i);
                    break;
                }
            }
        }
    }

    private RentalView.CardPS getCardById(String idPs) {
        for (RentalView.CardPS card : view.getListCards()) {
            if (card.getIdPs().equals(idPs)) return card;
        }
        return null;
    }

    // ===================== LOAD DATA =====================

    private void loadDataPlaystation() {
        view.getPnlDashboardCards().removeAll();
        view.getListCards().clear();

        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM playstation");
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String idPs = rs.getString("id_ps");
                String tipe = rs.getString("tipe");
                String status = rs.getString("status");
                boolean isRunning = isTimerRunning(idPs);

                RentalView.CardPS card = view.new CardPS(idPs, tipe, status, isRunning);

                card.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        for (RentalView.CardPS c : view.getListCards()) c.setNormalBorder();
                        card.setSelectedBorder();
                        view.setSelectedPsId(idPs);
                        view.getLblSelectedPs().setText("Terpilih: " + idPs);
                    }
                });

                view.getListCards().add(card);
                view.getPnlDashboardCards().add(card);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal memuat data PlayStation.\nDetail: " + ex.getMessage(),
                    "Error Database", JOptionPane.ERROR_MESSAGE);
        }

        view.getPnlDashboardCards().revalidate();
        view.getPnlDashboardCards().repaint();
    }

    private void loadDataMember() {
        view.getCbMember().removeAllItems();
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM member");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                view.getCbMember().addItem(rs.getString("id_member") + " - " + rs.getString("nama"));
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal memuat data member.\nDetail: " + ex.getMessage(),
                    "Error Database", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadDataTransaksi() {
        view.getModTransaksi().setRowCount(0);
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT t.id_transaksi, m.nama AS nama_member, t.nama_guest, t.id_ps, " +
                     "t.waktu_mulai, t.durasi_jam, t.total_biaya, t.status " +
                     "FROM transaksi t LEFT JOIN member m ON t.id_member = m.id_member " +
                     "ORDER BY t.id_transaksi DESC");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String penyewa = rs.getString("nama_member") != null
                        ? rs.getString("nama_member")
                        : rs.getString("nama_guest");
                view.getModTransaksi().addRow(new Object[]{
                        rs.getInt("id_transaksi"), penyewa,
                        rs.getString("id_ps"), rs.getString("waktu_mulai"),
                        rs.getInt("durasi_jam"), rs.getDouble("total_biaya"),
                        rs.getString("status")
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal memuat riwayat transaksi.\nDetail: " + ex.getMessage(),
                    "Error Database", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadManageMemberTable() {
        view.getModManageMember().setRowCount(0);
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM member");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                view.getModManageMember().addRow(new Object[]{
                        rs.getString("id_member"), rs.getString("nama"), rs.getString("jenis")
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal memuat tabel member.\nDetail: " + ex.getMessage(),
                    "Error Database", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadManagePSTable() {
        view.getModManagePS().setRowCount(0);
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM playstation");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                view.getModManagePS().addRow(new Object[]{
                        rs.getString("id_ps"), rs.getString("tipe"),
                        rs.getDouble("harga_per_jam"), rs.getString("status")
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal memuat tabel PlayStation.\nDetail: " + ex.getMessage(),
                    "Error Database", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ===================== ID GENERATORS =====================

    private String generateMemberId() {
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT MAX(CAST(SUBSTRING(id_member, 2) AS UNSIGNED)) AS max_id FROM member");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int maxId = rs.getInt("max_id");
                return String.format("M%02d", maxId + 1);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal generate ID Member.\nDetail: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
        return "M01";
    }

    private String generatePsId(String tipe) {
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT MAX(CAST(SUBSTRING(id_ps, LENGTH(?) + 2) AS UNSIGNED)) AS max_id FROM playstation WHERE tipe = ?")) {
            stmt.setString(1, tipe);
            stmt.setString(2, tipe);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int maxId = rs.getInt("max_id");
                return String.format("%s-%02d", tipe, maxId + 1);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal generate ID PS.\nDetail: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
        return tipe + "-01";
    }

    // ===================== CRUD MEMBER =====================

    private void tambahMember() {
        String nama = view.getTxtMemberNama().getText().trim();

        // Validasi input kosong
        if (nama.isEmpty()) {
            JOptionPane.showMessageDialog(view,
                    "Nama member tidak boleh kosong!",
                    "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (nama.length() < 3) {
            JOptionPane.showMessageDialog(view,
                    "Nama member minimal 3 karakter!",
                    "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String idBaru = generateMemberId();
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO member VALUES (?, ?, ?)")) {
            stmt.setString(1, idBaru);
            stmt.setString(2, nama);
            stmt.setString(3, view.getCbMemberJenis().getSelectedItem().toString());
            stmt.executeUpdate();
            JOptionPane.showMessageDialog(view,
                    "Member berhasil ditambahkan dengan ID: " + idBaru,
                    "Sukses", JOptionPane.INFORMATION_MESSAGE);
            refreshAllData();
            clearMemberForm();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal menambah member.\nDetail: " + ex.getMessage(),
                    "Error Database", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void ubahMember() {
        // Cek apakah row sudah dipilih
        if (view.getTxtMemberId().getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(view,
                    "Pilih member dari tabel terlebih dahulu!",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String nama = view.getTxtMemberNama().getText().trim();
        if (nama.isEmpty()) {
            JOptionPane.showMessageDialog(view,
                    "Nama member tidak boleh kosong!",
                    "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (nama.length() < 3) {
            JOptionPane.showMessageDialog(view,
                    "Nama member minimal 3 karakter!",
                    "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE member SET nama = ?, jenis = ? WHERE id_member = ?")) {
            stmt.setString(1, nama);
            stmt.setString(2, view.getCbMemberJenis().getSelectedItem().toString());
            stmt.setString(3, view.getTxtMemberId().getText());
            stmt.executeUpdate();
            JOptionPane.showMessageDialog(view, "Data member berhasil diubah.", "Sukses", JOptionPane.INFORMATION_MESSAGE);
            refreshAllData();
            clearMemberForm();
            view.setMemberFormEditable(false);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal mengubah data member.\nDetail: " + ex.getMessage(),
                    "Error Database", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void hapusMember() {
        // Cek apakah row sudah dipilih
        if (view.getTxtMemberId().getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(view,
                    "Pilih member dari tabel terlebih dahulu!",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String idMember = view.getTxtMemberId().getText();
        String namaMember = view.getTxtMemberNama().getText();

        int konfirmasi = JOptionPane.showConfirmDialog(view,
                "Yakin ingin menghapus member \"" + namaMember + "\"?",
                "Konfirmasi Hapus", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (konfirmasi != JOptionPane.YES_OPTION) return;

        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM member WHERE id_member = ?")) {
            stmt.setString(1, idMember);
            stmt.executeUpdate();
            JOptionPane.showMessageDialog(view, "Member berhasil dihapus.", "Sukses", JOptionPane.INFORMATION_MESSAGE);
            refreshAllData();
            clearMemberForm();
            view.setMemberFormEditable(false);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal menghapus member.\nMember mungkin masih terikat dengan transaksi aktif.",
                    "Error Database", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ===================== CRUD PS =====================

    private void tambahPS() {
        String hargaStr = view.getTxtPsHarga().getText().trim();

        // Validasi input harga
        if (hargaStr.isEmpty()) {
            JOptionPane.showMessageDialog(view,
                    "Harga per jam tidak boleh kosong!",
                    "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
            return;
        }
        double harga;
        try {
            harga = Double.parseDouble(hargaStr);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(view,
                    "Harga per jam harus berupa angka!",
                    "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (harga <= 0) {
            JOptionPane.showMessageDialog(view,
                    "Harga per jam harus lebih dari 0!",
                    "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String tipe = view.getCbPsTipe().getSelectedItem().toString();
        String idBaru = generatePsId(tipe);
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO playstation VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, idBaru);
            stmt.setString(2, tipe);
            stmt.setDouble(3, harga);
            stmt.setString(4, "Tersedia");
            stmt.executeUpdate();
            JOptionPane.showMessageDialog(view,
                    "PlayStation berhasil ditambahkan dengan ID: " + idBaru,
                    "Sukses", JOptionPane.INFORMATION_MESSAGE);
            refreshAllData();
            clearPsForm();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal menambah PlayStation.\nDetail: " + ex.getMessage(),
                    "Error Database", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void ubahPS() {
        // Cek apakah row sudah dipilih
        if (view.getTxtPsId().getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(view,
                    "Pilih PlayStation dari tabel terlebih dahulu!",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Cek apakah PS sedang disewa
        String idPs = view.getTxtPsId().getText().trim();
        if (isPsSedangDisewa(idPs)) {
            JOptionPane.showMessageDialog(view,
                    "PS ini sedang disewa, tidak bisa diubah!",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String hargaStr = view.getTxtPsHarga().getText().trim();
        if (hargaStr.isEmpty()) {
            JOptionPane.showMessageDialog(view,
                    "Harga per jam tidak boleh kosong!",
                    "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
            return;
        }
        double harga;
        try {
            harga = Double.parseDouble(hargaStr);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(view,
                    "Harga per jam harus berupa angka!",
                    "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (harga <= 0) {
            JOptionPane.showMessageDialog(view,
                    "Harga per jam harus lebih dari 0!",
                    "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE playstation SET tipe = ?, harga_per_jam = ? WHERE id_ps = ?")) {
            stmt.setString(1, view.getCbPsTipe().getSelectedItem().toString());
            stmt.setDouble(2, harga);
            stmt.setString(3, view.getTxtPsId().getText());
            stmt.executeUpdate();
            JOptionPane.showMessageDialog(view, "Data PlayStation berhasil diubah.", "Sukses", JOptionPane.INFORMATION_MESSAGE);
            refreshAllData();
            clearPsForm();
            view.setPsFormEditable(false);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal mengubah data PlayStation.\nDetail: " + ex.getMessage(),
                    "Error Database", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void hapusPS() {
        // Cek apakah row sudah dipilih
        if (view.getTxtPsId().getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(view,
                    "Pilih PlayStation dari tabel terlebih dahulu!",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Cek apakah PS sedang disewa
        String idPs = view.getTxtPsId().getText().trim();
        if (isPsSedangDisewa(idPs)) {
            JOptionPane.showMessageDialog(view,
                    "PS ini sedang disewa, tidak bisa dihapus!",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        //String idPs = view.getTxtPsId().getText();

        int konfirmasi = JOptionPane.showConfirmDialog(view,
                "Yakin ingin menghapus PlayStation \"" + idPs + "\"?",
                "Konfirmasi Hapus", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (konfirmasi != JOptionPane.YES_OPTION) return;

        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM playstation WHERE id_ps = ?")) {
            stmt.setString(1, idPs);
            stmt.executeUpdate();
            JOptionPane.showMessageDialog(view, "PlayStation berhasil dihapus.", "Sukses", JOptionPane.INFORMATION_MESSAGE);
            refreshAllData();
            clearPsForm();
            view.setPsFormEditable(false);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal menghapus PlayStation.\nUnit mungkin masih terikat dengan transaksi aktif.",
                    "Error Database", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ===================== CLEAR FORM =====================

    private void clearMemberForm() {
        view.getTxtMemberId().setText("");
        view.getTxtMemberNama().setText("");
    }

    private boolean isPsSedangDisewa(String idPs) {
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT status FROM playstation WHERE id_ps = ?")) {
            stmt.setString(1, idPs);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return "Disewa".equals(rs.getString("status"));
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal memeriksa status PlayStation.\nDetail: " + ex.getMessage(),
                    "Error Database", JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    private void clearPsForm() {
        view.getTxtPsId().setText("");
        view.getTxtPsHarga().setText("");
    }

    // ===================== PROSES SEWA =====================

    private void prosesSewa() {
        String idPs = view.getSelectedPsId();

        if (idPs == null) {
            JOptionPane.showMessageDialog(view,
                    "Klik salah satu kartu PS terlebih dahulu!",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        RentalView.CardPS card = getCardById(idPs);
        if (card == null) return;

        if (card.getLblStatus().getText().equals("Disewa")) {
            JOptionPane.showMessageDialog(view,
                    "PS ini sedang digunakan, pilih unit lain!",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Validasi durasi
        String durasiStr = view.getTxtDurasi().getText().trim();
        if (durasiStr.isEmpty()) {
            JOptionPane.showMessageDialog(view,
                    "Durasi sewa tidak boleh kosong!",
                    "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int durasi;
        try {
            durasi = Integer.parseInt(durasiStr);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(view,
                    "Durasi harus berupa angka bulat (jam)!",
                    "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (durasi <= 0) {
            JOptionPane.showMessageDialog(view,
                    "Durasi harus lebih dari 0 jam!",
                    "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (durasi > 24) {
            JOptionPane.showMessageDialog(view,
                    "Durasi maksimal sewa adalah 24 jam!",
                    "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String idMember = null;
        String namaGuest = null;

        if (view.getRbMember().isSelected()) {
            if (view.getCbMember().getSelectedItem() == null) {
                JOptionPane.showMessageDialog(view,
                        "Tidak ada member terdaftar. Tambah member terlebih dahulu!",
                        "Peringatan", JOptionPane.WARNING_MESSAGE);
                return;
            }
            idMember = view.getCbMember().getSelectedItem().toString().split(" - ")[0];
        } else {
            namaGuest = view.getTxtNamaGuest().getText().trim();
            if (namaGuest.isEmpty()) {
                JOptionPane.showMessageDialog(view,
                        "Nama guest tidak boleh kosong!",
                        "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (namaGuest.length() < 3) {
                JOptionPane.showMessageDialog(view,
                        "Nama guest minimal 3 karakter!",
                        "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        try {
            catatTransaksiBaru(idMember, namaGuest, idPs, durasi);
            updateStatusPS(idPs, "Disewa");

            card.getLblStatus().setText("Disewa");
            card.getLblIndicator().setForeground(new Color(239, 68, 68));

            mulaiTimerParallel(idPs, durasi * 3600);
            loadDataTransaksi();
            loadManagePSTable();

            view.getTxtDurasi().setText("");
            view.getTxtNamaGuest().setText("");
            JOptionPane.showMessageDialog(view,
                    "Sewa " + idPs + " berhasil dimulai selama " + durasi + " jam.",
                    "Sukses", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal memproses sewa.\nDetail: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void prosesSelesai() {
        String idPs = view.getSelectedPsId();

        if (idPs == null) {
            JOptionPane.showMessageDialog(view,
                    "Klik kartu PS yang ingin diselesaikan!",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        RentalView.CardPS card = getCardById(idPs);
        if (card == null) return;

        if (card.getLblStatus().getText().equals("Tersedia")) {
            JOptionPane.showMessageDialog(view,
                    "PS ini tidak sedang disewa.",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int konfirmasi = JOptionPane.showConfirmDialog(view,
                "Yakin ingin menyelesaikan sewa " + idPs + " sekarang?",
                "Konfirmasi", JOptionPane.YES_NO_OPTION);
        if (konfirmasi != JOptionPane.YES_OPTION) return;

        stopAndRemoveTimer(idPs);
        updateStatusPS(idPs, "Tersedia");
        selesaikanTransaksiBySystem(idPs);

        card.getLblStatus().setText("Tersedia");
        card.getLblIndicator().setForeground(new Color(16, 185, 129));
        card.getLblTimer().setText("00:00:00");

        loadDataPlaystation();
        loadDataTransaksi();
        loadManagePSTable();
        JOptionPane.showMessageDialog(view,
                "Sewa untuk " + idPs + " berhasil diselesaikan.",
                "Sukses", JOptionPane.INFORMATION_MESSAGE);
    }

    // ===================== TIMER =====================

    private void mulaiTimerParallel(String idPs, int sisaDetikAwal) {
        // Increment generasi untuk PS ini — semua invokeLater dari generasi sebelumnya akan diabaikan
        int gen = timerGeneration.merge(idPs, 1, Integer::sum);

        Runnable timerTask = () -> {
            int sisaDetik = sisaDetikAwal;
            boolean selesaiNatural = false;

            while (sisaDetik > 0 && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000);
                    sisaDetik--;
                    int h = sisaDetik / 3600;
                    int m = (sisaDetik % 3600) / 60;
                    int s = sisaDetik % 60;
                    String formatWaktu = String.format("%02d:%02d:%02d", h, m, s);

                    SwingUtilities.invokeLater(() -> {
                        // Abaikan jika timer sudah dihentikan atau generasi sudah beda (sewa baru)
                        if (timerGeneration.getOrDefault(idPs, 0) != gen) return;
                        RentalView.CardPS card = getCardById(idPs);
                        if (card != null) card.getLblTimer().setText(formatWaktu);
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            if (!Thread.currentThread().isInterrupted()) {
                selesaiNatural = true;
            }

            if (selesaiNatural) {
                SwingUtilities.invokeLater(() -> {
                    if (timerGeneration.getOrDefault(idPs, 0) != gen) return;
                    updateStatusPS(idPs, "Tersedia");
                    selesaikanTransaksiBySystem(idPs);

                    RentalView.CardPS card = getCardById(idPs);
                    if (card != null) {
                        card.getLblStatus().setText("Tersedia");
                        card.getLblIndicator().setForeground(new Color(16, 185, 129));
                        card.getLblTimer().setText("00:00:00");
                    }

                    loadDataPlaystation();
                    loadDataTransaksi();
                    loadManagePSTable();
                    JOptionPane.showMessageDialog(view, "Waktu untuk " + idPs + " telah habis!");
                });
                stopAndRemoveTimer(idPs);
            }
        };

        Thread timerThread = new Thread(timerTask);
        synchronized (activeTimers) {
            activeTimers.add(new ActiveTimer(idPs, timerThread));
        }
        timerExecutor.submit(timerThread);
    }

    // ===================== DB HELPERS =====================

    private void catatTransaksiBaru(String idMember, String namaGuest, String idPs, int durasi) throws SQLException {
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO transaksi (id_member, nama_guest, id_ps, waktu_mulai, durasi_jam, total_biaya, status) " +
                     "VALUES (?, ?, ?, NOW(), ?, 0, 'Aktif')")) {
            if (idMember != null) {
                stmt.setString(1, idMember);
            } else {
                stmt.setNull(1, Types.VARCHAR);
            }
            stmt.setString(2, namaGuest);
            stmt.setString(3, idPs);
            stmt.setInt(4, durasi);
            stmt.executeUpdate();
        }
    }

    private void updateStatusPS(String idPs, String status) {
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE playstation SET status = ? WHERE id_ps = ?")) {
            stmt.setString(1, status);
            stmt.setString(2, idPs);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal update status PlayStation.\nDetail: " + ex.getMessage(),
                    "Error Database", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selesaikanTransaksiBySystem(String idPs) {
        try (Connection conn = Database.connect()) {
            PreparedStatement stmtTrans = conn.prepareStatement(
                    "SELECT * FROM transaksi WHERE id_ps = ? AND status = 'Aktif'");
            stmtTrans.setString(1, idPs);
            ResultSet rsTrans = stmtTrans.executeQuery();

            if (rsTrans.next()) {
                int idTransaksi = rsTrans.getInt("id_transaksi");
                String idMember = rsTrans.getString("id_member");
                String namaGuest = rsTrans.getString("nama_guest");
                int durasi = rsTrans.getInt("durasi_jam");

                Member penyewa;
                if (idMember != null) {
                    PreparedStatement stmtMember = conn.prepareStatement(
                            "SELECT jenis, nama FROM member WHERE id_member = ?");
                    stmtMember.setString(1, idMember);
                    ResultSet rsMember = stmtMember.executeQuery();
                    String jenisMember = "Regular";
                    String namaMember = "Unknown";
                    if (rsMember.next()) {
                        jenisMember = rsMember.getString("jenis");
                        namaMember = rsMember.getString("nama");
                    }
                    penyewa = jenisMember.equals("VIP")
                            ? new VIPMember(idMember, namaMember)
                            : new RegularMember(idMember, namaMember);
                } else {
                    penyewa = new Guest(namaGuest);
                }

                PreparedStatement stmtPS = conn.prepareStatement(
                        "SELECT tipe, harga_per_jam FROM playstation WHERE id_ps = ?");
                stmtPS.setString(1, idPs);
                ResultSet rsPS = stmtPS.executeQuery();

                double totalAkhir = 0;
                if (rsPS.next()) {
                    String tipePS = rsPS.getString("tipe");
                    double hargaPerJam = rsPS.getDouble("harga_per_jam");
                    Konsol konsol = tipePS.equals("PS5")
                            ? new PlayStation5(idPs, hargaPerJam, "Tersedia")
                            : new PlayStation4(idPs, hargaPerJam, "Tersedia");
                    double hargaAwal = konsol.hitungBiaya(durasi);
                    double diskon = penyewa.hitungDiskon(hargaAwal);
                    totalAkhir = hargaAwal - diskon;
                }

                PreparedStatement stmtUpdate = conn.prepareStatement(
                        "UPDATE transaksi SET total_biaya = ?, status = 'Selesai' WHERE id_transaksi = ?");
                stmtUpdate.setDouble(1, totalAkhir);
                stmtUpdate.setInt(2, idTransaksi);
                stmtUpdate.executeUpdate();
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(view,
                    "Gagal menyelesaikan transaksi.\nDetail: " + ex.getMessage(),
                    "Error Database", JOptionPane.ERROR_MESSAGE);
        }
    }
}
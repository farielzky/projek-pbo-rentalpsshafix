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

    // Fix: pakai synchronizedList agar aman dari ConcurrentModificationException
    private final List<ActiveTimer> activeTimers = Collections.synchronizedList(new ArrayList<>());

    // ExecutorService sebagai thread pool manager
    private final ExecutorService timerExecutor = Executors.newCachedThreadPool();

    public RentalController(RentalView view) {
        this.view = view;
        resumeActiveTimers();
        refreshAllData();
        initListener();
    }

    private void resumeActiveTimers() {
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("SELECT id_ps, waktu_mulai, durasi_jam FROM transaksi WHERE status = 'Aktif'");
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
        } catch (SQLException ignored) {}
    }

    private void refreshAllData() {
        loadDataPlaystation();
        loadDataMember();
        loadDataTransaksi();
        loadManageMemberTable();
        loadManagePSTable();
    }

    private void initListener() {
        view.getBtnSewa().addActionListener(e -> prosesSewa());
        view.getBtnSelesai().addActionListener(e -> prosesSelesai());
        
        view.getBtnMemberTambah().addActionListener(e -> tambahMember());
        view.getBtnMemberUbah().addActionListener(e -> ubahMember());
        view.getBtnMemberHapus().addActionListener(e -> hapusMember());
        
        view.getBtnPsTambah().addActionListener(e -> tambahPS());
        view.getBtnPsUbah().addActionListener(e -> ubahPS());
        view.getBtnPsHapus().addActionListener(e -> hapusPS());

        view.getTblManageMember().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = view.getTblManageMember().getSelectedRow();
                if (row != -1) {
                    view.getTxtMemberId().setText(view.getModManageMember().getValueAt(row, 0).toString());
                    view.getTxtMemberNama().setText(view.getModManageMember().getValueAt(row, 1).toString());
                    view.getCbMemberJenis().setSelectedItem(view.getModManageMember().getValueAt(row, 2).toString());
                }
            }
        });

        view.getTblManagePS().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = view.getTblManagePS().getSelectedRow();
                if (row != -1) {
                    view.getTxtPsId().setText(view.getModManagePS().getValueAt(row, 0).toString());
                    view.getCbPsTipe().setSelectedItem(view.getModManagePS().getValueAt(row, 1).toString());
                    view.getTxtPsHarga().setText(view.getModManagePS().getValueAt(row, 2).toString());
                }
            }
        });
    }

    private boolean isTimerRunning(String idPs) {
        synchronized (activeTimers) {
            for (ActiveTimer at : activeTimers) {
                if (at.idPs.equals(idPs)) return true;
            }
        }
        return false;
    }

    private void stopAndRemoveTimer(String idPs) {
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
        } catch (SQLException ignored) {}

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
        } catch (SQLException ignored) {}
    }

    private void loadDataTransaksi() {
        view.getModTransaksi().setRowCount(0);
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("SELECT t.id_transaksi, m.nama AS nama_member, t.nama_guest, t.id_ps, t.waktu_mulai, t.durasi_jam, t.total_biaya, t.status FROM transaksi t LEFT JOIN member m ON t.id_member = m.id_member ORDER BY t.id_transaksi DESC");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String penyewa = rs.getString("nama_member") != null ? rs.getString("nama_member") : rs.getString("nama_guest");
                view.getModTransaksi().addRow(new Object[]{
                        rs.getInt("id_transaksi"), penyewa,
                        rs.getString("id_ps"), rs.getString("waktu_mulai"), rs.getInt("durasi_jam"),
                        rs.getDouble("total_biaya"), rs.getString("status")
                });
            }
        } catch (SQLException ignored) {}
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
        } catch (SQLException ignored) {}
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
        } catch (SQLException ignored) {}
    }

    private String generateMemberId() {
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT MAX(CAST(SUBSTRING(id_member, 2) AS UNSIGNED)) AS max_id FROM member");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int maxId = rs.getInt("max_id");
                return String.format("M%02d", maxId + 1);
            }
        } catch (SQLException ignored) {}
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
        } catch (SQLException ignored) {}
        return tipe + "-01";
    }

    private void tambahMember() {
        String idBaru = generateMemberId();
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO member VALUES (?, ?, ?)")) {
            stmt.setString(1, idBaru);
            stmt.setString(2, view.getTxtMemberNama().getText());
            stmt.setString(3, view.getCbMemberJenis().getSelectedItem().toString());
            stmt.executeUpdate();
            refreshAllData();
            clearMemberForm();
        } catch (SQLException ex) { JOptionPane.showMessageDialog(view, "Gagal Tambah Member!"); }
    }

    private void ubahMember() {
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("UPDATE member SET nama = ?, jenis = ? WHERE id_member = ?")) {
            stmt.setString(1, view.getTxtMemberNama().getText());
            stmt.setString(2, view.getCbMemberJenis().getSelectedItem().toString());
            stmt.setString(3, view.getTxtMemberId().getText());
            stmt.executeUpdate();
            refreshAllData();
            clearMemberForm();
        } catch (SQLException ex) { JOptionPane.showMessageDialog(view, "Gagal Ubah Member!"); }
    }

    private void hapusMember() {
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM member WHERE id_member = ?")) {
            stmt.setString(1, view.getTxtMemberId().getText());
            stmt.executeUpdate();
            refreshAllData();
            clearMemberForm();
        } catch (SQLException ex) { JOptionPane.showMessageDialog(view, "Gagal Hapus Member! (Masih terikat transaksi)"); }
    }

    private void tambahPS() {
        String tipe = view.getCbPsTipe().getSelectedItem().toString();
        String idBaru = generatePsId(tipe);
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO playstation VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, idBaru);
            stmt.setString(2, tipe);
            stmt.setDouble(3, Double.parseDouble(view.getTxtPsHarga().getText()));
            stmt.setString(4, "Tersedia");
            stmt.executeUpdate();
            refreshAllData();
            clearPsForm();
        } catch (Exception ex) { JOptionPane.showMessageDialog(view, "Gagal Tambah PS!"); }
    }

    private void ubahPS() {
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("UPDATE playstation SET tipe = ?, harga_per_jam = ? WHERE id_ps = ?")) {
            stmt.setString(1, view.getCbPsTipe().getSelectedItem().toString());
            stmt.setDouble(2, Double.parseDouble(view.getTxtPsHarga().getText()));
            stmt.setString(3, view.getTxtPsId().getText());
            stmt.executeUpdate();
            refreshAllData();
            clearPsForm();
        } catch (Exception ex) { JOptionPane.showMessageDialog(view, "Gagal Ubah PS!"); }
    }

    private void hapusPS() {
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM playstation WHERE id_ps = ?")) {
            stmt.setString(1, view.getTxtPsId().getText());
            stmt.executeUpdate();
            refreshAllData();
            clearPsForm();
        } catch (SQLException ex) { JOptionPane.showMessageDialog(view, "Gagal Hapus PS! (Masih terikat transaksi)"); }
    }

    private void clearMemberForm() {
        view.getTxtMemberId().setText("");
        view.getTxtMemberNama().setText("");
    }

    private void clearPsForm() {
        view.getTxtPsId().setText("");
        view.getTxtPsHarga().setText("");
    }

    private void prosesSewa() {
        String idPs = view.getSelectedPsId();

        if (idPs == null) {
            JOptionPane.showMessageDialog(view, "Klik salah satu kartu PS terlebih dahulu!", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        RentalView.CardPS card = getCardById(idPs);
        if (card.getLblStatus().getText().equals("Disewa")) {
            JOptionPane.showMessageDialog(view, "PS sedang digunakan!");
            return;
        }

        try {
            int durasi = Integer.parseInt(view.getTxtDurasi().getText());
            if (durasi <= 0) throw new NumberFormatException();

            String idMember = null;
            String namaGuest = null;

            if (view.getRbMember().isSelected()) {
                if (view.getCbMember().getSelectedItem() == null) {
                    JOptionPane.showMessageDialog(view, "Data member kosong!");
                    return;
                }
                idMember = view.getCbMember().getSelectedItem().toString().split(" - ")[0];
            } else {
                namaGuest = view.getTxtNamaGuest().getText();
                if (namaGuest.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(view, "Nama Guest tidak boleh kosong!");
                    return;
                }
            }

            catatTransaksiBaru(idMember, namaGuest, idPs, durasi);
            updateStatusPS(idPs, "Disewa");
            
            card.getLblStatus().setText("Disewa");
            card.getLblIndicator().setForeground(new Color(239, 68, 68));
            
            mulaiTimerParallel(idPs, durasi * 3600);
            loadDataTransaksi();
            loadManagePSTable(); 

            view.getTxtDurasi().setText("");
            view.getTxtNamaGuest().setText("");

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(view, "Durasi tidak valid!");
        }
    }

    private void prosesSelesai() {
        String idPs = view.getSelectedPsId();

        if (idPs == null) {
            JOptionPane.showMessageDialog(view, "Klik kartu PS yang ingin diselesaikan!", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        RentalView.CardPS card = getCardById(idPs);
        if (card.getLblStatus().getText().equals("Tersedia")) {
            JOptionPane.showMessageDialog(view, "PS ini tidak sedang disewa.");
            return;
        }

        stopAndRemoveTimer(idPs);
        updateStatusPS(idPs, "Tersedia");
        selesaikanTransaksiBySystem(idPs);
        
        card.getLblStatus().setText("Tersedia");
        card.getLblIndicator().setForeground(new Color(16, 185, 129));
        card.getLblTimer().setText("00:00:00");
        
        loadDataTransaksi();
        loadManagePSTable();
        JOptionPane.showMessageDialog(view, "Sewa untuk " + idPs + " berhasil diselesaikan.");
    }

    private void mulaiTimerParallel(String idPs, int sisaDetikAwal) {
        Runnable timerTask = () -> {
            int sisaDetik = sisaDetikAwal;
            while (sisaDetik > 0 && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000);
                    sisaDetik--;
                    int h = sisaDetik / 3600;
                    int m = (sisaDetik % 3600) / 60;
                    int s = sisaDetik % 60;
                    String formatWaktu = String.format("%02d:%02d:%02d", h, m, s);

                    SwingUtilities.invokeLater(() -> {
                        RentalView.CardPS card = getCardById(idPs);
                        if (card != null) card.getLblTimer().setText(formatWaktu);
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return; // keluar bersih jika diinterrupt
                }
            }
            if (!Thread.currentThread().isInterrupted()) {
                // Waktu habis secara natural
                SwingUtilities.invokeLater(() -> {
                    updateStatusPS(idPs, "Tersedia");
                    selesaikanTransaksiBySystem(idPs);
                    
                    RentalView.CardPS card = getCardById(idPs);
                    if (card != null) {
                        card.getLblStatus().setText("Tersedia");
                        card.getLblIndicator().setForeground(new Color(16, 185, 129));
                        card.getLblTimer().setText("00:00:00");
                    }
                    
                    loadDataTransaksi();
                    loadManagePSTable();
                    JOptionPane.showMessageDialog(view, "Waktu untuk " + idPs + " telah habis!");
                });
                stopAndRemoveTimer(idPs);
            }
        };

        // Jalankan via ExecutorService, simpan referensi thread untuk keperluan interrupt
        Thread timerThread = new Thread(timerTask);
        synchronized (activeTimers) {
            activeTimers.add(new ActiveTimer(idPs, timerThread));
        }
        timerExecutor.submit(timerThread);
    }

    private void catatTransaksiBaru(String idMember, String namaGuest, String idPs, int durasi) {
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO transaksi (id_member, nama_guest, id_ps, waktu_mulai, durasi_jam, total_biaya, status) VALUES (?, ?, ?, NOW(), ?, 0, 'Aktif')")) {
            
            if (idMember != null) {
                stmt.setString(1, idMember);
            } else {
                stmt.setNull(1, Types.VARCHAR);
            }
            
            stmt.setString(2, namaGuest);
            stmt.setString(3, idPs);
            stmt.setInt(4, durasi);
            stmt.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private void updateStatusPS(String idPs, String status) {
        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement("UPDATE playstation SET status = ? WHERE id_ps = ?")) {
            stmt.setString(1, status);
            stmt.setString(2, idPs);
            stmt.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private void selesaikanTransaksiBySystem(String idPs) {
        try (Connection conn = Database.connect()) {
            String queryTransaksi = "SELECT * FROM transaksi WHERE id_ps = ? AND status = 'Aktif'";
            PreparedStatement stmtTrans = conn.prepareStatement(queryTransaksi);
            stmtTrans.setString(1, idPs);
            ResultSet rsTrans = stmtTrans.executeQuery();

            if (rsTrans.next()) {
                int idTransaksi = rsTrans.getInt("id_transaksi");
                String idMember = rsTrans.getString("id_member");
                String namaGuest = rsTrans.getString("nama_guest");
                int durasi = rsTrans.getInt("durasi_jam");

                Member penyewa;

                if (idMember != null) {
                    String queryMember = "SELECT jenis, nama FROM member WHERE id_member = ?";
                    PreparedStatement stmtMember = conn.prepareStatement(queryMember);
                    stmtMember.setString(1, idMember);
                    ResultSet rsMember = stmtMember.executeQuery();
                    
                    String jenisMember = "Regular"; 
                    String namaMember = "Unknown";
                    if (rsMember.next()) {
                        jenisMember = rsMember.getString("jenis");
                        namaMember = rsMember.getString("nama");
                    }
                    penyewa = jenisMember.equals("VIP") ? new VIPMember(idMember, namaMember) : new RegularMember(idMember, namaMember);
                } else {
                    penyewa = new Guest(namaGuest);
                }

                String queryPS = "SELECT tipe, harga_per_jam FROM playstation WHERE id_ps = ?";
                PreparedStatement stmtPS = conn.prepareStatement(queryPS);
                stmtPS.setString(1, idPs);
                ResultSet rsPS = stmtPS.executeQuery();
                
                double totalAkhir = 0;
                if (rsPS.next()) {
                    String tipePS = rsPS.getString("tipe");
                    double hargaPerJam = rsPS.getDouble("harga_per_jam");

                    Konsol konsol = tipePS.equals("PS5") ? new PlayStation5(idPs, hargaPerJam, "Tersedia") : new PlayStation4(idPs, hargaPerJam, "Tersedia");
                    double hargaAwal = konsol.hitungBiaya(durasi);
                    double diskon = penyewa.hitungDiskon(hargaAwal);
                    totalAkhir = hargaAwal - diskon;
                }

                String updateTrans = "UPDATE transaksi SET total_biaya = ?, status = 'Selesai' WHERE id_transaksi = ?";
                PreparedStatement stmtUpdate = conn.prepareStatement(updateTrans);
                stmtUpdate.setDouble(1, totalAkhir);
                stmtUpdate.setInt(2, idTransaksi);
                stmtUpdate.executeUpdate();
            }
        } catch (SQLException ignored) {}
    }
}
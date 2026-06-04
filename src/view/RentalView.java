package view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class RentalView extends JFrame {
    private JTable tblTransaksi, tblManageMember, tblManagePS;
    private DefaultTableModel modTransaksi, modManageMember, modManagePS;
    
    private JPanel pnlDashboardCards;
    private JLabel lblSelectedPs;
    private String selectedPsId = null;

    private JRadioButton rbMember, rbGuest;
    private ButtonGroup bgTipePenyewa;
    private JComboBox<String> cbMember, cbMemberJenis, cbPsTipe;
    private JTextField txtDurasi, txtMemberId, txtMemberNama, txtPsId, txtPsHarga, txtNamaGuest;
    // txtMemberId dan txtPsId dipertahankan sebagai hidden field untuk menyimpan ID saat ubah/hapus
    private JButton btnSewa, btnSelesai;
    private JButton btnMemberTambah, btnMemberUbah, btnMemberHapus;
    private JButton btnPsTambah, btnPsUbah, btnPsHapus;

    private final Color COLOR_BG = new Color(245, 247, 250);
    private final Color COLOR_CARD = Color.WHITE;
    private final Color COLOR_PRIMARY = new Color(59, 130, 246);
    private final Color COLOR_SUCCESS = new Color(16, 185, 129);
    private final Color COLOR_DANGER = new Color(239, 68, 68);
    private final Color COLOR_TEXT = new Color(31, 41, 55);
    private final Font FONT_MAIN = new Font("SansSerif", Font.PLAIN, 13);
    private final Font FONT_BOLD = new Font("SansSerif", Font.BOLD, 13);

    private List<CardPS> listCards = new ArrayList<>();

    public class CardPS extends JPanel {
        private String idPs;
        private JLabel lblIndicator;
        private JLabel lblStatus;
        private JLabel lblTimer;

        public CardPS(String idPs, String tipe, String status, boolean isRunning) {
            this.idPs = idPs;
            setLayout(new BorderLayout());
            setPreferredSize(new Dimension(180, 120));
            setBackground(COLOR_CARD);
            setNormalBorder();
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            JPanel topPanel = new JPanel(new BorderLayout());
            topPanel.setOpaque(false);
            JLabel lblId = new JLabel(idPs + " (" + tipe + ")");
            lblId.setFont(new Font("SansSerif", Font.BOLD, 14));
            
            lblIndicator = new JLabel("⬤");
            lblIndicator.setFont(new Font("SansSerif", Font.PLAIN, 16));
            lblIndicator.setForeground(status.equals("Tersedia") ? COLOR_SUCCESS : COLOR_DANGER);
            
            topPanel.add(lblId, BorderLayout.WEST);
            topPanel.add(lblIndicator, BorderLayout.EAST);

            lblStatus = new JLabel(status, SwingConstants.CENTER);
            lblStatus.setFont(new Font("SansSerif", Font.PLAIN, 14));

            lblTimer = new JLabel(isRunning ? "Berjalan..." : "00:00:00", SwingConstants.CENTER);
            lblTimer.setFont(new Font("SansSerif", Font.BOLD, 16));

            add(topPanel, BorderLayout.NORTH);
            add(lblStatus, BorderLayout.CENTER);
            add(lblTimer, BorderLayout.SOUTH);
        }

        public String getIdPs() { return idPs; }
        public JLabel getLblIndicator() { return lblIndicator; }
        public JLabel getLblStatus() { return lblStatus; }
        public JLabel getLblTimer() { return lblTimer; }

        public void setNormalBorder() {
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(209, 213, 219), 2, true),
                    new EmptyBorder(10, 10, 10, 10)
            ));
        }

        public void setSelectedBorder() {
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(COLOR_PRIMARY, 3, true),
                    new EmptyBorder(10, 10, 10, 10)
            ));
        }
    }

    public RentalView() {
        setTitle("Sistem Manajemen Rental PS Pro Final");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(COLOR_BG);

        UIManager.put("TabbedPane.background", COLOR_BG);
        UIManager.put("TabbedPane.selected", COLOR_CARD);
        UIManager.put("TabbedPane.font", FONT_BOLD);
        /*
        JLabel lblTitle = new JLabel("Sistem Manajemen Rental PlayStation", SwingConstants.CENTER);
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 22));
        lblTitle.setForeground(COLOR_PRIMARY);
        lblTitle.setBorder(new EmptyBorder(18, 0, 10, 0));
        add(lblTitle, BorderLayout.NORTH);
        */

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBorder(new EmptyBorder(0, 10, 10, 10));

        JPanel pnlRental = new JPanel(new BorderLayout(15, 15));
        pnlRental.setBackground(COLOR_BG);

        pnlDashboardCards = new JPanel(new GridLayout(0, 4, 15, 15));
        pnlDashboardCards.setBorder(new EmptyBorder(15, 15, 15, 15));
        pnlDashboardCards.setBackground(COLOR_BG);
        JScrollPane scrollCards = new JScrollPane(pnlDashboardCards);
        scrollCards.setBorder(null);

        JPanel pnlInput = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        pnlInput.setBackground(COLOR_CARD);
        pnlInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235), 1),
                new EmptyBorder(10, 15, 10, 15)
        ));

        lblSelectedPs = createStyledLabel("Terpilih: [Belum Ada]");
        lblSelectedPs.setForeground(COLOR_PRIMARY);

        rbMember = new JRadioButton("Member", true);
        rbGuest = new JRadioButton("Guest");
        rbMember.setBackground(COLOR_CARD);
        rbGuest.setBackground(COLOR_CARD);
        bgTipePenyewa = new ButtonGroup();
        bgTipePenyewa.add(rbMember);
        bgTipePenyewa.add(rbGuest);

        cbMember = new JComboBox<>();
        txtNamaGuest = new JTextField(12);
        txtNamaGuest.setVisible(false);
        txtDurasi = new JTextField(5);
        btnSewa = styleButton("Mulai Sewa", COLOR_SUCCESS);
        btnSelesai = styleButton("Selesaikan Sewa", COLOR_PRIMARY);

        styleComponent(cbMember);
        styleComponent(txtNamaGuest);
        styleComponent(txtDurasi);

        rbMember.addActionListener(e -> {
            cbMember.setVisible(true);
            txtNamaGuest.setVisible(false);
        });
        rbGuest.addActionListener(e -> {
            cbMember.setVisible(false);
            txtNamaGuest.setVisible(true);
        });

        pnlInput.add(lblSelectedPs);
        pnlInput.add(new JLabel(" | "));
        pnlInput.add(rbMember);
        pnlInput.add(rbGuest);
        pnlInput.add(cbMember);
        pnlInput.add(txtNamaGuest);
        pnlInput.add(createStyledLabel("Durasi (Jam):"));
        pnlInput.add(txtDurasi);
        pnlInput.add(btnSewa);
        pnlInput.add(btnSelesai);

        pnlRental.add(scrollCards, BorderLayout.CENTER);
        pnlRental.add(pnlInput, BorderLayout.SOUTH);

        JPanel pnlTransaksi = new JPanel(new BorderLayout());
        pnlTransaksi.setBackground(COLOR_BG);
        modTransaksi = new DefaultTableModel(new String[]{"ID Transaksi", "Penyewa", "PS", "Waktu Mulai", "Durasi", "Total", "Status"}, 0);
        tblTransaksi = createModernTable(modTransaksi);
        pnlTransaksi.add(new JScrollPane(tblTransaksi), BorderLayout.CENTER);

        JPanel pnlManageMember = new JPanel(new BorderLayout(15, 15));
        pnlManageMember.setBackground(COLOR_BG);
        modManageMember = new DefaultTableModel(new String[]{"ID Member", "Nama", "Jenis"}, 0);
        tblManageMember = createModernTable(modManageMember);

        JPanel pnlFormMember = new JPanel(new GridLayout(3, 2, 10, 12));
        pnlFormMember.setBackground(COLOR_CARD);
        pnlFormMember.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(229, 231, 235)), "Form Kelola Member", TitledBorder.LEFT, TitledBorder.TOP, FONT_BOLD, COLOR_PRIMARY));
        ((TitledBorder) pnlFormMember.getBorder()).setBorder(new EmptyBorder(15, 15, 15, 15));

        txtMemberId = new JTextField();
        txtMemberId.setVisible(false);
        txtMemberNama = new JTextField();
        cbMemberJenis = new JComboBox<>(new String[]{"Regular", "VIP"});
        styleComponent(txtMemberNama);
        styleComponent(cbMemberJenis);

        JPanel pnlBtnMember = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        pnlBtnMember.setBackground(COLOR_CARD);
        btnMemberTambah = styleButton("Tambah", COLOR_SUCCESS);
        btnMemberUbah = styleButton("Ubah", COLOR_PRIMARY);
        btnMemberHapus = styleButton("Hapus", COLOR_DANGER);
        pnlBtnMember.add(btnMemberTambah);
        pnlBtnMember.add(btnMemberUbah);
        pnlBtnMember.add(btnMemberHapus);

        pnlFormMember.add(createStyledLabel("Nama Member:"));
        pnlFormMember.add(txtMemberNama);
        pnlFormMember.add(createStyledLabel("Jenis Member:"));
        pnlFormMember.add(cbMemberJenis);
        pnlFormMember.add(createStyledLabel("Aksi:"));
        pnlFormMember.add(pnlBtnMember);

        pnlManageMember.add(new JScrollPane(tblManageMember), BorderLayout.CENTER);
        pnlManageMember.add(pnlFormMember, BorderLayout.SOUTH);

        JPanel pnlManagePS = new JPanel(new BorderLayout(15, 15));
        pnlManagePS.setBackground(COLOR_BG);
        modManagePS = new DefaultTableModel(new String[]{"ID PS", "Tipe", "Harga/Jam", "Status"}, 0);
        tblManagePS = createModernTable(modManagePS);

        JPanel pnlFormPS = new JPanel(new GridLayout(3, 2, 10, 12));
        pnlFormPS.setBackground(COLOR_CARD);
        pnlFormPS.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(229, 231, 235)), "Form Kelola PlayStation", TitledBorder.LEFT, TitledBorder.TOP, FONT_BOLD, COLOR_PRIMARY));
        ((TitledBorder) pnlFormPS.getBorder()).setBorder(new EmptyBorder(15, 15, 15, 15));

        txtPsId = new JTextField();
        txtPsId.setVisible(false);
        cbPsTipe = new JComboBox<>(new String[]{"PS4", "PS5"});
        txtPsHarga = new JTextField();
        styleComponent(cbPsTipe);
        styleComponent(txtPsHarga);

        JPanel pnlBtnPs = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        pnlBtnPs.setBackground(COLOR_CARD);
        btnPsTambah = styleButton("Tambah", COLOR_SUCCESS);
        btnPsUbah = styleButton("Ubah", COLOR_PRIMARY);
        btnPsHapus = styleButton("Hapus", COLOR_DANGER);
        pnlBtnPs.add(btnPsTambah);
        pnlBtnPs.add(btnPsUbah);
        pnlBtnPs.add(btnPsHapus);

        pnlFormPS.add(createStyledLabel("Tipe Konsol:"));
        pnlFormPS.add(cbPsTipe);
        pnlFormPS.add(createStyledLabel("Harga Per Jam:"));
        pnlFormPS.add(txtPsHarga);
        pnlFormPS.add(createStyledLabel("Aksi:"));
        pnlFormPS.add(pnlBtnPs);

        pnlManagePS.add(new JScrollPane(tblManagePS), BorderLayout.CENTER);
        pnlManagePS.add(pnlFormPS, BorderLayout.SOUTH);

        tabbedPane.addTab("Dashboard Rental", pnlRental);
        tabbedPane.addTab("Riwayat Transaksi", pnlTransaksi);
        tabbedPane.addTab("Kelola Member", pnlManageMember);
        tabbedPane.addTab("Kelola PlayStation", pnlManagePS);
        add(tabbedPane, BorderLayout.CENTER);
    }

    private JTable createModernTable(DefaultTableModel model) {
        JTable table = new JTable(model) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table.setFont(FONT_MAIN);
        table.setRowHeight(30);
        table.setGridColor(new Color(243, 244, 246));
        table.setSelectionBackground(new Color(219, 234, 254));
        table.setSelectionForeground(COLOR_TEXT);
        table.setShowVerticalLines(false);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_BOLD);
        header.setBackground(COLOR_CARD);
        header.setForeground(COLOR_TEXT);
        header.setPreferredSize(new Dimension(header.getWidth(), 35));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        table.setDefaultRenderer(Object.class, centerRenderer);

        return table;
    }

    private JButton styleButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setOpaque(true);              // tambah ini
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(8, 15, 8, 15));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void styleComponent(JComponent comp) {
        comp.setFont(FONT_MAIN);
        comp.setBackground(COLOR_CARD);
        comp.setForeground(COLOR_TEXT);
        comp.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(209, 213, 219), 1),
                new EmptyBorder(5, 8, 5, 8)
        ));
    }

    private JLabel createStyledLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_BOLD);
        label.setForeground(COLOR_TEXT);
        return label;
    }

    public List<CardPS> getListCards() { return listCards; }
    public JPanel getPnlDashboardCards() { return pnlDashboardCards; }
    public String getSelectedPsId() { return selectedPsId; }
    public void setSelectedPsId(String selectedPsId) { this.selectedPsId = selectedPsId; }
    public JLabel getLblSelectedPs() { return lblSelectedPs; }
    public JRadioButton getRbMember() { return rbMember; }
    public DefaultTableModel getModTransaksi() { return modTransaksi; }
    public DefaultTableModel getModManageMember() { return modManageMember; }
    public DefaultTableModel getModManagePS() { return modManagePS; }
    public JTable getTblManageMember() { return tblManageMember; }
    public JTable getTblManagePS() { return tblManagePS; }
    public JComboBox<String> getCbMember() { return cbMember; }
    public JComboBox<String> getCbMemberJenis() { return cbMemberJenis; }
    public JComboBox<String> getCbPsTipe() { return cbPsTipe; }
    public JTextField getTxtDurasi() { return txtDurasi; }
    public JTextField getTxtNamaGuest() { return txtNamaGuest; }
    public JTextField getTxtMemberId() { return txtMemberId; }
    public JTextField getTxtMemberNama() { return txtMemberNama; }
    public JTextField getTxtPsId() { return txtPsId; }
    public JTextField getTxtPsHarga() { return txtPsHarga; }
    public JButton getBtnSewa() { return btnSewa; }
    public JButton getBtnSelesai() { return btnSelesai; }
    public JButton getBtnMemberTambah() { return btnMemberTambah; }
    public JButton getBtnMemberUbah() { return btnMemberUbah; }
    public JButton getBtnMemberHapus() { return btnMemberHapus; }
    public JButton getBtnPsTambah() { return btnPsTambah; }
    public JButton getBtnPsUbah() { return btnPsUbah; }
    public JButton getBtnPsHapus() { return btnPsHapus; }

    // Kontrol editable/disabled form Member (Ubah & Hapus)
    public void setMemberFormEditable(boolean editable) {
        btnMemberUbah.setEnabled(editable);
        btnMemberHapus.setEnabled(editable);
    }

    // Kontrol editable/disabled form PS (Ubah & Hapus)
    public void setPsFormEditable(boolean editable) {
        btnPsUbah.setEnabled(editable);
        btnPsHapus.setEnabled(editable);
    }
}
package model;

public abstract class Konsol {
    private String id;
    private String jenis;
    private double hargaPerJam;
    private String status;

    public Konsol(String id, String jenis, double hargaPerJam, String status) {
        this.id = id;
        this.jenis = jenis;
        this.hargaPerJam = hargaPerJam;
        this.status = status;
    }

    public String getId() { return id; }
    public String getJenis() { return jenis; }
    public double getHargaPerJam() { return hargaPerJam; }
    public String getStatus() { return status; }
    
    public void setStatus(String status) { this.status = status; }

    // Overloading: hitungBiaya tanpa parameter → kalkulasi untuk 1 jam (preview harga)
    public double hitungBiaya() {
        return hitungBiaya(1);
    }

    public abstract double hitungBiaya(int durasi);
}
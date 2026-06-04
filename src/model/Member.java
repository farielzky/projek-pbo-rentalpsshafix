package model;

public abstract class Member {
    private String idMember;
    private String nama;
    private String jenis;

    public Member(String idMember, String nama, String jenis) {
        this.idMember = idMember;
        this.nama = nama;
        this.jenis = jenis;
    }

    public String getIdMember() { return idMember; }
    public String getNama() { return nama; }
    public String getJenis() { return jenis; }

    // Overloading: hitungDiskon dengan rate manual (override persentase diskon default)
    public double hitungDiskon(double totalHarga, double persenDiskon) {
        return totalHarga * persenDiskon;
    }

    public abstract double hitungDiskon(double totalHarga);
}
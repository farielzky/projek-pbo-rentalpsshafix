package model;

public class PlayStation5 extends Konsol {
    public PlayStation5(String id, double hargaPerJam, String status) {
        super(id, "PS5", hargaPerJam, status);
    }

    @Override
    public double hitungBiaya(int durasi) {
        return getHargaPerJam() * durasi;
    }
}
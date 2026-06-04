package model;

public class PlayStation4 extends Konsol {
    public PlayStation4(String id, double hargaPerJam, String status) {
        super(id, "PS4", hargaPerJam, status);
    }

    @Override
    public double hitungBiaya(int durasi) {
        return getHargaPerJam() * durasi;
    }
}
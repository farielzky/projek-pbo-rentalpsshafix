package model;

public class Guest extends Member {
    public Guest(String nama) {
        super(null, nama, "Guest");
    }

    @Override
    public double hitungDiskon(double totalHarga) {
        return 0;
    }
}
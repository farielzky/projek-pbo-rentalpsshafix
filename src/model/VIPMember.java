package model;

public class VIPMember extends Member {
    public VIPMember(String idMember, String nama) {
        super(idMember, nama, "VIP");
    }

    @Override
    public double hitungDiskon(double totalHarga) {
        return totalHarga * 0.15;
    }
}
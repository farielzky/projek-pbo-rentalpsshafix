package model;

public class RegularMember extends Member {
    public RegularMember(String idMember, String nama) {
        super(idMember, nama, "Regular");
    }

    @Override
    public double hitungDiskon(double totalHarga) {
        return 0;
    }
}
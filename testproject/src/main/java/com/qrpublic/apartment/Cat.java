package com.qrpublic.apartment;

public class Cat extends Animal {
    String colorFur;

    public Cat(String n, int tail, String sound, String fur) {
        this(n, tail, sound);
        this.colorFur = fur;
    }

    public Cat(String n, int tail, String sound) {
        super(n, tail, sound);

    }

}

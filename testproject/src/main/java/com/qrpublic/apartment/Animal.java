package com.qrpublic.apartment;

import java.util.Objects;

public class Animal {
    String name;
    int tail;
    String sound;

    public Animal(String n) {
        this.name = n;
    }

    public Animal(String n, int tail, String sound) {
        this(n);
        this.tail = tail;
        this.sound = sound;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Animal animal = (Animal) o;
        return tail == animal.tail && Objects.equals(name, animal.name) && Objects.equals(sound, animal.sound);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, tail, sound);
    }
}

package com.qrpublic.apartment;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        //TIP Press <shortcut actionId="ShowIntentionActions"/> with your caret at the highlighted text
        // to see how IntelliJ IDEA suggests fixing it.
        System.out.println("Hello and welcome!");
        Animal animal1 = new Animal("Cat");
        Animal animal2 = new Animal("Cat");
        Animal animal3 = new Animal("Dog");
        System.out.println("animal 1 equals 2 ?:" + animal1.equals(animal2));
        System.out.println("animal 1 == 2 ?:" + String.valueOf(animal1 == animal2));

        Animal cat = new Cat("cat", 1, "meo", "yellow");
        Dog dog = new Dog("dog");
        Animal animalDog = new Dog("animalDog");
        Main main = new Main();
        try {
            main.cast(cat, Cat.class);
            System.out.println("Cast cat");
            main.cast(dog, Dog.class);
            System.out.println("Cast dog");
            main.cast(animalDog, Dog.class);
            System.out.println("Cast animal dog");
            main.cast(dog, Animal.class);
            System.out.println("Cast dog to animal");
            Animal animalCat = main.cast(cat, Animal.class);
            System.out.println("Cast cat to animal");
        } catch (ClassCastException e) {
            System.err.println(e.getMessage());
        }
    }

    public <T> T cast(Animal animal, Class<T> clazz) throws ClassCastException {
        return clazz.cast(animal);
    }
}
package com.qrpublic.apartment;

public class ComparisonMain {
    public static void main(String[] args) {
        String str1 = "string";
        String str2 = new String("string");
        System.out.println("str1 == str2? " + String.valueOf(str1 == str2));
        System.out.println("str1 equals str2? " + str1.equals(str2));

        Integer int1 = 50;
        Integer int2 = new Integer(50);
        int int3 = 50;
        System.out.println("int1 == int2? " + String.valueOf(int1 == int2)); // false
        System.out.println("int1 equals int2? " + int1.equals(int2)); // true, number, point to the same value
        System.out.println("int1 == int3? " + String.valueOf(int1 == int3)); // false>>true
        System.out.println("int1 equals int2? " + int1.equals(int2)); // true
        System.out.println("int1 equals int3? " + int1.equals(int3)); // true

        System.out.println("int2 == int3? " + String.valueOf(int2 == int3)); // true
        System.out.println("int2 equals int3? " + String.valueOf(int2.equals(int3))); // false>>true
    }
}

package com.cloudstorage.util;

import java.util.Random;
import java.util.UUID;

public class RandomChar {
    private static final String alphabetsInUpperCase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String alphabetsInLowerCase = "abcdefghijklmnopqrstuvwxyz";
    private static final String numbers = "0123456789";

    public static String unsignedGenerateChar(int size) {
        String alphabets = alphabetsInLowerCase + alphabetsInUpperCase + numbers;
        StringBuilder sb = new StringBuilder();

        Random r = new Random();

        for (int i = 0; i < size; i++) {
            sb.append(alphabets.charAt(r.nextInt(alphabets.length())));
        }

        return sb.toString();
    }

    public static String unsignedNoNumbersGenerateChar(int size) {
        String alphabets = alphabetsInLowerCase + alphabetsInUpperCase;
        StringBuilder sb = new StringBuilder();
        Random r = new Random();

        for (int i = 0; i < size; i++) {
            sb.append(alphabets.charAt(r.nextInt(alphabets.length())));
        }

        return sb.toString();
    }

    public static String allGenerateChar(int size) {
        StringBuilder sb = new StringBuilder();
        Random r = new Random();
        int max = 126;
        int min = 33;

        for (int i = 0; i < size; i++) {
            sb.append((char) r.nextInt(max - min + 1) + min);
        }

        return sb.toString();
    }

    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }
}
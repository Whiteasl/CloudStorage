package com.cloudstorage.util;

import java.util.Random;
import java.util.UUID;

/**
 * RandomChar
 */
public class RandomChar {
    private static final String alphabetsInUpperCase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String alphabetsInLowerCase = "abcdefghijklmnopqrstuvwxyz";
    private static final String numbers = "0123456789";

    /**
     * 无符号随机字符生成
     * 
     * @param size 随机字符数量
     * @return 返回生成的随机字符
     */
    public static String unsignedGenerateChar(int size) {
        String alphabets = alphabetsInLowerCase + alphabetsInUpperCase + numbers;
        StringBuilder sb = new StringBuilder();

        Random r = new Random();

        for (int i = 0; i < size; i++) {
            sb.append(alphabets.charAt(r.nextInt(alphabets.length())));
        }

        return sb.toString();
    }

    /**
     * 无数字、符号随机字符生成
     * 
     * @param size 随机字符数量
     * @return 返回生成的随机字符
     */
    public static String unsignedNoNumbersGenerateChar(int size) {
        String alphabets = alphabetsInLowerCase + alphabetsInUpperCase;
        StringBuilder sb = new StringBuilder();
        Random r = new Random();

        for (int i = 0; i < size; i++) {
            sb.append(alphabets.charAt(r.nextInt(alphabets.length())));
        }

        return sb.toString();
    }

    /**
     * 包含所有符号、数字随机字符生成
     * 
     * @param size 随机字符数量
     * @return 返回生成的随机字符
     */
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

    /**
     * 获取UUID
     * 
     * @return 返回生成的UUID
     */
    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }
}
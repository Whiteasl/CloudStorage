package com.cloudstorage;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// @SpringBootTest
// public class CloudstorageApplicationTests {
//     @Test
//     void contextLoads() {
//     }

// }

/**
 * CloudstorageApplicationTests
 */
public class CloudstorageApplicationTests {

    public static void main(String[] args) {
        System.out.println(UUID.randomUUID().toString().substring(0, 8));
    }
}
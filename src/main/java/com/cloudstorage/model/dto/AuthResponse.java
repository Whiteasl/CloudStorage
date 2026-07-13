package com.cloudstorage.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter

public class AuthResponse {

    private String username;
    private String token;
    private String role;
}
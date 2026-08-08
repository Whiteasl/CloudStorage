package com.cloudstorage.model.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter

public class AuthResponse {

    private String username;
    private String token;
    private String role;
}
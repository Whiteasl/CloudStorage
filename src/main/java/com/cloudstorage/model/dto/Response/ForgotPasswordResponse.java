package com.cloudstorage.model.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class ForgotPasswordResponse {
    private boolean success;
    private String token;
}

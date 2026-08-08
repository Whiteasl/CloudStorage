package com.cloudstorage.model.dto.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * ResetPasswordRequest
 * 携带 Token 和 密码 ，执行密码重置
 */
@AllArgsConstructor
@Getter
@Setter

public class ResetPasswordRequest {
    @NotBlank
    private String token;

    @NotBlank
    private String newPassword;
}

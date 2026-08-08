package com.cloudstorage.model.dto.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * ForgetPasswordRequest
 * 
 * 用户输入邮箱，发起忘记密码请求
 */
@AllArgsConstructor
@Getter
@Setter
public class ForgotPasswordRequest {
    @NotBlank
    private String email;
}

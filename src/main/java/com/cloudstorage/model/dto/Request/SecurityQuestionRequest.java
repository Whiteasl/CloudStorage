package com.cloudstorage.model.dto.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * SecurityQuestionRequest
 * 输入邮箱，获取用户的密保问题
 */
@Getter
@Setter
@AllArgsConstructor

public class SecurityQuestionRequest {
    @NotBlank
    private String token;
}
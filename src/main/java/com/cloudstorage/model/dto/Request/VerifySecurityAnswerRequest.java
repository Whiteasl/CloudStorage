package com.cloudstorage.model.dto.Request;

import java.util.List;

import com.cloudstorage.model.dto.AnswerItem;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * VerifySecurityAnswerRequest
 * 校验前端返回的答案请求
 */
@AllArgsConstructor
@Setter
@Getter
public class VerifySecurityAnswerRequest {
    @NotBlank
    private String token; // 密保问题的Token
    @NotEmpty
    private List<AnswerItem> answers; // 用户提交的答案
}

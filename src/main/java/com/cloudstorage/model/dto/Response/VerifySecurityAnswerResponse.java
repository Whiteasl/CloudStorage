package com.cloudstorage.model.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * VerifySecurityAnswerResponse
 * 校验结果返回
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class VerifySecurityAnswerResponse {
    private boolean judgment; // 校验结果
    private String token; // 通过时才有重置密码的Token
}

package com.cloudstorage.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * AnswerItem
 * VerifySecurityAnswerRequest 的工具类
 * 获取前端传入的问题ID和答案
 */
@AllArgsConstructor
@Setter
@Getter
public class AnswerItem {
    private Long id; // 问题ID
    private String answer; // 用户提交的答案
}

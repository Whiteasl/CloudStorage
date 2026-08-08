package com.cloudstorage.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * QuestionItem
 * SecurityQuestionResponse 的工具类
 * 返回单条问题(ID，内容)，不含答案
 */

@Setter
@Getter
@AllArgsConstructor
public class QuestionItem {
    private Long id;
    private String question;
}

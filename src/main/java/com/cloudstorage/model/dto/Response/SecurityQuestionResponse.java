package com.cloudstorage.model.dto.Response;

import java.util.List;

import com.cloudstorage.model.dto.QuestionItem;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Getter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
/**
 * SecurityQuestionResponse
 * 返回问题列表， [{id: 1, question: "..."}, {id: 1, question: "..."}]
 */
public class SecurityQuestionResponse {
    private List<QuestionItem> questions;

}

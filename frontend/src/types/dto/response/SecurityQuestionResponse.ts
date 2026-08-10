// 密保问题响应格式

import type { QuestionItem } from "../QuestionItem";

export interface SecurityQuestionResponse {
  questions: QuestionItem[]; // 密保问题列表
}

// 验证用户密保答案的请求格式体

import type { AnswerItem } from "../AnswerItem";

export interface VerifySecurityAnswerRequest {
  token: string;
  answers: AnswerItem[];
}

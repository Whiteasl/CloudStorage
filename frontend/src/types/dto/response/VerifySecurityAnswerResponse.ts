// 验证密保问题返回格式体

export interface VerifySecurityAnswerResponse {
  judgment: boolean; // 校验结果
  token: string; // 通过时才有的重置密码
}

// 忘记密码响应格式

export interface ForgotPasswordResponse {
  success: boolean; // 验证状态
  token: string; // 用于 忘记密码 对话的Token
}

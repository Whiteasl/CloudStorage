// 重置密码请求格式体

export interface ResetPasswordRequest {
  token: string; // 用于验证身份的Token
  newPassword: string; // 新密码
}

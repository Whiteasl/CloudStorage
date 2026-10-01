/**
 * 访问 /me 端点对登录态存活状态进行判断
 * 登录态发生变化时，同步更新
 */

import type { CurrentUserResponse } from "../types/dto/response/CurrentUserResponse";

import { get } from "../api/client";
import { authStore } from "./authStore";

// 探测登录态
export async function probeSession(): Promise<void> {
  try {
    const response = await get<CurrentUserResponse>("/me");

    authStore.setUser(response);
  } catch {
    // 出现任何错误都直接清空登录态
    authStore.clear();
  }
}

export function logout() {
  window.location.href = "/logout";
}

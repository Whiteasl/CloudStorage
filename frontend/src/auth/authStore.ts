/*
不依赖 api 中的 client.ts 文件，防止形成 import 回环
因为登录时的 Token 存储在 Cookie 中，且为 Http-Only 属性
前端无法读取，故使用本文件对登录态进行存储
如果登录态发生变化，会同步通知所有关注登录态的组件
*/

import type { CurrentUserResponse } from "../types/dto/response/CurrentUserResponse";
import { useSyncExternalStore } from "react";

export type AuthStatus = "unknown" | "authenticated" | "anonymous";

export interface AuthState {
  status: AuthStatus;
  user: CurrentUserResponse | null;
}

// 登录态的唯一存放变量，用于全局读取
let state: AuthState = { status: "unknown", user: null };

// 回调名单 Set类型避免重复注册导致重复回调
const listeners: Set<() => void> = new Set();

// 设置状态，调用回调名单
function setState(next: AuthState) {
  state = next;

  listeners.forEach((callback) => callback());
}

// 提供给外部的订阅函数
function subscribe(listener: () => void) {
  listeners.add(listener);

  return () => {
    listeners.delete(listener);
  };
}

// 提供给外部获取快照的函数
function getSnapshot() {
  /*
  直接返回 state 变量
  返回 {...state} 或 {state, user} 会导致每次产生新对象时， React 都重复刷新
  */
  return state;
}

export const authStore = {
  subscribe,
  getSnapshot,

  setUser(user: CurrentUserResponse) {
    setState({ status: "authenticated", user: user });
  },

  clear() {
    setState({ status: "anonymous", user: null });
  },
};

export function useAuth() {
  return useSyncExternalStore(authStore.subscribe, authStore.getSnapshot);
}

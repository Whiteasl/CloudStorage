import { useState } from "react";
import { post } from "../api/client";
import type { AuthResponse } from "../types";
import { Link } from "react-router-dom";

async function LoginSubmit(username: string, password: string): Promise<void> {
  try {
    const data = await post<AuthResponse>("/login", { username, password });

    localStorage.setItem("token", data.token);
    window.location.href = "/files";
  } catch (error) {
    console.log("错误：" + error);
  }
}

export default function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        LoginSubmit(username, password);
      }}
    >
      <label>
        账号：
        <input
          type="text"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        />
      </label>

      <label>
        密码：
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </label>

      <button type="submit">登录</button>
      <Link to="/register">注册</Link>
    </form>
  );
}

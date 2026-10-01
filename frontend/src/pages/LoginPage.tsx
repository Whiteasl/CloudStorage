import { useState } from "react";
import { ApiError, post } from "../api/client";
import type { AuthResponse } from "../types/dto/response/AuthResponse";
import { Link, useNavigate } from "react-router-dom";
import { probeSession } from "../auth/session";

export default function LoginPage() {
  const navigate = useNavigate();

  const [username, setUsername] = useState<string>("");
  const [password, setPassword] = useState<string>("");

  const [error, setError] = useState<string>("");

  async function LoginSubmit(
    username: string,
    password: string,
  ): Promise<void> {
    // 清空错误信息
    setError("");

    try {
      await post<AuthResponse>("/login", { username, password });

      await probeSession();

      navigate("/files");
    } catch (e) {
      console.log("错误：" + e);
      if (e instanceof ApiError && e.status === 401) {
        setError("账号或密码错误，请检查后重试");
      } else {
        setError("无法登录，请重试");
      }
    }
  }

  return (
    <div className="auth-page">
      <h2>登录</h2>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          LoginSubmit(username, password);
        }}
      >
        <label>
          账号
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

        {error && <p className="error-state">{error}</p>}

        <button type="submit">登录</button>
        <Link to="/register" className="auth-link">
          注册
        </Link>
        <Link to="/forgot-password" className="auth-link">
          忘记密码
        </Link>
      </form>
    </div>
  );
}

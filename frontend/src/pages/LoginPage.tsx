import { useState } from "react";
import { post } from "../api/client";
import type { AuthResponse } from "../types/dto/response/AuthResponse";
import { Link, useNavigate } from "react-router-dom";

export default function LoginPage() {
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  async function LoginSubmit(
    username: string,
    password: string,
  ): Promise<void> {
    try {
      const data = await post<AuthResponse>("/login", { username, password });

      localStorage.setItem("token", data.token);
      navigate("/files");
    } catch (error) {
      console.log("错误：" + error);
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

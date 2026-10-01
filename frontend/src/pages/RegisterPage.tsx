import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ApiError, post } from "../api/client";
import type { AuthResponse } from "../types/dto/response/AuthResponse";
import { probeSession } from "../auth/session";

export default function RegisterPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [email, setEmail] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");

  const navigate = useNavigate();

  async function RegisterSubmit(
    username: string,
    password: string,
    email: string,
  ): Promise<void> {
    setError("");

    try {
      await post<AuthResponse>("/register", {
        username,
        password,
        email,
      });

      await probeSession();

      navigate("/files");
    } catch (e) {
      console.error("错误：" + e);

      if (e instanceof ApiError && e.status === 409) {
        setError("邮箱或用户名已被注册，请检查后重试");
      } else {
        setError("注册失败，请重试");
      }
    }
  }

  return (
    <div className="auth-page">
      <h2>注册</h2>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (confirmPassword !== password) {
            setError("两次密码不一致");
            return;
          }
          RegisterSubmit(username, password, email);
        }}
      >
        <label>
          账号
          <input
            name="username"
            type="text"
            placeholder="请输入你的账号"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </label>
        <label>
          密码：
          <input
            name="password"
            type="password"
            placeholder="请输入你的密码"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          ></input>
        </label>
        <label>
          再次确定你的密码：
          <input
            type="password"
            value={confirmPassword}
            placeholder="再次确定你的密码"
            onChange={(e) => setConfirmPassword(e.target.value)}
            required
          ></input>
        </label>
        <label>
          邮箱：
          <input
            name="email"
            type="email"
            placeholder="请输入你的邮箱"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          ></input>
        </label>

        {error && <p className="error-state">{error}</p>}

        <button type="submit">注册</button>
        <Link to="/login" className="auth-link">
          去登录
        </Link>
      </form>
    </div>
  );
}

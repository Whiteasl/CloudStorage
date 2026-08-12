// 重设密码页面

import { useEffect, useState } from "react";
import { ApiError, get, post } from "../api/client";
import { Link, useSearchParams } from "react-router-dom";
import type { ResetPasswordRequest } from "../types/dto/request/ResetPasswordRequest";

// 重置密码页面
function ResetPassword({ token }: { token: string }) {
  // 存放修改成功的返回
  const [success, setSuccess] = useState<boolean>(false);

  //   存取信息
  const [message, setMessage] = useState<string>("");

  const [newPassword, setNewPassword] = useState<string>("");
  const [repeatPassword, setRepeatPassword] = useState<string>("");

  //   定义加载状态
  const [loading, setLoading] = useState<boolean>(false);

  return (
    <>
      <div className="reset-password">
        {message == "" && (
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              if (newPassword === repeatPassword)
                try {
                  setLoading(true);
                  if ((await submitPassword(token, newPassword)) === null) {
                    setMessage("修改成功，请登录");
                    setSuccess(true);
                  }
                } finally {
                  setLoading(false);
                }
              else setMessage("两次输入的密码不同，请检查后重新输入");
            }}
          >
            <label>
              新密码：
              <input
                type="password"
                onChange={(e) => setNewPassword(e.target.value)}
                value={newPassword}
                required
              />
              重复密码：
              <input
                type="password"
                onChange={(e) => setRepeatPassword(e.target.value)}
                value={repeatPassword}
                required
              />
              <button type="submit" disabled={loading}>
                重置密码
              </button>
            </label>
          </form>
        )}
        {message && (
          <>
            <p>{message}</p>
            {!success ? (
              <button
                type="button"
                onClick={() => {
                  setMessage("");
                  setNewPassword("");
                  setRepeatPassword("");
                }}
              >
                重试
              </button>
            ) : (
              <Link to="/login">去登录</Link>
            )}
          </>
        )}
      </div>
    </>
  );
}

// 提交新密码请求
async function submitPassword(token: string, newPassword: string) {
  const body: ResetPasswordRequest = {
    token: token,
    newPassword: newPassword,
  };

  try {
    await post("/forgot-password/reset", body);
  } catch (error) {
    if (error instanceof ApiError) {
      switch (error.status) {
        case 400:
          return "账号异常";
        case 429:
          return "请求过于频繁，请稍后再试";
        default:
          return "请求异常，请重试";
      }
    } else {
      return "网络异常，请检查连接";
    }
  }

  return null;
}

async function validateToken(token: string) {
  if (await get(`/forgot-password/validate?token=${token}`)) return true;

  return false;
}

export default function ForgotAndResetPasswordPage() {
  const [searchParams] = useSearchParams();
  //   存放信息
  const [message, setMessage] = useState<string>("");
  const resetToken = searchParams.get("token"); // 读取 URL 中的 token 值

  useEffect(() => {
    async function f() {
      if (resetToken === null) {
        setMessage("无效Token，请返回找回密码页面重试");
        return;
      }
      try {
        if (!(await validateToken(resetToken)))
          // 验证 Token 是否正确
          setMessage("账号异常");
      } catch (error) {
        if (error instanceof ApiError) {
          switch (error.status) {
            case 429:
              setMessage("请求过于频繁，请稍后再试");
              break;
            default:
              setMessage("请求失败，请重试");
          }
        } else {
          setMessage("网络异常，请检查连接");
        }
        console.error(error);
      }
    }
    f();
  }, [resetToken]);
  return (
    <>
      <div className="forgot-reset-password">
        {resetToken && message == "" ? (
          <ResetPassword token={resetToken} />
        ) : (
          <>
            <Link to="/forgot-password">返回</Link>
            <p>{message}</p>
          </>
        )}
      </div>
    </>
  );
}

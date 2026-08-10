import { useState } from "react";
import { ApiError, post } from "../api/client";
import type { ForgotPasswordResponse } from "../types/dto/response/ForgotPasswordResponse";
import type { AnswerItem } from "../types/dto/AnswerItem";
import type { QuestionItem } from "../types/dto/QuestionItem";
import type { SecurityQuestionResponse } from "../types/dto/response/SecurityQuestionResponse";
import type { VerifySecurityAnswerResponse } from "../types/dto/response/VerifySecurityAnswerResponse";
import type { VerifySecurityAnswerRequest } from "../types/dto/request/VerifySecurityAnswerRequest";
import type { SecurityQuestionRequest } from "../types/dto/request/SecurityQuestionRequest";
import type { ForgotPasswordRequest } from "../types/dto/request/ForgotPasswordRequest";
import { useNavigate } from "react-router-dom";

export default function ForgotPasswordPage() {
  // 记录找回方法 - 邮件验证/密保认证
  const [approach, setApproach] = useState<"email" | "question" | null>(null);
  // 保存进行的步骤
  // 0 - 输入邮箱
  // 1 - 选择验证方式
  // 2 - 邮箱验证
  // 3 - 密保问题验证
  const [step, setStep] = useState<0 | 1 | 2 | 3>(0);
  // 接收后端返回的Token
  const [flowToken, setFlowToken] = useState<string>("");
  // 邮箱
  const [email, setEmail] = useState<string>("");
  // 密保问题
  const [questions, setQuestions] = useState<QuestionItem[]>([]);
  // 密保答案
  const [answers, setAnswers] = useState<AnswerItem[]>([]);
  // 信息接收
  const [message, setMessage] = useState<string>("");
  // 加载状态
  const [loading, setLoading] = useState<boolean>(false);

  const navigate = useNavigate();

  // 提交邮箱信息
  async function emailSubmit(): Promise<void> {
    try {
      setLoading(true);
      setMessage("邮箱验证中，请稍候");

      const body: ForgotPasswordRequest = { email: email };
      const result = await post<ForgotPasswordResponse>(
        "/forgot-password",
        body,
      );
      if (result.success == true) {
        // 邮箱验证成功
        setStep(1);
        setFlowToken(result.token);
        setMessage("");
      } else {
        // 邮箱验证失败
        setMessage("未找到用户，请检查邮箱是否正确填写");
      }
    } catch (error) {
      if (error instanceof ApiError) {
        switch (error.status) {
          case 404:
            setMessage("未找到用户，请检查邮箱是否正确填写");
            break;
          case 429:
            setMessage("请求过于频繁，请稍后再试");
            break;
          default:
            setMessage("请求失败，请重试");
        }
      } else {
        setMessage("网络异常，请检查网络连接");
      }
      console.error(error);
    } finally {
      setLoading(false);
    }
  }

  // 邮箱找回
  async function recoverEmail(): Promise<void> {
    try {
      setLoading(true);
      setApproach("email");
      setStep(2);

      setMessage("正在发送邮件...");

      const body: ForgotPasswordRequest = { email: email };
      await post<void>("/forgot-password/email", body);
      setMessage("验证邮件已发送至你的邮箱\n如找不到邮件，请检查垃圾邮箱");
    } catch (error) {
      if (error instanceof ApiError) {
        switch (error.status) {
          case 404:
            setMessage("未找到用户");
            break;
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
    } finally {
      setLoading(false);
    }
  }

  // 获取密保问题
  async function getQuestions(): Promise<void> {
    try {
      setLoading(true);
      setApproach("question");

      // 请求密保问题
      const body: SecurityQuestionRequest = { token: flowToken };
      const data = await post<SecurityQuestionResponse>(
        "/security-questions",
        body,
      );
      if (data.questions.length != 0) {
        setQuestions(data.questions);
        setStep(3);
      } else {
        setMessage("未找到密保问题，请使用其他验证方法");
      }
    } catch (error) {
      if (error instanceof ApiError) {
        switch (error.status) {
          case 400:
            setMessage("账号异常，请检查后重试");
            break;
          case 410:
            setMessage("您还没有设置密保问题");
            break;
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
    } finally {
      setLoading(false);
    }
  }

  // 验证密保答案
  async function verifyAnswers(): Promise<void> {
    if (answers.length == 0) {
      setMessage("请输入答案");
      return;
    }
    try {
      setMessage("验证中...");

      setLoading(true);
      const body: VerifySecurityAnswerRequest = {
        token: flowToken,
        answers: answers,
      };
      const data = await post<VerifySecurityAnswerResponse>(
        "/verify-answers",
        body,
      );

      if (data.judgment == true && data.token) {
        setMessage("验证成功，正在跳转到重置密码页面");
        setQuestions([]);
        setAnswers([]);
        navigate(`/reset-password?token=${data.token}`);
      } else {
        setMessage("验证失败");
        setAnswers([]);
      }
    } catch (error) {
      if (error instanceof ApiError) {
        switch (error.status) {
          case 400:
            setMessage("账号异常，请检查后重试");
            break;
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
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <div className="forgot-password">
        {/* step 0 - 输入邮箱 */}
        {approach == null && step == 0 ? (
          <div className="step-0">
            <h1>请输入绑定用户的邮箱</h1>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                setMessage("");
                emailSubmit();
              }}
            >
              <label>
                邮箱：
                <input
                  type="email"
                  value={email}
                  placeholder="请输入你的邮箱"
                  required
                  onChange={(e) => {
                    setEmail(e.target.value);
                  }}
                ></input>
              </label>
              <button type="submit" disabled={loading}>
                提交
              </button>
            </form>
            {message != "" && <p>{message}</p>}
          </div>
        ) : null}

        {/* step 1 - 选择找回方式 */}
        {step == 1 && flowToken ? (
          <>
            <div className="step-1">
              <a
                href="#"
                onClick={() => {
                  navigate("/login");
                }}
              >
                &#8249; 返回登录
              </a>
              <h1>选择你的找回方式</h1>

              <form>
                <button
                  type="button"
                  onClick={() => {
                    recoverEmail();
                  }}
                >
                  邮箱找回
                </button>
                <button
                  type="button"
                  onClick={() => {
                    getQuestions();
                  }}
                >
                  密保问题找回
                </button>
              </form>
              {message && <p>{message}</p>}
            </div>
          </>
        ) : null}

        {/* step 2 - 邮件找回 */}
        {approach == "email" && step == 2 ? (
          <>
            <div className="message">
              <h1>邮件找回</h1>
              <a
                href="#"
                onClick={() => {
                  setStep(1);
                  setMessage("");
                }}
              >
                &#8249; 返回
              </a>
              {/* loading 会根据发送情况改变， true 显示 正在发送邮件， false 显示 邮件发送完成 */}
              {message && <p>{message}</p>}
            </div>
          </>
        ) : null}

        {/* step 3 - 密保问题找回 */}
        {approach == "question" && step == 3 ? (
          <>
            <div className="step-3">
              <h1>密保问题找回</h1>
              <a
                href="#"
                onClick={() => {
                  setStep(1);
                  setMessage("");
                }}
              >
                &#8249; 返回
              </a>
              {step == 3 && (
                <>
                  {!loading && message == "" ? (
                    <>
                      <form
                        onSubmit={(e) => {
                          e.preventDefault();
                          verifyAnswers();
                        }}
                      >
                        {questions.map((question) => (
                          <label>
                            {question.question}
                            <input
                              name="answer"
                              type="text"
                              placeholder="请输入密保答案"
                              onChange={(e) =>
                                setAnswers((prev) => {
                                  const filtered = prev.filter(
                                    (a) => a.id !== question.id,
                                  );
                                  return [
                                    ...filtered,
                                    { id: question.id, answer: e.target.value },
                                  ];
                                })
                              }
                              required
                            />
                          </label>
                        ))}
                        <button type="submit">提交</button>
                      </form>
                    </>
                  ) : (
                    <div className="message">
                      <p>{message}</p>
                      {!loading && (
                        <button
                          onClick={() => {
                            setMessage("");
                            setAnswers([]);
                          }}
                        >
                          重试
                        </button>
                      )}
                    </div>
                  )}
                </>
              )}
            </div>
          </>
        ) : null}
      </div>
    </>
  );
}

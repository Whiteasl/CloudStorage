// 前后端通信层
const BASE_URL: string = import.meta.env.PROD ? "" : "/api";

function getToken(): string | null {
  let token: string | null;
  token = localStorage.getItem("token");
  return token;
}

export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const token: string | null = getToken();

  const response = await fetch(BASE_URL + url, {
    ...options,
    headers: {
      ...options?.headers,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });

  if (response.status === 401) {
    localStorage.removeItem("token");
    window.location.href = "/login";
    throw new Error("未登录");
  }

  if (!response.ok)
    throw new ApiError(`HTTP ${response.status}`, response.status);

  const text = await response.text();
  return text ? JSON.parse(text) : (undefined as T);
}

// 五个请求方法
export function get<T>(url: string): Promise<T> {
  return request<T>(url, { method: "GET" });
}

export function post<T>(url: string, body: unknown): Promise<T> {
  return request<T>(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function put<T>(url: string, body?: unknown): Promise<T> {
  return request<T>(url, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function del<T>(url: string): Promise<T> {
  return request<T>(url, {
    method: "DELETE",
  });
}

export function upload<T>(url: string, formData: FormData): Promise<T> {
  return request<T>(url, {
    method: "POST",
    body: formData,
  });
}

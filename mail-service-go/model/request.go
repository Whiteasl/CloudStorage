// Package model 定义邮件服务的请求和响应结构体
package model

// SendRequest 表示调用方发起的一次邮件发送请求
type SendRequest struct {
	To      string `json:"to"`      // 收件人地址
	Subject string `json:"subject"` // 邮件主题
	Body    string `json:"body"`    // 邮件正文(HTML)
}

// SendResponse 表示服务器响应状态
type SendResponse struct {
	Success   bool   `json:"success"`             // 发送成功/发送失败
	MessageID string `json:"messageId,omitempty"` // 信息ID
	Error     string `json:"error,omitempty"`     // 错误原因
}

// HealthResponse 表示健康状态响应
type HealthResponse struct {
	Status string `json:"status"` // 状态
}

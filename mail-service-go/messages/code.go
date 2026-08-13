// 错误码常量映射
package messages

// 状态码
type Code int

// 枚举状态码
const (
// Info 从 000 开始
)

const (
	// Warning 从 100 开始
	RateLimit Code = 100 + iota // 请求过于频繁
)

const (
	// Error 从 200 开始
	SendFailed    Code = 200 + iota // 发送失败
	AuthMissing                     // 未提供 Token
	AuthInvalid                     // Token 无效
	InvalidBody                     // 请求体格式错误
	Unauthorized                    // 未经授权
	LoadingFailed                   // 环境配置加载失败
)

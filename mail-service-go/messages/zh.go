package messages

var zh = map[Code]string{
	// Info 信息

	// 警告信息
	RateLimit: "请求过于频繁，请稍候再试",

	// 错误信息
	SendFailed:    "邮件发送失败",
	AuthMissing:   "未提供认证令牌",
	AuthInvalid:   "认证令牌无效",
	InvalidBody:   "请求体格式错误",
	Unauthorized:  "未经授权",
	LoadingFailed: "环境配置加载失败",
}

package messages

var en = map[Code]string{
	// Info Message

	// Warning Message
	RateLimit: "Too many requests, please try again later",

	// Error Message
	SendFailed:    "Email send failed",
	AuthMissing:   "Authentication token missing",
	AuthInvalid:   "Authentication token invalied",
	InvalidBody:   "Invalid request body",
	LoadingFailed: "Config Loading Failed, please check in enviroment",
}

package handle

import (
	"encoding/json"
	"mail-service-go/config"
	"mail-service-go/mailer"
	"mail-service-go/messages"
	"mail-service-go/model"
	"net/http"
)

type SendHandle struct {
	Mailer *mailer.Mailer
	Config config.Config
}

func (h *SendHandle) AuthMiddleware() func(http.Handler) http.Handler {
	panic("unimplemented")
}

func AuthMiddleware(token string, lang string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			auth := r.Header.Get("Authorization")

			if auth != "Bearer "+token {
				http.Error(w, messages.Message(messages.Unauthorized, lang), 401)
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

// POST /send
// 邮件发送
func (h *SendHandle) Send(w http.ResponseWriter, r *http.Request) {
	var req model.SendRequest

	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, messages.Message(messages.InvalidBody, h.Config.Language), 400)
		return
	}

	h.Mailer.Send(req)

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusAccepted)
	json.NewEncoder(w).Encode(model.SendResponse{Success: true})

}

// 健康检查
func (h *SendHandle) Health(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

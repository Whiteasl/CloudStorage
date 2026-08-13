package main

import (
	"log"
	"mail-service-go/config"
	"mail-service-go/handle"
	"mail-service-go/mailer"

	"net/http"

	"github.com/go-chi/chi/v5"
)

func main() {

	// 载入配置
	cfg, _ := config.Load()

	// 创建 Mailer
	m := mailer.New(cfg)

	h := &handle.SendHandle{Mailer: m, Config: *cfg}

	// 路由
	r := chi.NewRouter()

	r.Get("/health", h.Health)

	r.With(handle.AuthMiddleware(cfg.AuthToken, cfg.Language)).Post("/send", h.Send)

	log.Println("+-----------------------------------+")
	log.Println("|                                   |")
	log.Println("|          Mail-Service-Go          |")
	log.Println("|                                   |")
	log.Println("+-----------------------------------+")

	log.Printf("[*] Mail Host: %s", cfg.MailHost)
	log.Printf("[*] Application Listening Port: %s", cfg.ServerPort)
	log.Printf("[*] Mail Service Listening Port: %s", cfg.MailPort)
	log.Printf("[*] Mail Username: %s", cfg.MailUsername)
	// log.Printf("[*] Mail Password: %s", cfg.MailPassword)
	log.Printf("[*] Mail Worker Count: %d", cfg.MailWorkers)
	log.Printf("[*] Visit Host: http://%s:%s", cfg.MailHost, cfg.ServerPort)

	log.Fatal(http.ListenAndServe(":"+cfg.ServerPort, r))

}

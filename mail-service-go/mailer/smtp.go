package mailer

import (
	"log"
	"mail-service-go/config"
	"mail-service-go/messages"
	"mail-service-go/model"
	"time"

	"strconv"

	"gopkg.in/gomail.v2"
)

type Mailer struct {
	dialer      *gomail.Dialer
	sendRequest chan model.SendRequest
}

// New 创建一个 Mailer 实例
func New(cfg *config.Config) *Mailer {
	port, _ := strconv.Atoi(cfg.MailPort)
	m := &Mailer{
		dialer:      gomail.NewDialer(cfg.MailHost, port, cfg.MailUsername, cfg.MailPassword),
		sendRequest: make(chan model.SendRequest, 50),
	}

	// 限制进程数量，避免高并发导致被邮件服务商拉黑IP
	for i := 0; i < cfg.MailWorkers; i++ {
		go func() {
			for ch := range m.sendRequest {

				msg := gomail.NewMessage()
				message := map[string][]string{
					"From":    {cfg.MailFrom},
					"To":      {ch.To},
					"Subject": {ch.Subject},
				}
				msg.SetHeaders(message)
				msg.SetBody("text/html", ch.Body)
				for j := 0; j < 3; j++ {
					// 发送失败则重新尝试发送，3 次尝试均失败则进行记录

					if err := m.dialer.DialAndSend(msg); err == nil {
						break
					}

					if j == 2 {
						log.Printf("[!] %s, To:%s, Subject:%s, Body:%s, Retry count:3 \n", messages.Message(messages.SendFailed, cfg.Language), ch.To, ch.Subject, ch.Body)
					}

					time.Sleep(time.Second * time.Duration(j+1))
				}
			}

		}()
	}

	return m
}

// Send 方法，用于把 请求 放入 Channel 中，立刻返回状态
func (m *Mailer) Send(req model.SendRequest) {
	m.sendRequest <- req
}

package config

import "github.com/caarlos0/env/v11"

type Config struct {
	MailHost     string `env:"MAIL_HOST"`
	MailPort     string `env:"MAIL_PORT"`
	MailUsername string `env:"MAIL_USERNAME"`
	MailPassword string `env:"MAIL_PASSWORD"`
	MailFrom     string `env:"MAIL_FROM"`
	MailWorkers  int    `env:"MAIL_WORKERS"`
	ServerPort   string `env:"SERVER_PORT"`
	AuthToken    string `env:"AUTH_TOKEN"`
	Language     string `env:"LANGUAGE"`
}

func Load() (*Config, error) {

	cfg := &Config{
		MailHost:     "localhost",
		MailPort:     "587",
		MailUsername: "admin",
		MailPassword: "admin",
		MailFrom:     "admin@example.com",
		MailWorkers:  10,
		ServerPort:   "8081",
		AuthToken:    "shared-secret",
		Language:     "en",
	}

	err := env.Parse(cfg)

	return cfg, err
}

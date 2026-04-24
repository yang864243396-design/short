package utils

import (
	"fmt"
	"net/mail"
	"strings"

	"short-drama-backend/config"

	gomail "gopkg.in/gomail.v2"
)

// smtpEhloName 从发件地址取域名作为 EHLO 标识；避免默认 localhost，降低国内邮局（如 QQ）拒收或中继失败概率。
func smtpEhloName(from, smtpHost string) string {
	s := strings.TrimSpace(from)
	addr := s
	if a, err := mail.ParseAddress(s); err == nil && a.Address != "" {
		addr = a.Address
	} else if i, j := strings.LastIndex(s, "<"), strings.LastIndex(s, ">"); i >= 0 && j > i {
		addr = strings.TrimSpace(s[i+1 : j])
	}
	if i := strings.LastIndex(addr, "@"); i > 0 && i < len(addr)-1 {
		d := strings.ToLower(strings.TrimSpace(addr[i+1:]))
		if d != "" {
			return d
		}
	}
	if smtpHost != "" {
		return smtpHost
	}
	return ""
}

// SendRegisterVerificationEmail 发送注册验证码邮件。
// 端口 465：隐式 SSL（SSL=true）；端口 587/25 等：STARTTLS（SSL=false，gomail 自动协商 TLS）。
func SendRegisterVerificationEmail(cfg *config.SMTPConfig, toEmail, code string) error {
	if cfg == nil || cfg.Host == "" || cfg.User == "" || cfg.From == "" {
		return fmt.Errorf("SMTP 未配置")
	}
	m := gomail.NewMessage()
	m.SetHeader("From", cfg.From)
	m.SetHeader("To", toEmail)
	m.SetHeader("Subject", "注册验证码")
	body := fmt.Sprintf(
		`<p>您好，</p><p>您的注册验证码为：<b style="font-size:18px">%s</b></p><p>验证码 10 分钟内有效，请勿告知他人。</p><p style="color:#999;font-size:12px">如非本人操作，请忽略本邮件。</p>`,
		code,
	)
	m.SetBody("text/html", body)

	port := cfg.Port
	if port <= 0 {
		port = 465
	}
	d := gomail.NewDialer(cfg.Host, port, cfg.User, cfg.Password)
	// 465：全程 SSL；587：STARTTLS（多数企业/QQ 企业邮/163 发件用 587）
	d.SSL = port == 465
	if ehlo := smtpEhloName(cfg.From, cfg.Host); ehlo != "" {
		d.LocalName = ehlo
	}
	if err := d.DialAndSend(m); err != nil {
		return fmt.Errorf("smtp dial/send: %w", err)
	}
	return nil
}

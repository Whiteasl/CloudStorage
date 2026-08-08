package com.cloudstorage.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class MailClient {
    private RestTemplate restTemplate = new RestTemplate();
    private String authToken;
    private String serviceUrl;
    private static final Logger log = LoggerFactory.getLogger(MailClient.class);

    public MailClient(@Value("${cloudstorage.mail.auth-token}") String authToken,
            @Value("${cloudstorage.mail.service-url}") String serviceUrl) {
        this.authToken = authToken;
        this.serviceUrl = serviceUrl;
    }

    public void resetPasswordSend(String to, String subject, String text, String token) {
        HttpHeaders headers = new HttpHeaders();

        // 调用邮箱服务发送邮件
        headers.set("Authorization", "Bearer " + authToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("to", to, "subject", subject, "body", text);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForObject(serviceUrl, entity, String.class);
            log.info("密码重置邮件已发送至 {} ", to);
        } catch (Exception e) {
            log.warn("邮件服务不可达 ({}) , 重置链接已输出到日志", e.getMessage());
            log.info("重置链接：http://localhost:5173/reset-password?token={}", token);
        }
    }

    public void send(String to, String subject, String text) {
        HttpHeaders headers = new HttpHeaders();

        headers.set("Authorization", "Bearer " + authToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("to", to, "subject", subject, "body", text);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForObject(serviceUrl, entity, String.class);
            log.info("已发送一封邮件至：{}", to);
        } catch (Exception e) {
            log.warn("发送至 {} 的邮件出现错误，错误日志：({})", to, e.getMessage());
        }
    }
}

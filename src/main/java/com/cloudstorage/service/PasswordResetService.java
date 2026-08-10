package com.cloudstorage.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloudstorage.model.dto.AnswerItem;
import com.cloudstorage.model.dto.QuestionItem;
import com.cloudstorage.model.dto.Response.ForgotPasswordResponse;
import com.cloudstorage.model.dto.Response.SecurityQuestionResponse;
import com.cloudstorage.model.dto.Response.VerifySecurityAnswerResponse;
import com.cloudstorage.model.entity.SecurityQuestion;
import com.cloudstorage.model.entity.User;
import com.cloudstorage.repository.SecurityQuestionRepository;
import com.cloudstorage.repository.UserRepository;
import com.cloudstorage.util.JwtTokenUtil;

@Service
public class PasswordResetService {
    private final StringRedisTemplate redis;
    private final UserRepository userRepository;
    private final SecurityQuestionRepository securityQuestionRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailClient mailClient;
    private final JwtTokenUtil jwtTokenUtil;

    @Value("${cloudstorage.app.base-url}")
    private String baseURL;

    @Value("${cloudstorage.mail.reset-password-subject}")
    private String resetPasswordSubject;

    public PasswordResetService(StringRedisTemplate redis, UserRepository userRepository,
            SecurityQuestionRepository securityQuestionRepository, PasswordEncoder passwordEncoder,
            MailClient mailClient, JwtTokenUtil jwtTokenUtil) {
        this.redis = redis;
        this.userRepository = userRepository;
        this.securityQuestionRepository = securityQuestionRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailClient = mailClient;
        this.jwtTokenUtil = jwtTokenUtil;
    }

    /**
     * 忘记密码 响应
     * 
     * @param email 用于校验的邮箱
     * @return ForgetPasswordResponse
     */
    public ForgotPasswordResponse forgotPassword(String email) {
        userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到用户"));

        String token = jwtTokenUtil.generateForgotPasswordToken(email);

        return new ForgotPasswordResponse(true, token);
    }

    /**
     * 忘记密码 - 邮件验证
     * 
     * @param email 邮箱
     */
    public void forgotPasswordEmail(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到用户"));

        String token = this.generateTokenAndSaved(user);

        String link = this.baseURL + "/forgot-password/validate?token=" + token;

        mailClient.resetPasswordSend(email, resetPasswordSubject, link, token);

    }

    /**
     * 获取密保问题
     * 
     * @param sessionToken 用于校验本次“忘记密码”请求的Token
     * @return SecurityQuestionResponse
     */
    public SecurityQuestionResponse getSecurityQuestion(String sessionToken) {
        String email = jwtTokenUtil.validateToken(sessionToken).get("email", String.class);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "账户错误"));

        // 获取密保问题
        List<SecurityQuestion> securityQuestions = securityQuestionRepository.findByUserId(user.getId());

        if (securityQuestions.size() == 0)
            throw new ResponseStatusException(HttpStatus.GONE, "您还没有设置密保问题");

        SecurityQuestionResponse securityQuestionResponse = new SecurityQuestionResponse();

        // 初始化数组，避免空指针
        List<QuestionItem> temp = new ArrayList<>();
        securityQuestionResponse.setQuestions(temp);

        for (SecurityQuestion question : securityQuestions) {
            // 将问题放入返回体中
            securityQuestionResponse.getQuestions().add(new QuestionItem(question.getId(), question.getQuestion()));
        }

        return securityQuestionResponse;
    }

    /**
     * 校验密保问题的答案
     * 
     * @param sessionToken 校验本次“忘记密码”请求的Token
     * @param answers      前端传入的答案
     * @return VerifySecurityAnswerResponse - 根据答对的题数判断是否返回Token
     */
    public VerifySecurityAnswerResponse verifySecurityAnswers(String sessionToken, List<AnswerItem> answers) {
        String email = jwtTokenUtil.validateToken(sessionToken).get("email", String.class);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "账号错误"));
        // 校验密保答案
        double count = 0;

        List<SecurityQuestion> securityQuestions = securityQuestionRepository.findByUserId(user.getId());

        for (AnswerItem answer : answers) {
            for (SecurityQuestion realAnswer : securityQuestions) {
                if (passwordEncoder.matches(answer.getAnswer(), realAnswer.getAnswer())) {
                    count++;
                    break;
                }
            }
        }

        double probability = count / securityQuestions.size();

        if (probability > 0.75) {
            String token = this.generateTokenAndSaved(user);
            return new VerifySecurityAnswerResponse(true, token);
        }

        return new VerifySecurityAnswerResponse(false, "");
    }

    /**
     * 重置密码
     * 
     * @param token       校验本次请求是否有效
     * @param newPassword 新密码
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        String userId = this.validateResetPasswordToken(token);

        if (userId == null || userId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账户异常");
        }

        User user = userRepository.findById(Long.valueOf(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "账号异常"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        String text = String.format("您好 %s，\n\n您的密码已于 %s 重置成功，如不是您本人操作，请尽快联系管理员", user.getUsername(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        mailClient.send(user.getEmail(), resetPasswordSubject, text);
        // 密码重置成功，删除 Token
        redis.delete("reset:token:" + token);
    }

    /**
     * 校验 忘记密码 的Token是否正确
     * 
     * @param token 校验 用户 的Token
     * @return String - 正确返回userId，错误返回null
     */
    public String validateResetPasswordToken(String token) {

        if (token == null || token.isEmpty())
            return null;

        String userId = redis.opsForValue().get("reset:token:" + token);

        if (userId == null)
            return null;

        userRepository.findById(Long.valueOf(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "账号出错，请重试或联系管理员"));

        return userId;
    }

    private String generateTokenAndSaved(User user) {
        String token = jwtTokenUtil.generateToken(user);
        redis.opsForValue().set("reset:token:" + token, String.valueOf(user.getId()), Duration.ofMinutes(20));

        return token;
    }
}

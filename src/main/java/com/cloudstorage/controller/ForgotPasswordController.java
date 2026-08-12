package com.cloudstorage.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.RestController;

import com.cloudstorage.model.dto.Request.ForgotPasswordRequest;
import com.cloudstorage.model.dto.Request.ResetPasswordRequest;
import com.cloudstorage.model.dto.Request.SecurityQuestionRequest;
import com.cloudstorage.model.dto.Request.VerifySecurityAnswerRequest;
import com.cloudstorage.model.dto.Response.ForgotPasswordResponse;
import com.cloudstorage.model.dto.Response.SecurityQuestionResponse;
import com.cloudstorage.model.dto.Response.VerifySecurityAnswerResponse;
import com.cloudstorage.service.PasswordResetService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * ForgotPasswordController
 */
@RestController
public class ForgotPasswordController {
    private final PasswordResetService passwordResetService;

    public ForgotPasswordController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    // 查询用户是否存在
    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPasswordRequest(
            @Valid @RequestBody ForgotPasswordRequest request) {

        return ResponseEntity.ok(passwordResetService.forgotPassword(request.getEmail()));
    }

    // 邮箱验证
    @PostMapping("/forgot-password/email")
    public ResponseEntity<Void> forgotPasswordVerifyEmail(@Valid @RequestBody ForgotPasswordRequest request) {

        passwordResetService.forgotPasswordEmail(request.getEmail());

        return ResponseEntity.ok().build();
    }

    // 获取密保问题
    @PostMapping("/security-questions")
    public ResponseEntity<SecurityQuestionResponse> securityQuestions(
            @Valid @RequestBody SecurityQuestionRequest request) {

        return ResponseEntity.ok(passwordResetService.getSecurityQuestion(request.getToken()));
    }

    // 校验密保问题
    @PostMapping("/verify-answers")
    public ResponseEntity<VerifySecurityAnswerResponse> verifySecurityAnswers(
            @Valid @RequestBody VerifySecurityAnswerRequest request) {

        return ResponseEntity.ok(passwordResetService.verifySecurityAnswers(request.getToken(), request.getAnswers()));
    }

    // 验证Token
    @GetMapping("/forgot-password/validate")
    public ResponseEntity<Boolean> validateForgotToken(@RequestParam String token) {

        String userId = passwordResetService.validateResetPasswordToken(token);

        return ResponseEntity.ok(userId != null);
    }

    // 重置密码
    @PostMapping("/forgot-password/reset")
    public ResponseEntity<Void> resetForgotPassword(@Valid @RequestBody ResetPasswordRequest request) {

        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());

        return ResponseEntity.ok().build();
    }

}

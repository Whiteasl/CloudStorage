package com.cloudstorage.controller;

import com.cloudstorage.security.AuthCookieService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.cloudstorage.model.dto.Request.LoginRequest;
import com.cloudstorage.model.dto.Request.RegisterRequest;
import com.cloudstorage.model.dto.Response.AuthResponse;
import com.cloudstorage.model.dto.Response.CurrentUserResponse;
import com.cloudstorage.model.entity.User;
import com.cloudstorage.service.AuthService;
import com.cloudstorage.service.UserService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * AuthController
 */
@RestController
public class AuthController {
    private final AuthCookieService authCookieService;
    private final UserService userService;
    private final AuthService authService;

    public AuthController(UserService userService, AuthService authService, AuthCookieService authCookieService) {
        this.userService = userService;
        this.authService = authService;
        this.authCookieService = authCookieService;
    }

    /**
     * 用户注册控制器
     * 
     * @param request 注册请求体
     * @return
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.registerUser(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, authCookieService.issue(user).toString())
                .body(new AuthResponse(user.getUsername(), user.getRole()));
    }

    /**
     * 用户登录控制器
     * 
     * @param request 登录请求体
     * @return
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.login(request);

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, authCookieService.issue(user).toString())
                .body(new AuthResponse(user.getUsername(), user.getRole()));
    }

    /**
     * 查询用户登录状态请求
     * 
     * @param authentication Authentication - SpringBoot自动注入的 Filter
     * @return
     */
    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(Authentication authentication) {
        User user = userService.findById((Long) authentication.getPrincipal());

        return ResponseEntity.ok().body(new CurrentUserResponse(user.getId(), user.getUsername(), user.getRole()));
    }

    /**
     * 注销时删除前端Cookie
     * 
     * @return
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, authCookieService.expire().toString()).build();
    }

}

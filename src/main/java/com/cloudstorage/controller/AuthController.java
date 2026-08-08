package com.cloudstorage.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.cloudstorage.model.dto.Request.LoginRequest;
import com.cloudstorage.model.dto.Request.RegisterRequest;
import com.cloudstorage.model.dto.Response.AuthResponse;
import com.cloudstorage.model.entity.User;
import com.cloudstorage.service.AuthService;
import com.cloudstorage.service.UserService;
import com.cloudstorage.util.JwtTokenUtil;

import jakarta.validation.Valid;

/**
 * AuthController
 */
@RestController
public class AuthController {
    private final UserService userService;
    private final AuthService authService;
    private final JwtTokenUtil jwtTokenUtil;

    public AuthController(UserService userService, AuthService authService, JwtTokenUtil jwtTokenUtil) {
        this.userService = userService;
        this.authService = authService;
        this.jwtTokenUtil = jwtTokenUtil;
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
        String token = jwtTokenUtil.generateToken(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(user.getUsername(), token, user.getRole()));
    }

    /**
     * 用户登录控制器
     * 
     * @param request 登录请求体
     * @return
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(authService.login(request));
    }
}

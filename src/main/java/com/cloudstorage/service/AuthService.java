package com.cloudstorage.service;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.cloudstorage.model.dto.Request.LoginRequest;
import com.cloudstorage.model.dto.Response.AuthResponse;
import com.cloudstorage.model.entity.User;
import com.cloudstorage.repository.UserRepository;
import com.cloudstorage.util.JwtTokenUtil;

/**
 * AuthService
 */
@Service
public class AuthService {
    private final AuthenticationManager authenticatorManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserRepository userRepository;

    public AuthService(AuthenticationManager authenticationManager, JwtTokenUtil jwtTokenUtil,
            UserRepository userRepository) {
        this.authenticatorManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.userRepository = userRepository;
    }

    /**
     * 登录验证
     * 
     * @param request 获取请求体
     * @return 登录成功则把令牌写入 Auth 中
     * 
     */
    public AuthResponse login(LoginRequest request) {
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                request.getUsername(),
                request.getPassword());

        // 验证用户名和密码，错误会自动抛出异常，不作异常处理
        authenticatorManager.authenticate(usernamePasswordAuthenticationToken);

        // 查找用户
        Optional<User> userOptional = userRepository.findByUsername(request.getUsername());
        // 获取用户对象
        User user = userOptional.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        String token = jwtTokenUtil.generateToken(user); // 生成令牌

        // 返回一个 AuthResponse，方便 AuthController
        // 直接获取ResponseEntity
        return new AuthResponse(user.getUsername(), token, user.getRole());
    }

}

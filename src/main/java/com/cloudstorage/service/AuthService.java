package com.cloudstorage.service;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.cloudstorage.model.dto.Request.LoginRequest;
import com.cloudstorage.model.entity.User;
import com.cloudstorage.repository.UserRepository;

/**
 * AuthService
 */
@Service
public class AuthService {
    private final AuthenticationManager authenticatorManager;
    private final UserRepository userRepository;

    public AuthService(AuthenticationManager authenticationManager, UserRepository userRepository) {
        this.authenticatorManager = authenticationManager;
        this.userRepository = userRepository;
    }

    /**
     * 验证凭据，返回用户实体
     * 
     * @param request 获取请求体
     * @return User - 返回用户实体
     * 
     */
    public User login(LoginRequest request) {
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                request.getUsername(),
                request.getPassword());

        // 验证用户名和密码，错误会自动抛出异常，不作异常处理
        authenticatorManager.authenticate(usernamePasswordAuthenticationToken);

        // 查找用户
        Optional<User> userOptional = userRepository.findByUsername(request.getUsername());
        // 获取用户对象
        User user = userOptional.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        // 返回经过验证的用户实体
        // 发放 Token 交给 AuthCookieService
        return user;
    }

}

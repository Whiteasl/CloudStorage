package com.cloudstorage.service;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.cloudstorage.model.dto.RegisterRequest;
import com.cloudstorage.model.entity.User;
import com.cloudstorage.repository.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(RegisterRequest request) {
        boolean result = false; // 存储返回值

        // 从请求中获取字段
        String username = request.getUsername();
        String password = request.getPassword();
        String email = request.getEmail();

        // 检验用户名和邮箱是否重复
        result = existsByUsername(username);
        if (result) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }

        result = existsByEmail(email);
        if (result) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已被注册");
        }

        // 检验通过
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password)); // 把密码进行加密，然后再存入数据库中
        user.setEmail(email);

        return userRepository.save(user);

    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
}

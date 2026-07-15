package com.cloudstorage.service;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.cloudstorage.model.dto.RegisterRequest;
import com.cloudstorage.model.entity.User;
import com.cloudstorage.repository.UserRepository;

/**
 * UserService
 */
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户注册
     * 
     * @param request 注册请求题
     * @return 返回一个 User 实体
     */
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

    /**
     * 通过用户名寻找用户实体
     * 
     * @param username 用户名
     * @return 返回寻找到的用户实体
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * 校验邮箱是否已被注册
     * 
     * @param email 邮箱名
     * @return 邮箱存在则返回 true，反之则返回 false
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * 校验用户名是否存在
     * 
     * @param username 用户名
     * @return 存在则返回 true，反之则返回 false
     */
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
}

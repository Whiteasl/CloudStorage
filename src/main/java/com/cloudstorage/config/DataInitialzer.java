package com.cloudstorage.config;

import com.cloudstorage.service.StorageService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.cloudstorage.model.entity.User;
import com.cloudstorage.repository.UserRepository;

/**
 * DataInitializer
 */
@Component
public class DataInitialzer implements CommandLineRunner {

    private final StorageService storageService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitialzer(UserRepository userRepository, PasswordEncoder passwordEncoder,
            StorageService storageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.storageService = storageService;
    }

    @Override
    public void run(String... args) throws Exception {

        createIfNotExist("admin", "123456", "admin@admin.com", "admin");
        createIfNotExist("user", "123456", "user@user.com", "user");

    }

    /**
     * 初始化时创建用户
     * 
     * @param username 用户名
     * @param password 密码
     * @param email    邮箱
     * @param role     角色
     */
    private void createIfNotExist(String username, String password, String email, String role) {

        if (userRepository.existsByUsername(username))
            return;

        User user = new User();

        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setRole(role);
        user.setEnabled(true);

        userRepository.save(user);

        storageService.initUserDirectory(user.getId());

        System.out.println("[DataInitialzer] 创建初始用户：" + username + "( " + role + ")");
        System.out.println("[DataInitialzer] 密码：" + password);
    }
}
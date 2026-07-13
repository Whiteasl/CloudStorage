package com.cloudstorage.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import com.cloudstorage.model.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username); // 根据用户名寻找用户

    Optional<User> findByEmail(String email); // 根据邮箱寻找用户

    boolean existsByUsername(String username); // 检查用户名是否存在，存在则返回 true

    boolean existsByEmail(String email); // 检查邮箱是否被占用，占用则返回 true

    List<User> findByRole(String role); // 查找某个权限的所有用户

    Optional<User> findById(Long id); // 根据id查找用户

}

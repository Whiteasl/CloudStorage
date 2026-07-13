package com.cloudstorage.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StorageService {
    @Value("${cloudstorage.storage.root}")
    private String storagePath;

    public Path getUserPath(Long userId) {
        // 获取用户路径
        return Paths.get(storagePath, userId.toString());
    }

    public void initUserDirectory(Long userId) {
        // 先调用 getUserPath 获取用户路径
        Path userPath = getUserPath(userId);

        // 调用函数创建目录
        try {
            Files.createDirectories(userPath);
            Files.setPosixFilePermissions(userPath, PosixFilePermissions.fromString("rwx------"));
        } catch (IOException e) {
            throw new RuntimeException("用户存储目录创建失败，请联系管理员反馈错误", e);
        }
    }

    public Path validatePath(Long userId, String relativePath) {
        // 用于验证路径，确保没有目录穿越漏洞
        Path userDir = getUserPath(userId);
        Path target = userDir.resolve(relativePath).normalize();

        // 验证传入的路径是否在用户根目录下
        if (!target.startsWith(userDir))
            throw new RuntimeException("路径不存在，请检查文件是否正确，或联系管理员报告Bug");

        // 返回一个文件路径，从用户根目录开始
        return target;

    }

    public void removeExecutePermission(Path path) {
        // 对传入的文件去除执行权限，如果传入的路径是目录，则不做处理/重写权限
        try {
            if (Files.isDirectory(path))
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
            else
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (IOException e) {
            throw new RuntimeException("文件权限错误");
        }
    }

}
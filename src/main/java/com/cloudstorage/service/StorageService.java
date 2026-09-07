package com.cloudstorage.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.cloudstorage.model.entity.UserFile;
import com.cloudstorage.repository.UserFileRepository;

/**
 * StorageService
 */
@Service
public class StorageService {
    private final UserFileRepository userFileRepository;

    @Value("${cloudstorage.storage.root}")
    private String storagePath;

    public StorageService(UserFileRepository userFileRepository) {
        this.userFileRepository = userFileRepository;
    }

    /**
     * 获取用户路径
     * 
     * @param userId 用户ID
     * @return 返回用户路径
     */
    public Path getUserPath(Long userId) {

        return Paths.get(storagePath, userId.toString());
    }

    /**
     * 初始化用户路径，生成用户目录。只在用户注册时使用一次
     * 
     * @param userId 用户ID
     */
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

    /**
     * 验证路径，清除目录穿越漏洞
     * 
     * @param userId       用户ID
     * @param relativePath 真实路径
     * @return 返回全路径（从用户目录开始）
     */
    public Path validatePath(Long userId, String relativePath) {
        // 用于验证路径，确保没有目录穿越漏洞
        Path userDir = getUserPath(userId).normalize();
        Path target = userDir.resolve(relativePath).normalize();

        // 验证传入的路径是否在用户根目录下
        if (!target.startsWith(userDir))
            throw new RuntimeException("路径不存在");

        // 返回一个文件路径，从用户根目录开始
        return target;

    }

    /**
     * 获取文件的逻辑路径
     * 
     * @param fileId  文件ID
     * @param ownerId 文件拥有者ID
     * @return String - 文件的逻辑路径，未进行路径穿越检查，需要谨慎使用
     */
    public String resolveLogicalPath(Long fileId, Long ownerId) {
        UserFile uf = userFileRepository.findByIdAndOwner_Id(fileId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "未找到文件"));

        List<Long> visited = new ArrayList<>();

        ArrayDeque<String> realPath = new ArrayDeque<>();

        while (true) {
            if (visited.size() > 100) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "文件树异常：目录层级过深");
            }

            if (visited.contains(uf.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "文件树异常：目录环错误");
            }

            realPath.addFirst(uf.getFilename());
            visited.add(uf.getId());

            if (uf.getParentFolderId() == null || uf.getParentFolderId() == 0) {
                break;
            }

            uf = userFileRepository.findByIdAndOwner_Id(uf.getParentFolderId(), ownerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "文件树异常：父目录不存在"));

            if (!uf.isFolder()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "文件树异常：父目录是文件");
            }

        }

        return String.join("/", realPath);
    }

    /**
     * 把文件 ID 解析成经过校验的磁盘路径
     * 
     * @param fileId  文件ID
     * @param ownerId 文件所有者ID
     * @return Path - 返回经过校验的文件路径，极大程度避免了路径穿越
     */
    public Path resolveRealPath(Long fileId, Long ownerId) {
        return validatePath(ownerId, resolveLogicalPath(fileId, ownerId));
    }

    /**
     * 对文件去除执行权限
     * 
     * @param path 文件路径
     */
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
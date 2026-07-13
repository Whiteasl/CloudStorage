package com.cloudstorage.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.cloudstorage.model.entity.User;
import com.cloudstorage.model.entity.UserFile;
import com.cloudstorage.repository.UserFileRepository;
import com.cloudstorage.repository.UserRepository;

import jakarta.transaction.Transactional;

@Service
public class FileService {
    private final StorageService storageService;
    private final UserFileRepository userFileRepository;
    private final UserRepository userRepository;

    public FileService(StorageService storageService, UserFileRepository userFileRepository,
            UserRepository userRepository) {
        this.storageService = storageService;
        this.userFileRepository = userFileRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void uploadFile(MultipartFile file, Long userId, Long parentFolderId) {

        // 管理文件上传功能

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到用户，请联系管理员解决"));
        Long storageUsed = owner.getStorageUsed(); // 获取用户已用配额

        // 获取上传文件的相对路径，便于后续存储到数据库中
        String relativePath;

        if (parentFolderId == null)
            // 如果处于根目录中，则直接存储其文件名
            relativePath = file.getOriginalFilename();
        else {
            UserFile parentFolder = userFileRepository.findByIdAndOwner(parentFolderId, owner)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "父目录不存在"));
            relativePath = parentFolder.getFilePath() + "/" + file.getOriginalFilename();
        }

        Path diskPath = storageService.validatePath(userId, relativePath);

        // 分别检查配额问题和命名问题
        if (file.getSize() + storageUsed > owner.getStorageQuota())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户配额不足");

        if (userFileRepository.existsByFilenameAndOwnerAndParentFolderId(file.getOriginalFilename(), owner,
                parentFolderId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目录下已存在同名");

        try {
            UserFile uf = new UserFile();

            uf.setFilename(file.getOriginalFilename());
            uf.setFileSize(file.getSize());
            uf.setFilePath(relativePath);
            uf.setOwner(owner);
            uf.setParentFolderId(parentFolderId);
            uf.setContentType(file.getContentType());
            // 更新用户配额已使用量
            owner.setStorageUsed(owner.getStorageUsed() + file.getSize());

            userFileRepository.save(uf);
            userRepository.save(owner);

            Files.copy(file.getInputStream(), diskPath);

            storageService.removeExecutePermission(diskPath);

        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件上传失败");
        }

    }

    public FileSystemResource downloadFile(Long fileId, Long userId) {
        // 用于管理下载文件的功能

        // 获取文件实体
        UserFile uf = userFileRepository
                .findByIdAndOwner(fileId,
                        userRepository.findById(userId) // 获取用户实体
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到用户")))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到文件")); // 获取文件实体

        Path diskPath = storageService.validatePath(userId, uf.getFilePath()); // 转换成磁盘路径

        // 不允许下载文件夹
        // 后续实现文件夹打包成压缩包下载的功能
        if (uf.isFolder())
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "文件夹不允许被下载");

        return new FileSystemResource(diskPath);
    }

    @Transactional
    public void createFolder(String folderName, Long userId, Long parentFolderId) {
        // 创建目录功能

        // 创建用户实体
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "请检查用户是否存在"));

        // 获取父目录路径
        String relativePath;
        if (parentFolderId == null)
            relativePath = folderName;

        else {
            UserFile parentFolder = userFileRepository.findByIdAndOwner(parentFolderId, owner)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "父目录不存在，请检查路径地址是否正确"));
            relativePath = parentFolder.getFilePath() + "/" + folderName;
        }

        // 创建 UserFile 实体，并进行填充
        UserFile uf = new UserFile();

        uf.setFilename(folderName);
        uf.setOwner(owner);
        uf.setFileSize(0);
        uf.setFilePath(relativePath);
        uf.setFolder(true);
        uf.setParentFolderId(parentFolderId);

        if (userFileRepository.existsByFilenameAndOwnerAndParentFolderId(folderName, owner, parentFolderId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "创建失败：该目录下已有同名目录");

        userFileRepository.save(uf);

        // 创建真实目录
        try {
            Files.createDirectory(storageService.validatePath(userId, relativePath));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "目录创建失败");
        }

    }

    public List<UserFile> listFiles(Long userId, Long parentFolderId) {
        // 获取目录下所有文件和目录
        return userFileRepository.findByOwnerAndParentFolderId(userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到用户")), parentFolderId);
    }

    @Transactional
    public void deleteFile(Long fileId, Long userId) {
        // 删除文件功能

        // 检验文件归属
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "请检查文件归属者是否正确"));
        UserFile uf = userFileRepository.findByIdAndOwner(fileId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到文件，请检查文件是否正确"));

        // 检查目录下是否有文件：非空目录不允许删除
        // 暂时没有实现递归删除目录的功能，先进行记录，后续进行实现
        if (uf.isFolder() && !userFileRepository.findByOwnerAndParentFolderId(owner, uf.getId()).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目录不为空，请先删除子文件");

        try {
            Files.delete(storageService.validatePath(userId, uf.getFilePath()));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件删除失败");
        }

        // 设置用户配额
        owner.setStorageUsed(owner.getStorageUsed() - uf.getFileSize());
        userRepository.save(owner);

        // 删除数据库中的文件索引
        userFileRepository.deleteByIdAndOwner(fileId, owner);
    }

    @Transactional
    public void renameFile(Long fileId, Long userId, String newName) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到目标用户"));
        UserFile uf = userFileRepository.findByIdAndOwner(fileId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到目标文件"));

        // 检查重名
        if (userFileRepository.existsByFilenameAndOwnerAndParentFolderId(newName, owner, uf.getParentFolderId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目录下已有同名文件");

        // 对文件进行重命名
        if (!uf.isFolder()) {
            String oldPath = uf.getFilePath();
            int slash = oldPath.lastIndexOf('/');
            String newPath = slash == -1 ? newName : oldPath.substring(0, slash + 1) + newName; // 检查文件是否在根目录下

            uf.setFilePath(newPath);
            uf.setFilename(newName);

            userFileRepository.save(uf);

            try {
                Files.move(storageService.validatePath(userId, oldPath), storageService.validatePath(userId, newPath));
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "重命名失败");
            }
        } else {
            // 文件夹重命名
            // 文件夹重命名较为繁琐
            // 重命名文件夹的方法有两种：
            // 1、全路径存储（当期方案）：在数据库中存储文件的全路径。对文件夹重命名时需要遍历所有子文件，对子文件路径逐个修改
            // 2、路径拼接：不在数据库中存储文件的全路径，路径通过 parentFolderId 进行拼接，有多少个 parentFolderId
            // 就拼接多少个目录，直到 null

            String oldPath = uf.getFilePath(); // 获取文件路径
            int slash = oldPath.lastIndexOf('/');
            String newPath = slash == -1 ? newName : oldPath.substring(0, slash + 1) + newName; // 定义新路径

            uf.setFilename(newName);
            uf.setFilePath(newPath);
            userFileRepository.save(uf);

            try {
                Files.move(storageService.validatePath(userId, oldPath),
                        storageService.validatePath(userId, newPath));
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "重命名失败");
            }

            // 递归收集所有子文件
            List<UserFile> allFiles = new ArrayList<>();
            collectSubdirectories(owner, uf.getId(), allFiles);

            // 递归重命名子文件的路径
            for (UserFile child : allFiles) {
                String childOldPath = child.getFilePath(); // 获取旧路径
                String childNewPath = newPath + childOldPath.substring(oldPath.length()); // 获取新路径
                child.setFilePath(childNewPath); // 设置新路径
                userFileRepository.save(child);

            }

        }
    }

    public void moveFile(Long fileId, Long userId, Long targetFolderId) {
        // 实现 移动文件 功能
        // 需注意，不能把文件移动到自己的子目录中

        
    }

    private void collectSubdirectories(User owner, Long folderId, List<UserFile> allFiles) {
        // 帮助重命名方法获取目录下的所有子文件/目录
        // 获取所有子文件
        // 通过递归获取所有子目录

        List<UserFile> children = userFileRepository.findByOwnerAndParentFolderId(owner, folderId);
        for (UserFile child : children) {
            allFiles.add(child);
            if (child.isFolder()) {
                collectSubdirectories(owner, child.getId(), allFiles);
            }
        }

    }

}

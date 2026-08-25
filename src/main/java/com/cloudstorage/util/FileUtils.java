package com.cloudstorage.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.cloudstorage.model.entity.User;
import com.cloudstorage.model.entity.UserFile;
import com.cloudstorage.repository.UserFileRepository;
import com.cloudstorage.repository.UserRepository;
import com.cloudstorage.service.StorageService;

/**
 * FileUtils
 */
@Component
public class FileUtils {

    private final UserFileRepository userFileRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public FileUtils(UserFileRepository userFileRepository, UserRepository userRepository,
            StorageService storageService, FileUtils fileUtils) {
        this.userFileRepository = userFileRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    /**
     * 压缩文件
     * 
     * @param fileIds     文件ID集合
     * @param userId      所有者ID
     * @param archiveName 压缩文件名字
     * @return 成功则返回一个 FileSystemResource 对象
     */
    public FileSystemResource compressFiles(Set<Long> fileIds, Long userId, String archiveName) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "账户异常：未找到账户"));
        List<UserFile> userFiles = getFilesByIds(fileIds, owner);

        Path tempZip;
        try {
            // 创建临时文件，用于后续存储压缩文件
            tempZip = Files.createTempFile(archiveName, ".zip");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "临时文件创建错误，请联系管理员处理");
        }

        // 开始对文件进行压缩，生成压缩包
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {

            for (UserFile uf : userFiles) {
                // 压缩目录
                if (uf.isFolder()) {
                    // 获取文件夹逻辑路径
                    ZipEntry dirEntry = new ZipEntry(
                            storageService.resolveLogicalPath(uf.getId(), userId) + "/");
                    zos.putNextEntry(dirEntry);
                    zos.closeEntry();

                    List<UserFile> children = new ArrayList<>();

                    // 获取所有子目录
                    collectSubdirectories(owner, uf.getId(), children);

                    for (UserFile child : children) {
                        if (!child.isFolder()) {
                            // 子文件是文件时
                            // 获取文件的磁盘路径
                            String logical = storageService.resolveLogicalPath(child.getId(), userId);
                            Path diskPath = storageService.validatePath(userId, logical);
                            // 添加到压缩文件中
                            ZipEntry entry = new ZipEntry(logical);
                            zos.putNextEntry(entry);
                            Files.copy(diskPath, zos);
                            zos.closeEntry();
                        }
                    }

                } else {
                    // 压缩对象是文件时

                    Path diskPath = storageService.resolveRealPath(uf.getId(), userId);
                    ZipEntry entry = new ZipEntry(storageService.resolveLogicalPath(uf.getId(), userId));
                    zos.putNextEntry(entry);
                    Files.copy(diskPath, zos);
                    zos.closeEntry();
                }
            }

        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "压缩失败");
        }

        return new FileSystemResource(tempZip);
    }

    /**
     * 从文件ID获取文件实体
     * 
     * @param fileIds 文件ID集合
     * @param owner   文件拥有者
     * @return 成功则返回一个存储 UserFile 实体的列表
     */
    private List<UserFile> getFilesByIds(Set<Long> fileIds, User owner) {

        List<UserFile> userFiles = new ArrayList<>();

        for (Long id : fileIds) {

            userFiles.add(userFileRepository.findByIdAndOwner(id, owner)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件异常：有一个或多个文件未找到")));
        }

        return userFiles;
    }

    /**
     * 通过递归获取目录下的所有子文件/目录
     * 
     * @param owner    User - 目录拥有者
     * @param folderId Long - 目录ID
     * @param files    List<UserFile> - 收集文件的列表
     */
    public void collectSubdirectories(User owner, Long folderId, List<UserFile> files) {

        List<UserFile> children = userFileRepository.findByOwnerAndParentFolderId(owner, folderId);
        for (UserFile child : children) {
            files.add(child);
            if (child.isFolder()) {
                collectSubdirectories(owner, child.getId(), files);
            }
        }

    }

    /**
     * 递归删除
     * 
     * @param folderId Path - 需要被删除的文件夹
     * @return boolean - 删除失败时返回 false
     * @throws IOException - Files.delete 的异常处理
     */
    public void deleteRecursively(Path delDir) throws IOException {

        if (Files.exists(delDir)) {
            Files.walkFileTree(delDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

    }

    // /**
    // * 辅助方法，用于检测文件夹是否为空文件夹
    // *
    // * @param checkDir Path - 待检测文件夹
    // * @return boolean - 空文件夹返回true
    // * @throws IOException 文件不存在，抛出 IOException，交给上层处理
    // */
    // private boolean isEmptyDir(Path checkDir) throws IOException {
    // if (Files.exists(checkDir)) {
    // try (DirectoryStream<Path> stream = Files.newDirectoryStream(checkDir)) {
    // return !stream.iterator().hasNext();
    // }
    // } else {
    // throw new IOException();
    // }

    // }
}

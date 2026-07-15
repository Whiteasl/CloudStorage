package com.cloudstorage.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
            StorageService storageService) {
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
            tempZip = Files.createTempFile(archiveName, ".zip");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "临时文件创建错误，请联系管理员处理");
        }

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {

            for (UserFile uf : userFiles) {
                // 压缩目录
                if (uf.isFolder()) {
                    ZipEntry dirEntry = new ZipEntry(uf.getFilePath() + "/");
                    zos.putNextEntry(dirEntry);
                    zos.closeEntry();

                    List<UserFile> children = new ArrayList<>();

                    // 获取所有子目录
                    collectSubdirectories(owner, uf.getId(), children);

                    for (UserFile child : children) {
                        if (!child.isFolder()) {

                            Path diskPath = storageService.validatePath(userId, child.getFilePath());
                            ZipEntry entry = new ZipEntry(child.getFilePath());
                            zos.putNextEntry(entry);
                            Files.copy(diskPath, zos);
                            zos.closeEntry();
                        }
                    }

                } else {
                    // 压缩文件

                    Path diskPath = storageService.validatePath(userId, uf.getFilePath());
                    ZipEntry entry = new ZipEntry(uf.getFilePath());
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
     * @param owner    目录拥有者
     * @param folderId 目录ID
     * @param allFiles 传入的列表
     */
    public void collectSubdirectories(User owner, Long folderId, List<UserFile> allFiles) {

        List<UserFile> children = userFileRepository.findByOwnerAndParentFolderId(owner, folderId);
        for (UserFile child : children) {
            allFiles.add(child);
            if (child.isFolder()) {
                collectSubdirectories(owner, child.getId(), allFiles);
            }
        }

    }
}

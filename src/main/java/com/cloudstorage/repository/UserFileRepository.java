package com.cloudstorage.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cloudstorage.model.entity.User;
import com.cloudstorage.model.entity.UserFile;

public interface UserFileRepository extends JpaRepository<UserFile, Long> {
    Optional<UserFile> findByIdAndOwner(Long id, User owner); // 根据 文件ID 和 文件所有者 寻找文件

    List<UserFile> findByOwnerAndParentFolderId(User owner, Long parentFolderId); // 根据 文件所有者 和 父目录id 寻找文件/目录

    // 帮助前端实现目录导航 获取用户在某个文件夹下的所有文件和目录
    // parentFolderId = null -> 用户当前处于根目录
    // parentFolderId = 5 -> 列出目录等级为 5 的目录下的所有文件和目录
    List<UserFile> findAllByIdInAndOwner(List<Long> ids, User owner);

    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM UserFile f WHERE f.owner = :owner")
    Long sumFileSizeByOwner(@Param("owner") User owner); // 用户配额检查

    void deleteByIdAndOwner(Long id, User owner); // 安全删除 - 确保只删除当前用户的文件

    List<UserFile> findByOwnerAndIsFolderTrue(User owner); // 获取当前用户所有文件夹，构建目录树

    Long countByOwner(User owner); // 统计用户文件总数 辅助配额管理

    boolean existsByFilenameAndOwnerAndParentFolderId(String fileName, User owner, Long parentFolderId); // 防止文件重名（仅限当前用户）

    List<UserFile> findByOwnerAndFilenameContaining(User owner, String fileName); // 文件名模糊搜索

    List<UserFile> findByOwnerAndParentFolderIdAndIsFolder(User owner, Long parentFolderId, boolean isFolder); // 导航时区分文件/目录

}
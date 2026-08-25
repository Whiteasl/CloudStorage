package com.cloudstorage.service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.cloudstorage.model.entity.FileAnomaly;
import com.cloudstorage.model.entity.User;
import com.cloudstorage.model.entity.UserFile;
import com.cloudstorage.model.enums.AnomalyType;
import com.cloudstorage.repository.FileAnomalyRepository;
import com.cloudstorage.repository.ShareLinkRepository;
import com.cloudstorage.repository.UserFileRepository;
import com.cloudstorage.repository.UserRepository;
import com.cloudstorage.util.FileUtils;

/**
 * FileService
 */
@Service
public class FileService {
    private final StorageService storageService;
    private final UserFileRepository userFileRepository;
    private final UserRepository userRepository;
    private final FileUtils fileUtil;
    private final PlatformTransactionManager platformTransactionManager;
    private final ShareLinkRepository shareLinkRepository;
    private final FileAnomalyRepository fileAnomalyRepository;
    private final static Logger log = LoggerFactory.getLogger(FileService.class);
    private final TransactionTemplate transactionTemplate;

    private final static Long ROOT_FOLDER_ID = 0L;

    public FileService(StorageService storageService, UserFileRepository userFileRepository,
            UserRepository userRepository, FileUtils fileUtil, PlatformTransactionManager platformTransactionManager,
            ShareLinkRepository shareLinkRepository, FileAnomalyRepository fileAnomalyRepository) {
        this.storageService = storageService;
        this.userFileRepository = userFileRepository;
        this.userRepository = userRepository;
        this.fileUtil = fileUtil;
        this.platformTransactionManager = platformTransactionManager;
        this.shareLinkRepository = shareLinkRepository;
        this.fileAnomalyRepository = fileAnomalyRepository;
        this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
    }

    /**
     * 上传文件 业务代码
     * 顺序：查用户 -> 检查文件夹 -> 配额检查 -> 同名检查 -> 逻辑路径检查
     * 
     * @param file           MultipartFile - 上传的文件数据
     * @param userId         Long - 用户ID
     * @param parentFolderId Long - 存储上传文件的文件夹ID
     * 
     */
    public void uploadFile(MultipartFile file, Long userId, Long parentFolderId) {

        // 查用户
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到用户，请联系管理员解决"));
        Long storageUsed = owner.getStorageUsed(); // 获取用户已用配额

        // 规范化文件夹ID 并对文件夹进行检查
        final Long safeParent = normalizeParentId(parentFolderId);
        if (safeParent != 0) {
            ensureParentFolder(owner, safeParent);
        }

        // 分别检查配额问题和命名问题
        if (file.getSize() + storageUsed > owner.getStorageQuota())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户配额不足");

        if (userFileRepository.existsByFilenameAndOwnerAndParentFolderId(file.getOriginalFilename(), owner,
                safeParent))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目录下已存在同名");

        // 路径构造，分为 临时存储路径 和 存储路径
        // 存储路径 - 逻辑路径
        String logical = safeParent == 0L ? file.getOriginalFilename()
                : storageService.resolveLogicalPath(safeParent, userId) + "/" + file.getOriginalFilename();

        // 存储路径 - 通过逻辑路径推导出的 Path
        Path diskPath = storageService.validatePath(userId, logical);

        // 上传文件时用的同路径下的临时文件名
        Path tempPath = diskPath.resolveSibling(".tmp---" + UUID.randomUUID().toString() + ".tmp");

        // 磁盘操作
        try {
            // 把上传流接入临时文件
            Files.copy(file.getInputStream(), tempPath);

            try {
                // 对临时文件进行原子改名
                Files.move(tempPath, diskPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // 文件系统不支持原子改名时抛出错误
                // 修改为：普通改名操作
                Files.move(tempPath, diskPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // 上传失败
            // 删除临时文件并抛出 500 错误
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
                // 清理失败，遗留文件不再管理，避免出现递归错误
                // 交由开机检查或是下次上传直接覆盖

                log.warn("[*] Upload: Failed to clear up residual file: " + ignored.getMessage());

            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件上传失败");
        }

        // 去权限步骤
        try {
            storageService.removeExecutePermission(diskPath);
        } catch (RuntimeException e) {
            // 去权限操作失败
            // 删除文件并抛出 500 错误
            try {
                Files.deleteIfExists(diskPath);
            } catch (IOException ignored) {
                // 清理失败，遗留文件不再管理，避免出现递归错误
                // 交由开机检查或是下次上传直接覆盖
                log.warn("[*] Upload: Failed to clear up residual file: " + ignored.getMessage());

            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件上传失败");
        }

        // 更新数据库数据
        try {

            this.transactionTemplate.executeWithoutResult(status -> {
                UserFile uf = new UserFile();
                // 文件信息更新
                uf.setFilename(file.getOriginalFilename());
                uf.setContentType(file.getContentType());
                uf.setFileSize(file.getSize());
                uf.setOwner(owner);
                uf.setParentFolderId(safeParent);

                userFileRepository.save(uf);

                // 用户配额信息更新
                owner.setStorageUsed(storageUsed + file.getSize());

                userRepository.save(owner);

            });
        } catch (DataIntegrityViolationException e) {
            // 并发竞态唯一约束触发
            // 删除文件，抛出409错误
            try {
                Files.deleteIfExists(diskPath);
            } catch (IOException ignored) {
                // 清理失败，遗留文件不再管理，避免出现递归错误
                // 交由开机检查或是下次上传直接覆盖
                log.warn("[*] Upload: Failed to clear up residual file: " + ignored.getMessage());

            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目录下已存在同名文件");
        } catch (RuntimeException e) {
            // 数据库更新失败
            // 删除文件，抛出 500 错误
            try {
                Files.deleteIfExists(diskPath);
            } catch (IOException ignored) {
                // 清理失败，不再管理遗留文件，避免出现递归错误
                // 交由开机检查或是下次上传直接覆盖
                log.warn("[*] Upload: Failed to clear up residual file: " + ignored.getMessage());
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件上传失败");
        }

    }

    /**
     * 下载文件 业务
     * 
     * @param fileId Long - 被下载的文件的ID
     * @param userId Long - 文件所有者ID
     * @return FileSystemResource 返回一个文件内容流
     */

    public FileSystemResource downloadFile(Long fileId, Long userId) {

        // 获取文件实体
        UserFile uf = userFileRepository
                .findByIdAndOwner(fileId,
                        userRepository.findById(userId) // 获取用户实体
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到用户")))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到文件")); // 获取文件实体

        // 不允许下载文件夹
        // 后续实现文件夹打包成压缩包下载的功能
        if (uf.isFolder())
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "文件夹不允许被下载");

        // Path diskPath = storageService.validatePath(userId, uf.getFilePath());
        // 转换成磁盘路径
        Path diskPath = storageService.resolveRealPath(fileId, userId);

        return new FileSystemResource(diskPath);
    }

    /**
     * 创建目录功能
     * 
     * @param folderName     String - 目录名
     * @param userId         Long - 所有者ID
     * @param parentFolderId Long - 目录所在目录的ID
     */
    public void createFolder(String folderName, Long userId, Long parentFolderId) {

        // 获取用户实体
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "请检查用户是否存在"));

        // 文件路径 - 逻辑路径和磁盘路径
        String logical;
        Path diskPath;

        // 规范化文件夹ID
        Long checkedParentId = normalizeParentId(parentFolderId);
        if (checkedParentId != 0L)
            ensureParentFolder(owner, checkedParentId);

        // 同名检查
        if (userFileRepository.existsByFilenameAndOwnerAndParentFolderId(folderName, owner, checkedParentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文件夹下已有同名文件夹");
        }

        // 路径处理
        logical = checkedParentId == 0L ? folderName
                : storageService.resolveLogicalPath(checkedParentId, userId) + "/" + folderName;
        diskPath = storageService.validatePath(userId, logical);

        // 创建文件夹
        try {
            Files.createDirectory(diskPath);
        } catch (FileAlreadyExistsException e) {
            recordAnomaly(owner, AnomalyType.ORPHAN, null, logical, AnomalyType.ORPHAN.getDescription() + "同名目录残留");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文件夹中存在同名残留，请联系管理员处理");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件夹创建失败");
        }

        // 更新数据库
        try {
            transactionTemplate.executeWithoutResult(status -> {
                UserFile uf = new UserFile();
                uf.setFilename(folderName);
                uf.setParentFolderId(checkedParentId);
                uf.setFileSize(0);
                uf.setOwner(owner);
                uf.setFolder(true);

                userFileRepository.save(uf);
            });
        } catch (DataIntegrityViolationException e) {
            try {
                Files.deleteIfExists(diskPath);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "文件夹下已有同名文件夹");

            } catch (IOException ignored) {
                // 无法删除，直接不管
                log.warn("[*] createFolder: Failed to clean up residual directory: " + e.getMessage());
            }
        } catch (RuntimeException e) {
            try {
                Files.deleteIfExists(diskPath);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件夹创建失败");

            } catch (IOException ignored) {
                // 无法删除，直接不管
                log.warn("[*] createFolder: Failed to clean up residual directory: " + e.getMessage());
            }
        }

    }

    /**
     * 获取目录下的所有文件和文件夹
     * 
     * @param userId         Long - 目录所有者ID
     * @param parentFolderId Long - 目录ID
     * @return List<UserFile> 返回一个包含 UserFile 实体的列表
     */
    public List<UserFile> listFiles(Long userId, Long parentFolderId) {

        // 获取用户实体
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到用户"));

        // 规范化文件夹ID
        Long checkedParentId = normalizeParentId(parentFolderId);

        if (checkedParentId != 0L)
            // 检查文件夹合理性
            ensureParentFolder(owner, checkedParentId);

        return userFileRepository.findByOwnerAndParentFolderId(owner, checkedParentId);
    }

    /**
     * 删除文件
     * 
     * @param fileId Long - 文件ID
     * @param userId Long - 文件所有者ID
     */
    public void deleteFile(Long fileId, Long userId) {

        // 检验文件归属 并 获取用户实体和文件实体
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "请检查文件归属者是否正确"));
        UserFile uf = userFileRepository.findByIdAndOwner(fileId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到文件，请检查文件是否正确"));

        // 收集文件夹中的子文件数量
        List<UserFile> files = new ArrayList<>();

        if (uf.isFolder()) {

            fileUtil.collectSubdirectories(owner, fileId, files);
            files.add(uf);

        } else {
            files.add(uf);
        }

        // 路径处理
        String logical = storageService.resolveLogicalPath(fileId, userId);
        Path diskPath = storageService.validatePath(userId, logical);

        // 统计被释放的存储空间
        Long totalSize = 0L;

        for (UserFile file : files) {
            totalSize += file.getFileSize();
        }

        final Long finalSize = totalSize;

        // 更新数据库
        try {
            transactionTemplate.executeWithoutResult(status -> {
                shareLinkRepository.deleteByShareFileIn(files);
                userFileRepository.deleteAll(files);

                owner.setStorageUsed(owner.getStorageUsed() - finalSize);
                userRepository.save(owner);
            });
        } catch (DataIntegrityViolationException e) {
            // 因为磁盘文件没有动，所以不需要额外操作
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文件被其他数据引用，无法删除");
        } catch (RuntimeException e) {
            // 因为磁盘文件没有动，所以不需要额外操作
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件删除失败");
        }

        // 删除文件
        if (uf.isFolder()) {
            try {
                fileUtil.deleteRecursively(diskPath);
            } catch (IOException e) {
                recordAnomaly(owner, AnomalyType.CLEANUP_FAILED, null, logical,
                        AnomalyType.CLEANUP_FAILED.getDescription() + e.getMessage());
            }
        } else {
            try {
                Files.deleteIfExists(diskPath);
            } catch (IOException e) {
                recordAnomaly(owner, AnomalyType.CLEANUP_FAILED, null, logical,
                        AnomalyType.CLEANUP_FAILED.getDescription() + e.getMessage());
            }
        }

    }

    /**
     * 重命名
     * 
     * @param fileId  Long - 文件ID
     * @param userId  Long - 文件所有者ID
     * @param newName String - 新名字
     */
    @Transactional
    public void renameFile(Long fileId, Long userId, String newName) {
        /**
         * 重命名
         * 把 指定文件/目录 重命名
         * 
         * @param fileId  指定文件/目录 ID
         * @param userId  用户 ID
         * @param newName 新名字
         */
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
            fileUtil.collectSubdirectories(owner, uf.getId(), allFiles);

            // 递归重命名子文件的路径
            for (UserFile child : allFiles) {
                String childOldPath = child.getFilePath(); // 获取旧路径
                String childNewPath = newPath + childOldPath.substring(oldPath.length()); // 获取新路径
                child.setFilePath(childNewPath); // 设置新路径
                userFileRepository.save(child);

            }

        }
    }

    /**
     * 移动文件
     * 
     * @param sourceId       源文件ID
     * @param userId         文件所有者ID
     * @param targetFolderId 目标目录的ID
     */
    @Transactional
    public void moveFile(Long sourceId, Long userId, Long targetFolderId) {

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到目标用户"));
        UserFile source = userFileRepository.findByIdAndOwner(sourceId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到文件"));

        String relativePath;
        if (targetFolderId != null) {
            // 目标目录归属校验
            UserFile targetFolder = userFileRepository.findByIdAndOwner(targetFolderId, owner)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到目标文件夹"));

            // 定义真实磁盘路径
            relativePath = targetFolder.getFilePath() + "/" + source.getFilename();
        } else {
            relativePath = source.getFilename();
        }

        if (userFileRepository.existsByFilenameAndOwnerAndParentFolderId(source.getFilename(), owner, targetFolderId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标目录下有同名文件/目录");

        if (source.isFolder() && targetFolderId != null && isDescendantOf(owner, sourceId, targetFolderId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不能移动到子目录中");

        // 递归更新子文件
        if (source.isFolder()) {
            List<UserFile> allChild = new ArrayList<>();
            fileUtil.collectSubdirectories(owner, sourceId, allChild);

            String oldPath = source.getFilePath();

            for (UserFile child : allChild) {
                String newPath = relativePath + child.getFilePath().substring(oldPath.length());
                child.setFilePath(newPath);
                userFileRepository.save(child);
            }
        }
        String oldPath = source.getFilePath();
        // 更新数据库信息
        source.setParentFolderId(targetFolderId);
        source.setFilePath(relativePath);
        userFileRepository.save(source);

        try {
            // 移动真实文件
            Files.move(storageService.validatePath(userId, oldPath),
                    storageService.validatePath(userId, relativePath));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "移动文件失败");
        }

    }

    /**
     * 获取文件元数据
     * 
     * @param userId // 用户 ID
     * @param fileId // 文件 ID
     */

    public UserFile getFileDetail(Long fileId, Long userId) {
        /**
         * 获取文件元数据
         * 例如：
         * 文件名：text.txt
         * 大小：10 KB
         * 类型：text/plain
         * 创建时间：2026-09-29 13:00
         * 修改时间：2026-09-30 12:00
         */

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到用户"));
        UserFile uf = userFileRepository.findByIdAndOwner(fileId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到文件"));

        return uf;

    }

    /**
     * 搜索文件
     * 
     * @param userId  所有者ID
     * @param keyword 搜索的字符
     */
    public List<UserFile> searchFiles(Long userId, String keyword) {

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到用户"));
        return userFileRepository.findByOwnerAndFilenameContaining(owner, keyword);
    }

    /**
     * 批量删除
     * 
     * @param ids     需要被删除的文件ID
     * @param userId: 拥有者ID
     */
    @Transactional
    public void batchDelete(Set<Long> ids, Long userId) {

        for (Long fileId : ids) {
            this.deleteFile(fileId, userId);
        }

    }

    /**
     * 检查目标目录是否为被移动目录的子目录
     * 
     * @param owner          当前用户
     * @param folderId       被移动目录的id
     * @param targetFolderId 目标目录的id
     *                       return 如果目标目录是被移动目录的子目录则返回 true(会形成死循环)，false(不会形成死循环)
     **/
    private boolean isDescendantOf(User owner, Long folderId, Long targetFolderId) {
        /**
         * 检查目标目录是否为被移动目录的子目录
         * 从 targetFolder 往上追溯 parentFolder 链
         * 如果遇到了 folder 就说明目标目录是被移动目录的子目录
         */

        Long currentFolder = targetFolderId;
        while (currentFolder != null) {
            if (currentFolder.equals(folderId))
                return true;

            UserFile nextFolder = userFileRepository.findByIdAndOwner(currentFolder, owner).orElse(null);
            if (nextFolder == null)
                break;
            currentFolder = nextFolder.getParentFolderId();
        }

        return false;

    }

    /**
     * 规范化用户根目录
     * 
     * @param parentId Long - 父目录ID
     * @return Long - 规范化后的父目录ID
     */
    private Long normalizeParentId(Long parentId) {
        if (parentId == null) {
            return ROOT_FOLDER_ID;
        }
        return parentId;
    }

    /**
     * 确保节点是文件夹
     * 
     * @param owner    User - 文件所有者
     * @param folderId Long - 文件夹ID
     * @return boolean - 只有正确时返回 true, 其他情况抛出400错误
     */
    private boolean ensureParentFolder(User owner, Long folderId) {
        if (!userFileRepository.findByIdAndOwner(folderId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "节点不存在")).isFolder())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该节点不是文件夹");

        return true;
    }

    /**
     * 删除旧路径(remove move 的辅助检查函数)
     * 
     * @param oldPath Path - 需要被删除的文件夹ID
     */
    private void sweepOldPath(Path oldDiskPath, User owner, String logicalPath) {

        // 检测改路径是否存在，避免网络延迟导致的错误信息
        if (Files.exists(oldDiskPath)) {
            try {
                fileUtil.deleteRecursively(oldDiskPath);

            } catch (IOException e) {
                // 删除失败，归纳到异常文件中
                recordAnomaly(owner, AnomalyType.CLEANUP_FAILED, null, logicalPath,
                        AnomalyType.CLEANUP_FAILED.getDescription() + logicalPath);
            }
        }
    }

    /**
     * 记录异常文件的辅助函数
     * 
     * @param fileId  Long - 异常的文件ID
     * @param ownerId Long - 异常文件的拥有者ID
     */
    private void recordAnomaly(User owner, AnomalyType type, Long fileId, String logicalPath, String description) {

        FileAnomaly anomaly = new FileAnomaly();
        try {
            anomaly.setAnomalyType(type);
            anomaly.setDescription(description);
            anomaly.setFileId(fileId);
            anomaly.setLogicalPath(logicalPath);
            anomaly.setOwner(owner);

            fileAnomalyRepository.save(anomaly);
        } catch (Exception e) {
            log.error("[!] Anomaly record failed: " + e.getMessage());
        }
    }

}

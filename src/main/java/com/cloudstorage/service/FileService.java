package com.cloudstorage.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
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
    // @Transactional
    public void uploadFile(MultipartFile file, Long userId, Long parentFolderId) {

        // 查用户
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到用户，请联系管理员解决"));
        Long storageUsed = owner.getStorageUsed(); // 获取用户已用配额

        // 存储逻辑路径
        String logical;

        if (parentFolderId == null)
            // 如果处于根目录中，则直接存储其文件名
            logical = file.getOriginalFilename();
        else {
            // 不处于根目录中，获取逻辑路径
            // UserFile parentFolder = userFileRepository.findByIdAndOwner(parentFolderId,
            // owner)
            // .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            // "父目录不存在"));
            // logical = parentFolder.getFilePath() + "/" + file.getOriginalFilename();
            logical = storageService.resolveLogicalPath(parentFolderId, userId) + "/" + ".tmp---"
                    + UUID.randomUUID().toString() + ".tmp";
        }

        // 从逻辑路径中解析出真实路径
        Path diskPath = storageService.validatePath(userId, logical);

        // 分别检查配额问题和命名问题
        if (file.getSize() + storageUsed > owner.getStorageQuota())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户配额不足");

        if (userFileRepository.existsByFilenameAndOwnerAndParentFolderId(file.getOriginalFilename(), owner,
                parentFolderId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目录下已存在同名");

        // 更新数据库数据
        // 把文件写入到磁盘中
        try {
            UserFile uf = new UserFile();

            uf.setFilename(file.getOriginalFilename());
            uf.setFileSize(file.getSize());
            // uf.setFilePath(relativePath);
            uf.setParentFolderId(parentFolderId);
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

    /**
     * 下载文件 业务
     * 
     * @param fileId 被下载的文件的ID
     * @param userId 文件所有者ID
     * @return 成功 则返回一个文件内容流
     */

    public FileSystemResource downloadFile(Long fileId, Long userId) {

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

    /**
     * 创建目录功能
     * 
     * @param folderName     目录名
     * @param userId         所有者ID
     * @param parentFolderId 目录所在目录的ID
     */
    @Transactional
    public void createFolder(String folderName, Long userId, Long parentFolderId) {

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

    /**
     * 获取目录下的所有文件和文件夹
     * 
     * @param userId         目录所有者ID
     * @param parentFolderId 目录ID
     * @return 成功 则返回一个包含 UserFile 实体的列表
     */
    public List<UserFile> listFiles(Long userId, Long parentFolderId) {

        return userFileRepository.findByOwnerAndParentFolderId(userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到用户")), parentFolderId);
    }

    /**
     * 删除文件
     * 
     * @param fileId 文件ID
     * @param userId 文件所有者ID
     */
    @Transactional
    public void deleteFile(Long fileId, Long userId) {

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

    /**
     * 重命名
     * 
     * @param fileId  文件ID
     * @param userId  文件所有者ID
     * @param newName 新名字
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

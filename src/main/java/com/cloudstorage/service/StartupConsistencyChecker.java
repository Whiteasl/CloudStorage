package com.cloudstorage.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.cloudstorage.model.entity.FileAnomaly;
import com.cloudstorage.model.entity.User;
import com.cloudstorage.model.entity.UserFile;
import com.cloudstorage.model.enums.AnomalyType;
import com.cloudstorage.repository.FileAnomalyRepository;
import com.cloudstorage.repository.UserFileRepository;
import com.cloudstorage.repository.UserRepository;

@Component
public class StartupConsistencyChecker implements ApplicationRunner {
    private final UserRepository userRepository;
    private final UserFileRepository userFileRepository;
    private final StorageService storageService;
    private final FileAnomalyRepository fileAnomalyRepository;

    private final static Logger log = LoggerFactory.getLogger(StartupConsistencyChecker.class);

    private final static int SIZE_TOLERANCE = 1024; // 容差大小
    private final static int RECHECK_DELAY_MS = 10_000; // 复核延迟

    // 存储异常信息的record
    private record Candidate(AnomalyType type, Long ownerId, Long fileId, String logicalPath,
            String description) {

        String key() {
            return buildKey(type, ownerId, fileId, logicalPath);
        }

    }

    public StartupConsistencyChecker(UserRepository userRepository, UserFileRepository userFileRepository,
            StorageService storageService, FileAnomalyRepository fileAnomalyRepository) {
        this.userRepository = userRepository;
        this.userFileRepository = userFileRepository;
        this.storageService = storageService;
        this.fileAnomalyRepository = fileAnomalyRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Thread checker = new Thread(this::checkAll, "startup-consistency-checker");
        checker.setDaemon(true); // 不阻止 JVM 退出，防止 JVM 关闭时被卡住
        checker.start();
        log.info("[*] The background consistency check thread has been initiated.");
    }

    public void checkAll() {
        // 开机自检功能的主流程
        // 自检流程：
        // 从数据库中遍历文件，并校验磁盘的实际文件
        // 从磁盘中遍历文件，校验磁盘文件与数据库中的数据的偏差
        // 记录异常文件信息

        // 异常文件判断依据：
        // 1、孤儿文件：数据库中无记录，磁盘中有文件；或是上传失败时导致的临时文件
        // 2、异常文件：文件信息与数据库中记录的信息比对有误差
        // 3、两次自检均出现该异常文件

        // 总检查次数：2

        // 第一次自检
        try {
            List<Candidate> out = scan();

            if (out.isEmpty()) {
                // 无任何异常，直接通过自检
                return;
            }

            // 把 out 数组中的异常 Key 装进集合中，方便二次自检的结果比对
            Set<String> keys = new HashSet<>();

            for (Candidate c : out) {
                keys.add(c.key());
            }

            // out 不为空，说明有异常，睡眠后再次检查，避免误报
            try {
                Thread.sleep(RECHECK_DELAY_MS);
            } catch (InterruptedException e) {
                log.error("[*] checkAll(): sleep interrupted --- " + e.getMessage());
                Thread.currentThread().interrupt();
                return;
            }

            // 第二次自检
            out = scan();

            // 查找已经记录在案的未处理异常
            List<FileAnomaly> anomalies = fileAnomalyRepository.findByResolvedFalse();

            Set<String> unresolved = new HashSet<>();

            for (FileAnomaly fa : anomalies) {
                unresolved
                        .add(buildKey(fa.getAnomalyType(), fa.getOwner().getId(), fa.getFileId(), fa.getLogicalPath()));
            }

            // 剔除交集
            keys.removeAll(unresolved);

            // 记录真实的新异常
            List<Candidate> confirmed = new ArrayList<>();

            for (Candidate c : out) {
                // 遍历检查异常是否两次均出现
                if (keys.contains(c.key())) {
                    // 二次出现该异常，则将其添加到 confirmed 中，后续记录到数据库中
                    confirmed.add(c);
                }
            }

            int count = 0;

            for (Candidate c : confirmed) {
                FileAnomaly fa = new FileAnomaly();
                fa.setAnomalyType(c.type());

                fa.setOwner(userRepository.findById(c.ownerId()).orElse(null));

                if (fa.getOwner() == null) {
                    // 如果该异常没有所有者 - 即所有者在自检期间注销了，数据库中找不到了
                    // 记录异常，跳过该记录
                    // 该部分属于注销功能管理
                    log.warn("[*] The user attributed to the exception does not exist: " + c.ownerId());
                    continue;
                }

                fa.setLogicalPath(c.logicalPath());
                fa.setDescription(c.description());
                fa.setFileId(c.fileId());

                fileAnomalyRepository.save(fa);
                count++;
            }

            log.info("[*] Confirmed " + confirmed.size() + " anomalies, write down " + count + " anomalies");
        } catch (Exception e) {
            // 通用异常处理
            log.error("[*] checkAll: scan() or scanUser() Failed: " + e.getMessage());
        }

    }

    private List<Candidate> scan() {
        List<User> users = userRepository.findAll();

        if (users.isEmpty()) {
            // 数据库中没有用户的操作
            log.info("[!] No user information exists in the database.");
            return new ArrayList<>();
        }

        List<Candidate> out = new ArrayList<>();
        for (User user : users) {
            scanUser(user, out);
        }

        return out;

    }

    private void scanUser(User owner, List<Candidate> out) {
        List<UserFile> userFiles = userFileRepository.findAllByOwner(owner);

        Map<Long, UserFile> byId = new HashMap<>();
        Map<Long, String> rowLogical = new HashMap<>();
        Map<String, Long> logicalToId = new HashMap<>();
        Set<Long> invalid = new HashSet<>();

        String logical = null;

        // Init: 树结构 + 推导检查
        for (UserFile userFile : userFiles) {
            // 把用户所有文件都存放到byId中，后续用于检测
            byId.put(userFile.getId(), userFile);
        }

        // Check-1: 对所有文件路径树进行比对
        for (UserFile userFile : userFiles) {
            if ((logical = deriveLogical(userFile.getId(), byId, owner, out)) == null) {
                // deriverLogical 返回 null 说明文件结构有异常
                invalid.add(userFile.getId());
                continue;
            }

            Long prev;
            if ((prev = logicalToId.putIfAbsent(logical, userFile.getId())) != null) {
                // logicalToId 比对出现非空，说明已有其他文件注册此路径，出现文件路径异常
                out.add(new Candidate(AnomalyType.DUP_LOGICAL, owner.getId(), userFile.getId(), logical,
                        buildDescription(AnomalyType.DUP_LOGICAL, prev, userFile.getId(), userFile.getFilename())));
                invalid.add(userFile.getId());
                continue;
            }

            // 文件结构无异常，返回文件路径字符串，存入rowLogical中
            rowLogical.put(userFile.getId(), logical);

        }

        // Check-2: DB -> DISK
        for (UserFile userFile : userFiles) {
            if (invalid.contains(userFile.getId())) {
                // invalid 中已经标记该文件为异常文件，直接跳过
                continue;
            }

            // 获取文件的逻辑路径
            logical = rowLogical.get(userFile.getId());

            Path diskPath;
            try {
                // 验证该文件在磁盘中的路径
                diskPath = storageService.validatePath(owner.getId(), logical);
            } catch (RuntimeException e) {
                // 找不到文件的路径，validatePath 函数会抛出异常。这里直接接住异常，避免函数暂停
                // 异常处理：
                // 标记为异常文件，跳过本次循环
                out.add(new Candidate(AnomalyType.MISSING, owner.getId(), userFile.getId(), logical,
                        buildDescription(AnomalyType.MISSING, userFile.getId(), userFile.getFilename())));
                continue;
            }

            if (!Files.exists(diskPath)) {
                // 磁盘路径不存在
                out.add(new Candidate(AnomalyType.MISSING, owner.getId(), userFile.getId(), logical,
                        buildDescription(AnomalyType.MISSING, userFile.getId(), userFile.getFilename())));
                continue;
            }

            if (Files.isDirectory(diskPath) != userFile.isFolder()) {
                // 磁盘文件属性不清
                out.add(new Candidate(AnomalyType.TYPE_MISMATCH, owner.getId(), userFile.getId(), logical,
                        buildDescription(AnomalyType.TYPE_MISMATCH, userFile.getId(), userFile.getFilename())));

                continue;
            }

            if (userFile.isFolder()) {
                // 下面的代码用于检测文件大小，文件夹不需要检测大小
                continue;
            }

            Long realSize;

            try {
                realSize = Files.size(diskPath);
            } catch (IOException e) {
                // 无法获取文件的真实大小，跳过该文件的大小检查，服务 log 输出
                log.warn("[!] StartupConsistencyChecker Error:  Encountered an error accessing the file size -- "
                        + e.getMessage());

                continue;
            }

            if (Math.abs(realSize - userFile.getFileSize()) > SIZE_TOLERANCE) {
                // 文件大小误差超过容差时处理
                out.add(new Candidate(AnomalyType.SIZE_MISMATCH, owner.getId(), userFile.getId(), logical,
                        buildDescription(AnomalyType.SIZE_MISMATCH, userFile.getId(), userFile.getFilename())));

                continue;
            }
        }

        // Check-3: DISK -> DB
        Path userDir = storageService.getUserPath(owner.getId());
        if (!Files.exists(userDir)) {
            // 找不到用户的根文件夹路径，代表本次磁盘文件检查全部失败
            out.add(new Candidate(AnomalyType.USERDIR_NOT_EXIST, owner.getId(), null, null,
                    buildDescription(AnomalyType.USERDIR_NOT_EXIST, owner.getId())));
            return;
        }

        // 遍历函数检查文件
        walkDir(userDir, "", logicalToId, owner, out);
    }

    /**
     * 遍历校验文件，并尝试清除上传失败导致的孤儿文件
     * 
     * @param path        Path - 待检查的文件夹
     * @param prefix      String - 已经遍历出的路径（递归使用）
     * @param logicalToId Map<String, Long> - 存储着逻辑路径对应文件ID的Map
     * @param owner       User - 用户
     * @param out         List<Candidate> - 异常记录数组
     */
    private void walkDir(Path path, String prefix, Map<String, Long> logicalToId, User owner, List<Candidate> out) {
        // 存储文件路径
        String logical = prefix;

        // 存储文件名（全称）
        String name;

        try (DirectoryStream<Path> files = Files.newDirectoryStream(path)) {
            for (Path file : files) {
                name = logical.isEmpty() ? file.getFileName().toString()
                        : logical + "/" + file.getFileName().toString();

                String tmpfile = file.getFileName().toString();
                if (tmpfile.startsWith(".tmp---") && tmpfile.endsWith(".tmp")) {
                    // 上传失败出现的临时文件
                    try {
                        Files.deleteIfExists(file);
                        continue;
                    } catch (IOException e) {
                        out.add(new Candidate(AnomalyType.ORPHAN, owner.getId(), null, name,
                                buildDescription(AnomalyType.ORPHAN, name)));

                        continue;
                    }
                }

                if (!logicalToId.containsKey(name)) {
                    // 无法在已遍历的逻辑路径中寻找到该文件
                    // 说明出现孤儿文件/文件夹，直接跳过该文件夹，不继续深入浪费时间
                    out.add(new Candidate(AnomalyType.ORPHAN, owner.getId(), null, name,
                            buildDescription(AnomalyType.ORPHAN, name)));

                    continue;
                }

                if (Files.isDirectory(file)) {
                    // 该文件是个文件夹时，递归检查文件夹下的所有文件
                    walkDir(file, name, logicalToId, owner, out);
                }
            }
        } catch (IOException e) {
            // 先跳过异常处理，完成主逻辑再进行处理
            log.warn("[*] Unable to access the file: " + e.getMessage());
        }
    }

    private String deriveLogical(Long checkFileId, Map<Long, UserFile> byId, User owner, List<Candidate> out) {
        Deque<String> segments = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();

        seen.add(byId.get(checkFileId).getId());
        segments.add(byId.get(checkFileId).getFilename());

        // 当前文件ID 为 传入文件的父目录ID
        Long currentId = byId.get(checkFileId).getParentFolderId();

        // 存放当前节点实体
        UserFile currentEntity;

        while (currentId != null && currentId != 0) {
            if (!seen.add(currentId)) {
                // 异常处理：文件路径推导成环
                out.add(new Candidate(AnomalyType.RING, owner.getId(), checkFileId, null,
                        buildDescription(AnomalyType.RING, checkFileId, currentId)));

                return null;
            }

            if ((currentEntity = byId.get(currentId)) == null) {
                // 接收 currentId 实体
                // 异常处理：currentId 实体不存在
                out.add(new Candidate(AnomalyType.PARENT_MISSING, owner.getId(), checkFileId, null,
                        buildDescription(AnomalyType.PARENT_MISSING, checkFileId, currentId)));

                return null;
            }

            if (!currentEntity.isFolder()) {
                // 判断 current 是否为文件夹
                // 异常处理：无法判断该文件是否为文件夹
                out.add(new Candidate(AnomalyType.PARENT_NOT_FOLDER, owner.getId(), checkFileId, null,
                        buildDescription(AnomalyType.PARENT_NOT_FOLDER, checkFileId, currentId)));

                return null;
            }

            segments.addFirst(currentEntity.getFilename());
            currentId = currentEntity.getParentFolderId();
        }

        return String.join("/", segments);

    }

    /**
     * 错误标识 Key 构建
     * 
     * @param type        AnomalyType - 错误类型
     * @param ownerId     Long - 错误文件拥有者ID
     * @param fileId      Long - 错误文件ID
     * @param logicalPath String - 本地路径，路径树构建失败时为 null
     * @return String - 返回错误标示 key
     */
    private static String buildKey(AnomalyType type, Long ownerId, Long fileId, String logicalPath) {
        return type.name() + "|" + ownerId + "|" + fileId + "|" + logicalPath;
    }

    /**
     * 通用错误描述构建
     * 
     * @param type     AnomalyType - 错误类型
     * @param fileId   Long - 错误文件ID
     * @param filename String - 错误文件名
     * @return String - 错误描述
     */
    private static String buildDescription(AnomalyType type, Long fileId, String filename) {
        return type.getDescription() + fileId + "--" + filename;
    }

    /**
     * 文件路径树检查错误错误描述构建
     * 
     * @param type        AnomalyType - 错误类型
     * @param checkFileId Long - 检查的文件ID
     * @param currentId   Long - 当前文件节点ID
     * @return String - 错误描述
     */
    private static String buildDescription(AnomalyType type, Long checkFileId, Long currentId) {
        return type.getDescription() + checkFileId + "--" + "文件路径树检查过程出现错误, 错误节点: " + currentId;
    }

    /**
     * DUP_LOGICAL 类型错误描述构建
     * 
     * @param type     AnomalyType - 错误类型
     * @param prev     Long - 已经存在列表中的文件ID
     * @param fileId   Long - 重复文件ID
     * @param filename String - 重复文件名
     * @return String - 错误描述
     */
    private static String buildDescription(AnomalyType type, Long prev, Long fileId, String filename) {
        return type.getDescription() + "{" + "First: " + prev + "," + "Second: " + fileId + "}" + ", Abnormal: "
                + fileId + "--" + filename;
    }

    /**
     * USERDIR_NOT_EXIST 类型错误描述构建
     * 
     * @param type    AnomalyType - 错误类型
     * @param ownerId Long - 错误用户ID
     * @return String - 错误描述
     */
    private static String buildDescription(AnomalyType type, Long ownerId) {
        return type.getDescription() + ownerId + " -- 无法在磁盘中找到该用户的根文件夹";
    }

    /**
     * ORPHAN 类型错误描述构建
     * 
     * @param type     AnomalyType - 错误类型
     * @param filename String - 错误文件名
     * @return String - 错误描述
     */
    private static String buildDescription(AnomalyType type, String filename) {
        return type.getDescription() + filename;
    }
}

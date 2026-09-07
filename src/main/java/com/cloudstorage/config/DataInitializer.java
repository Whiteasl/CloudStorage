package com.cloudstorage.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.cloudstorage.model.entity.User;
import com.cloudstorage.repository.UserRepository;
import com.cloudstorage.service.StorageService;

/**
 * DataInitializer
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final StorageService storageService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final static Logger log = LoggerFactory.getLogger(DataInitializer.class);

    public DataInitializer(StorageService storageService, UserRepository userRepository,
            PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.storageService = storageService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {

        this.migrateFileTable();

        createIfNotExist("admin", "123456", "admin@admin.com", "admin");
        createIfNotExist("user", "123456", "user@user.com", "user");

    }

    /**
     * 初始化时创建用户
     * 
     * @param username 用户名
     * @param password 密码
     * @param email    邮箱
     * @param role     角色
     */
    private void createIfNotExist(String username, String password, String email, String role) {

        if (userRepository.existsByUsername(username))
            return;

        User user = new User();

        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setRole(role);
        user.setEnabled(true);

        userRepository.save(user);

        storageService.initUserDirectory(user.getId());

        System.out.println("[DataInitializer] 创建初始用户：" + username + "( " + role + ")");
        System.out.println("[DataInitializer] 密码：" + password);
    }

    /**
     * 迁移表结构
     * 历史升级的一次性残留清洗，现在每次启动幂等空转
     */
    private void migrateFileTable() {
        int updated = jdbcTemplate.update("UPDATE files SET parent_folder_id = 0 WHERE parent_folder_id IS NULL");

        if (updated > 0) {
            log.info("[*] Normalized " + updated + " lines root directory marker.");
        }

        // 用于存储被执行的SQL语句
        // 查询数据用的 SQL语句
        String sql = "SELECT id, owner_id, parent_folder_id, filename FROM files";

        // 收集结果，以便执行 去重 操作
        // rs -> 数据集 rowNum -> 行号
        List<FileRow> fileRows = jdbcTemplate.query(sql,
                (rs, rowNum) -> new FileRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4)));

        // 清空语句
        sql = null;

        // 存储key - id
        Map<String, Long> keys = new HashMap<>();
        // 存储重复行，后续删除
        List<Long> duplicates = new ArrayList<>();

        // 查找重复元素
        for (FileRow fr : fileRows) {
            String key = this.buildKey(fr);
            if (!keys.containsKey(key)) {
                keys.put(key, fr.id());
                continue;
            }

            Long old = keys.get(key);

            if (old > fr.id()) {
                // 如果后来小于先来
                // 后来存入 keys ，先来存储 duplicates 后续删除

                duplicates.add(keys.get(key));
                keys.replace(key, fr.id());
                continue;
            }

            duplicates.add(fr.id());
        }

        // 去重
        if (!duplicates.isEmpty()) {
            List<String> placeholders = Collections.nCopies(duplicates.size(), "?");
            String placeholder = String.join(",", placeholders);

            // 修改为 去重 用的 SQL语句
            sql = "DELETE FROM files WHERE id IN (" + placeholder + ")";

            jdbcTemplate.update(sql, duplicates.toArray());

        }

        // 方言分支
        // 清空语句，避免执行残留语句
        sql = null;
        try (Connection connection = dataSource.getConnection()) {
            switch (connection.getMetaData().getDatabaseProductName().toLowerCase()) {
                case "h2":
                    // 语句：修改 files 中所有 parent_folder_id 不为 NULL
                    sql = "ALTER TABLE files ALTER COLUMN parent_folder_id SET NOT NULL";
                    break;

                case "mariadb":
                    // 语句：修改 files 中所有 parent_folder_id 不为 NULL
                    sql = "ALTER TABLE files MODIFY parent_folder_id BIGINT NOT NULL";
                    break;

                case "mysql":
                    // 语句：修改 files 中所有 parent_folder_id 不为 NULL
                    sql = "ALTER TABLE files MODIFY parent_folder_id BIGINT NOT NULL";
                    break;

                default:
                    log.warn(
                            "[!] migrateFileTable: This database not supported, you can change to Mariadb, H2 or MySQL");
            }

        } catch (SQLException e) {
            log.error("[!] migrateFileTable: Failed connect to database: " + e.getMessage());
        }
        if (sql != null) {
            try {
                jdbcTemplate.execute(sql);
            } catch (DataAccessException e) {
                log.error("[!] migrateFileTable: Failed to execute SQL statement: " + e.getMessage());
            }
        }

        // 索引校验
        try {

            if (!this.indexExists("files", "idx_files_parent_folder"))
                jdbcTemplate.execute("CREATE INDEX idx_files_parent_folder ON files(parent_folder_id)");
        } catch (SQLException e) {
            log.error("[!] indexExists: Failed to connect database: " + e.getMessage());
        } catch (DataAccessException e) {
            log.error("[!] migrateFileTable: Failed to execute SQL statement: " + e.getMessage());
        }

        // 删列
        try {
            if (this.columnExists("files", "file_path"))
                jdbcTemplate.execute("ALTER TABLE files DROP COLUMN file_path");
        } catch (SQLException e) {
            log.error("[!] columnsExists: Failed to check column existence: " + e.getMessage());
        } catch (DataAccessException e) {
            log.error("[!] migrateFileTable: Failed to execute SQL statement: " + e.getMessage());
        }
    }

    /**
     * FileRow
     * 内部 record 类
     * 迁移去重时读取的文件行快照
     * 
     * @param id       Long - 异常ID
     * @param ownerId  Long - 导致本次异常的用户
     * @param parentId Long - 导致本次异常的文件的父目录ID
     * @param filename String - 导致本次异常的文件名
     */
    private record FileRow(Long id, Long ownerId, Long parentId, String filename) {
    }

    /**
     * 拼查重键，三段用 | 分隔，对应文件唯一性的三个条件：归属人+所在目录+文件名
     * 
     * @param fr FileRow - 内部类，迁移去重时读取的文件行快照
     * @return String - 返回异常的唯一Key
     */
    private String buildKey(FileRow fr) {
        return fr.ownerId() + "|" + fr.parentId() + "|" + fr.filename();
    }

    /**
     * 索引存在性查询
     * 
     * @param table     String - 表名
     * @param indexName String - 索引名
     * @return boolean - 如果存在则返回 true, 否则返回 false
     * @throws SQLException 元数据查询失败时抛出
     */
    private boolean indexExists(String table, String indexName) throws SQLException {
        // 获取索引元数据
        try (Connection connection = dataSource.getConnection();
                // null catalog + % 在 mariaDB 中返回空结果集，导致每次启动都误判索引不存在
                ResultSet rs = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, table, false,
                        false);) {

            // 对返回值进行比对
            while (rs.next()) {
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))
                        && indexName.equalsIgnoreCase(rs.getString("INDEX_NAME")))
                    return true;

            }

            return false;
        }

    }

    /**
     * 判断列是否存在
     * 
     * @param table      String - 查询的表名
     * @param columnName String - 查询的列名
     * @return boolean - 如果有结果则返回 true ,否则返回 false
     * @throws SQLException 元数据查询失败时抛出
     */
    private boolean columnExists(String table, String columnName) throws SQLException {
        // 获取列的元数据
        try (Connection connection = dataSource.getConnection();
                ResultSet rs = connection.getMetaData().getColumns(connection.getCatalog(), null, table, columnName);) {

            // ResultSet 游标悬停在第一行前，next() 移动到第一行并报告是否有结果，精确查询只会返回一行结果
            return rs.next();
        }
    }
}
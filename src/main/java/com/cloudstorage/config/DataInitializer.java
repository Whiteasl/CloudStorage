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
            if (!this.indexExists("files", "uq_files_owner_parent_name"))
                jdbcTemplate.execute(
                        "CREATE UNIQUE INDEX uq_files_owner_parent_name ON files(owner_id, parent_folder_id, filename)");

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
            log.error("[!] columnsExists: Failed to connect database: " + e.getMessage());
        } catch (DataAccessException e) {
            log.error("[!] migrateFileTable: Failed to execute SQL statement: " + e.getMessage());
        }
    }

    private record FileRow(Long id, Long ownerId, Long parentId, String filename) {
    }

    private String buildKey(FileRow fr) {
        return fr.ownerId() + "|" + fr.parentId() + "|" + fr.filename();
    }

    private boolean indexExists(String table, String indexName) throws SQLException {
        // 获取表数据
        try (Connection connection = dataSource.getConnection();
                ResultSet rs = connection.getMetaData().getIndexInfo(null, null, "%", false, false);) {

            // 对返回值进行比对
            while (rs.next()) {
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))
                        && indexName.equalsIgnoreCase(rs.getString("INDEX_NAME")))
                    return true;

            }

            return false;
        }

    }

    private boolean columnExists(String table, String indexName) throws SQLException {
        // 获取表数据
        try (Connection connection = dataSource.getConnection();
                ResultSet rs = connection.getMetaData().getColumns(null, null, "%", "%");) {

            // 对返回值进行比对
            while (rs.next()) {
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))
                        && indexName.equalsIgnoreCase(rs.getString("INDEX_NAME")))
                    return true;
            }

            return false;
        }
    }
}
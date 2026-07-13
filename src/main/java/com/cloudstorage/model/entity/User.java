// 定义 User 对象在数据库中的表结构

package com.cloudstorage.model.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity // 标记为JPA实体
@Table(name = "users") // 表名（user是保留字，所以使用 users）
@EntityListeners(AuditingEntityListener.class) // 开启时间自动填充
@Getter
@Setter // 使用 lombok 自动生成 Setter 和 Getter 方法
@NoArgsConstructor // 生成无参构造方法 JPA 需要无参构造
@AllArgsConstructor // lombok 自动生成包含所有字段的构造方法

public class User {
    @Id // 定义主键
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 让数据库自动生成值(自增) IDENTITY 就是数据库的 AUTO_INCREMENT，每次插入新行就会自动编号 1,
                                                        // 2, 3...
    private Long id; // long 类型的默认值是 0，JPA 分不清是没存过还是 'id=0'，所以使用 Long 类型

    @Column(nullable = false, unique = true) // "nullable" 该列不为空 "unique" 值不能重复
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String role = "user"; // 定义用户权限，分别有 'user' 和 'admin' 两种，默认为用户

    // 用户空间管理
    private long storageQuota = 1073741824L; // 单位字节，默认值为 1 GB
    private long storageUsed = 0L; // 已用空间

    private boolean enabled = true; // 账户是否启用，只有验证了邮箱后才会启动，开发阶段先设置为 注册即启动。部署时再改为 false

    @CreatedDate // 创建后自动填入创建时的时间
    @Column(updatable = false) // 创建后不能更改
    private LocalDateTime createdAt; // 自动填充创建时间

    @LastModifiedDate // 每次更新自动修改为更新时的时间
    private LocalDateTime updatedAt; // 自动填充更新时间

}
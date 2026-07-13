// 定义 UserFiles 在数据库中的表结构

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table( name = "files")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor

public class UserFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String filePath;
    
    @Column(nullable = false)
    private long fileSize;              // 字节数（文件大小）

    private String contentType;         // MIME 类型

    private boolean isFolder = false;           // 分辨是否为文件夹，默认为 false，文件夹设置为 true

    private Long parentFolderId;            // 子目录所属的父级目录， null 为根目录，其他值=所属文件夹 ID

    @ManyToOne 
    @JoinColumn(name = "owner_id")
    private User owner;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
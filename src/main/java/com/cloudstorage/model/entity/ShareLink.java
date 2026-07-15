package com.cloudstorage.model.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
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
@Table(name = "ShareLink")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class ShareLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String verificationCode; // 校验码

    @ManyToOne
    @JoinColumn(name = "file")
    private UserFile shareFile; // 分享文件

    @ManyToOne
    @JoinColumn(name = "owner")
    private User owner; // 分享文件 拥有者

    @Column(nullable = false)
    private int downloadCount = 0; // 下载次数

    @Column(nullable = false)
    private int downloadLimit = -1; // 允许下载次数，-1 代表在分享时间内无限制下载，分享文件必须设置分享次数

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt; // 分享时间

    @Column(nullable = false)
    private LocalDateTime expireTime; // 过期时间
}
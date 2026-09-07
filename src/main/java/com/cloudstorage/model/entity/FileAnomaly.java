package com.cloudstorage.model.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.cloudstorage.model.enums.AnomalyType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * FileAnomaly
 * 
 * 文件异常记录实体
 */
@Entity
@Table(name = "FileAnomaly")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileAnomaly {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 异常ID

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner; // 造成异常的文件所有者

    private Long fileId; // 造成异常的文件ID

    private String logicalPath; // 造成异常的文件的路径

    @Column(nullable = false)
    private boolean resolved = false; // 问题解决状态，默认为未解决

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnomalyType anomalyType; // 异常类型

    @Column(nullable = false)
    private String description; // 异常信息

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime detectedAt; // 异常发现时间，默认为不可更改
}

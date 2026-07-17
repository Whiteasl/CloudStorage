package com.cloudstorage.model.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Setter
@Getter
public class ShareLinkResponse {
    private Long id;
    private String verificationCode;
    // private UserFile shareFile; // 避免真实路径泄露
    // private User owner; // 防止他人拿到密码哈希，把实体传递改成字段传递，只传必要字段

    // 原 UserFile 实体，修改成文件属性，避免全部信息泄露
    private String filename;
    private Long fileSize;
    private boolean isFolder;

    // 原 User 实体，修改成用户属性，避免全部信息泄露
    private String ownerName;

    private int downloadCount;
    private int downloadLimit;
    private LocalDateTime createdAt;
    private LocalDateTime expireTime;
}

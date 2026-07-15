package com.cloudstorage.model.dto;

import java.time.LocalDateTime;

import com.cloudstorage.model.entity.User;
import com.cloudstorage.model.entity.UserFile;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Setter
@Getter
public class ShareLinkResponse {
    private Long id;
    private String verificationCode;
    private UserFile shareFile;
    private User owner;
    private int downloadCount;
    private int downloadLimit;
    private LocalDateTime createdAt;
    private LocalDateTime expireTime;
}

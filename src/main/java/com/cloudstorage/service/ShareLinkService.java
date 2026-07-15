package com.cloudstorage.service;

import com.cloudstorage.config.AppConfig;
import com.cloudstorage.repository.ShareLinkRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloudstorage.model.dto.ShareLinkResponse;
import com.cloudstorage.model.entity.*;
import com.cloudstorage.repository.UserFileRepository;
import com.cloudstorage.repository.UserRepository;
import com.cloudstorage.util.RandomChar;

@Service
public class ShareLinkService {
    private final ShareLinkRepository shareLinkRepository;
    private final UserRepository userRepository;
    private final UserFileRepository userFileRepository;
    private final StorageService storageService;

    public ShareLinkService(UserRepository userRepository, UserFileRepository userFileRepository,
            StorageService storageService, ShareLinkRepository shareLinkRepository, AppConfig appConfig) {
        this.userRepository = userRepository;
        this.userFileRepository = userFileRepository;
        this.storageService = storageService;
        this.shareLinkRepository = shareLinkRepository;
    }

    public ShareLinkResponse createShare(Long fileId, Long userId, int downloadLimit, int expireHours) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "账户异常：未找到账户"));
        UserFile uf = userFileRepository.findByIdAndOwner(fileId, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件异常：未找到文件"));

        ShareLink sl = new ShareLink();

        if (shareLinkRepository.existsByShareFileAndOwner(uf, owner)) {

            ShareLink existing = shareLinkRepository.findByShareFileAndOwner(uf, owner).orElse(null);
            String existCode = existing != null ? existing.getVerificationCode() : "未知";

            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "分享异常：已分享过同一份内容，分享链接为 " + existCode);
        }

        // 生成校验码
        String code = this.generateCode();
        while (shareLinkRepository.existsByVerificationCode(code)) {

            code = this.generateCode();
        }

        // 保存到数据库
        sl.setVerificationCode(code);
        sl.setShareFile(uf);
        sl.setOwner(owner);
        sl.setDownloadLimit(downloadLimit);
        sl.setExpireTime(LocalDateTime.now().plusHours(expireHours));
        shareLinkRepository.save(sl);

        return toShareLinkResponse(sl);
    }

    @Transactional
    public FileSystemResource accessShare(String code) {

        ShareLink shareLink = shareLinkRepository.findByVerificationCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到分享文件"));

        int downloadCount = shareLink.getDownloadCount();
        int downloadLimit = shareLink.getDownloadLimit();

        if (shareLink.getExpireTime().isBefore(LocalDateTime.now()))
            throw new ResponseStatusException(HttpStatus.GONE, "链接已过期");

        if (downloadLimit != -1 && downloadCount >= downloadLimit)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "下载次数已到达上限");

        shareLink.setDownloadCount(++downloadCount);
        shareLinkRepository.save(shareLink);

        Path diskPath = storageService.validatePath(shareLink.getOwner().getId(),
                shareLink.getShareFile().getFilePath());

        return new FileSystemResource(diskPath);
    }

    public List<ShareLinkResponse> listMyShare(Long userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户异常：未找到用户"));

        List<ShareLink> shareLinks = shareLinkRepository.findByOwner(owner);

        List<ShareLinkResponse> shareLinkResponses = new ArrayList<>();
        for (ShareLink sl : shareLinks) {
            shareLinkResponses.add(this.toShareLinkResponse(sl));
        }
        return shareLinkResponses;
    }

    public void deleteShare(Long id, Long userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "账户异常：未找到用户"));
        shareLinkRepository.deleteByIdAndOwner(id, owner);
    }

    /**
     * 生成校验码
     */
    private String generateCode() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder(RandomChar.generateUUID().substring(0, 8) + "-");

        sb.append(RandomChar.unsignedGenerateChar(r.nextInt(8 - 6 + 1) + 6));

        return sb.toString();
    }

    /**
     * 把 ShareLink 实体转换成 ShareLinkResponse
     */
    private ShareLinkResponse toShareLinkResponse(ShareLink sl) {
        return new ShareLinkResponse(
                sl.getId(),
                sl.getVerificationCode(),
                sl.getShareFile(),
                sl.getOwner(),
                sl.getDownloadCount(),
                sl.getDownloadLimit(),
                sl.getCreatedAt(),
                sl.getExpireTime());

    }
}

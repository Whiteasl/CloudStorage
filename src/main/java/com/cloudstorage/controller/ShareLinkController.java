package com.cloudstorage.controller;

import org.springframework.web.bind.annotation.RestController;

import com.cloudstorage.service.ShareLinkService;
import com.cloudstorage.model.dto.ShareLinkResponse;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * ShareLinkController
 */
@RestController
public class ShareLinkController {
    private final ShareLinkService shareLinkService;

    public ShareLinkController(ShareLinkService shareLinkService) {
        this.shareLinkService = shareLinkService;
    }

    /**
     * 创建分享链接控制器
     * 
     * @param fileId        分享文件ID
     * @param downloadLimit 下载次数限制
     * @param expireHours   过期时间
     * @return
     */
    @PostMapping("/share/create")
    public ResponseEntity<Void> createShare(@RequestParam("fileId") Long fileId,
            @RequestParam("downloadLimit") int downloadLimit, @RequestParam("expireHours") int expireHours) {

        shareLinkService.createShare(fileId, this.getCurrentUserId(), downloadLimit, expireHours);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取分享文件控制器，注意该路径是大写首字母
     * 
     * @param code 分享文件校验码
     * @return 返回文件流
     */
    @GetMapping("/Share/{code}")
    public ResponseEntity<FileSystemResource> getShareFile(@PathVariable("code") String code) {

        FileSystemResource resource = shareLinkService.accessShare(code);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * 列出用户所有的分享链接
     * 
     * @return 存储 ShareLinkResponse 实体的列表
     */
    @GetMapping("/share/list")
    public ResponseEntity<List<ShareLinkResponse>> listMyShare() {

        return ResponseEntity.ok(shareLinkService.listMyShare(this.getCurrentUserId()));
    }

    /**
     * 删除分享链接控制器
     * 
     * @param id 分享链接ID
     * @return
     */
    @DeleteMapping("/share/delete")
    public ResponseEntity<Void> deleteShare(@RequestParam("shareId") Long id) {

        shareLinkService.deleteShare(id, this.getCurrentUserId());

        return ResponseEntity.ok().build();
    }

    /**
     * 获取当前用户ID
     * 
     * @return
     */
    private Long getCurrentUserId() {
        // 获取当前用户

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.valueOf((String) auth.getPrincipal());
    }

}

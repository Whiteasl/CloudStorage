package com.cloudstorage.controller;

import org.springframework.web.bind.annotation.RestController;

import com.cloudstorage.service.ShareLinkService;
import com.cloudstorage.model.dto.ShareLinkResponse;
import com.cloudstorage.model.entity.*;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
public class ShareLinkController {
    private final ShareLinkService shareLinkService;

    public ShareLinkController(ShareLinkService shareLinkService) {
        this.shareLinkService = shareLinkService;
    }

    private Long getCurrentUserId() {
        // 获取当前用户

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.valueOf((String) auth.getPrincipal());
    }

    @PostMapping("/share/create")
    public ResponseEntity<Void> createShare(@RequestParam("fileId") Long fileId,
            @RequestParam("downloadLimit") int downloadLimit, @RequestParam("expireHours") int expireHours) {

        shareLinkService.createShare(fileId, this.getCurrentUserId(), downloadLimit, expireHours);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/share/{code}")
    public ResponseEntity<FileSystemResource> getShareFile(@PathVariable("code") String code) {

        FileSystemResource resource = shareLinkService.accessShare(code);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/share/list")
    public ResponseEntity<List<ShareLinkResponse>> listMyShare() {

        return ResponseEntity.ok(shareLinkService.listMyShare(this.getCurrentUserId()));
    }

    @DeleteMapping("/share/delete")
    public ResponseEntity<Void> deleteShare(@RequestParam("shareId") Long id) {

        shareLinkService.deleteShare(id, this.getCurrentUserId());

        return ResponseEntity.ok().build();
    }

}

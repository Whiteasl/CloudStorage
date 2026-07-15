package com.cloudstorage.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cloudstorage.model.dto.BatchDeleteRequest;
import com.cloudstorage.model.dto.CreateFolderRequest;
import com.cloudstorage.model.dto.FileResponse;
import com.cloudstorage.model.entity.UserFile;
import com.cloudstorage.service.FileService;
import com.cloudstorage.util.FileUtils;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
public class FileController {
    private final FileService fileService;
    private final FileUtils fileUtils;

    public FileController(FileService fileService, FileUtils fileUtils) {
        this.fileService = fileService;
        this.fileUtils = fileUtils;
    }

    private Long getCurrentUserId() {
        // 获取当前用户

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.valueOf((String) auth.getPrincipal());
    }

    private FileResponse toFileResponse(UserFile uf) {
        // 把 UserFile 实体转换成 FileResponse

        return new FileResponse(
                uf.getId(),
                uf.getFilename(),
                uf.getFilePath(),
                uf.getFileSize(),
                uf.getContentType(),
                uf.isFolder(),
                uf.getParentFolderId(),
                uf.getCreatedAt(),
                uf.getUpdatedAt());
    }

    /**
     * 处理上传文件请求
     * 
     * @param @RequestParam                 MultipartFile file 文件属性
     * @param @RequestParam(required=false) Long parentFolderId 文件存储的目录ID
     */
    @PostMapping("/file/upload")
    public ResponseEntity<Void> uploadFile(@RequestParam("file") MultipartFile file,
            @RequestParam(required = false, value = "parentFolderId") Long parentFolderId) {

        Long userId = this.getCurrentUserId();

        fileService.uploadFile(file, userId, parentFolderId);

        return ResponseEntity.status(HttpStatus.CREATED).build();

    }

    @GetMapping("/file/download")
    public ResponseEntity<FileSystemResource> downloadFile(@RequestParam("fileId") Long fileId) {
        Long userId = this.getCurrentUserId();
        FileSystemResource resource = fileService.downloadFile(fileId, userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping("/file/folder")
    public ResponseEntity<Void> createFolder(@RequestBody CreateFolderRequest request) {

        fileService.createFolder(request.getFolderName(), this.getCurrentUserId(), request.getParentFolderId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/file/list")
    public ResponseEntity<List<FileResponse>> listFiles(
            @RequestParam(required = false, value = "parentFolderId") Long parentFolderId) {
        List<UserFile> fileList = fileService.listFiles(this.getCurrentUserId(), parentFolderId);
        List<FileResponse> fileResponseList = new ArrayList<>();
        for (UserFile uf : fileList) {
            fileResponseList.add(this.toFileResponse(uf));
        }
        return ResponseEntity.ok(fileResponseList);
    }

    @DeleteMapping("/file/delete")
    public ResponseEntity<Void> deleteFile(@RequestParam("fileId") Long fileId) {
        fileService.deleteFile(fileId, this.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/file/rename")
    public ResponseEntity<Void> renameFile(@RequestParam("fileId") Long fileId,
            @RequestParam("newName") String newName) {

        fileService.renameFile(fileId, getCurrentUserId(), newName);

        return ResponseEntity.ok().build();
    }

    @PutMapping("/file/move")
    public ResponseEntity<Void> moveFile(@RequestParam("fileId") Long sourceId,
            @RequestParam(required = false, value = "targetFolderId") Long targetFolderId) {

        fileService.moveFile(sourceId, this.getCurrentUserId(), targetFolderId);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/file/{fileId}")
    public ResponseEntity<FileResponse> getFileDetail(@PathVariable("fileId") Long fileId) {

        FileResponse fileResponse = this.toFileResponse(fileService.getFileDetail(fileId, this.getCurrentUserId()));

        return ResponseEntity.ok(fileResponse);
    }

    @GetMapping("/file/search")
    public ResponseEntity<List<FileResponse>> searchFiles(@RequestParam("keyword") String keyword) {

        List<UserFile> userFiles = fileService.searchFiles(getCurrentUserId(), keyword);

        List<FileResponse> fileResponses = new ArrayList<>();
        for (UserFile uf : userFiles) {
            fileResponses.add(this.toFileResponse(uf));
        }

        return ResponseEntity.ok(fileResponses);
    }

    @PostMapping("/file/batch-delete")
    public ResponseEntity<Void> batchDelete(@Valid @RequestBody BatchDeleteRequest request) {
        fileService.batchDelete(request.getIds(), getCurrentUserId());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/file/compress")
    public ResponseEntity<FileSystemResource> compressFiles(@RequestParam("fileIds") Set<Long> fileIds,
            @RequestParam(required = false, value = "archiveName") String archiveName) {

        if (archiveName == null) {
            archiveName = String.valueOf(System.currentTimeMillis());
        }
        FileSystemResource resource = fileUtils.compressFiles(fileIds, this.getCurrentUserId(), archiveName);

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + archiveName + ".zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

}
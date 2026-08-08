package com.cloudstorage.controller;

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

import com.cloudstorage.model.dto.Request.BatchDeleteRequest;
import com.cloudstorage.model.dto.Request.CreateFolderRequest;
import com.cloudstorage.model.dto.Response.FileResponse;
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

/**
 * FileController
 */
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
        return (Long) auth.getPrincipal();
    }

    /***
     * 把 UserFile 实体转换成 FileResponse
     * 
     * @param uf 传入需要转换的 UserFile 实体
     * @return 返回 FileResponse 对象
     */

    private FileResponse toFileResponse(UserFile uf) {

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
     * 上传文件控制器
     * 
     * @param MultipartFile  从URL中获取 file 文件属性
     * @param parentFolderId 从URL中获取 文件存储的目录ID ，默认值为 null
     */
    @PostMapping("/file/upload")
    public ResponseEntity<Void> uploadFile(@RequestParam("file") MultipartFile file,
            @RequestParam(required = false, value = "parentFolderId") Long parentFolderId) {

        Long userId = this.getCurrentUserId();

        fileService.uploadFile(file, userId, parentFolderId);

        return ResponseEntity.status(HttpStatus.CREATED).build();

    }

    /**
     * 下载文件控制器
     * 
     * @param fileId 从URL中获取 下载的文件ID
     * 
     */
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

    /**
     * 处理创建目录控制器
     * 
     * @param request 获取请求体
     * 
     */
    @PostMapping("/file/folder")
    public ResponseEntity<Void> createFolder(@RequestBody CreateFolderRequest request) {

        fileService.createFolder(request.getFolderName(), this.getCurrentUserId(), request.getParentFolderId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 列出指定目录下的所有文件/文件夹
     * 
     * @param parentFolderId 指定目录的ID，默认值为 null
     */
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

    /**
     * 删除文件控制器
     * 
     * @param fileId 被删除文件的ID
     */
    @DeleteMapping("/file/delete")
    public ResponseEntity<Void> deleteFile(@RequestParam("fileId") Long fileId) {
        fileService.deleteFile(fileId, this.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    /***
     * 文件重命名控制器
     * 
     * @param fileId  文件 ID
     * @param newName 新名字
     * @return 返回一个状态码
     */
    @PutMapping("/file/rename")
    public ResponseEntity<Void> renameFile(@RequestParam("fileId") Long fileId,
            @RequestParam("newName") String newName) {

        fileService.renameFile(fileId, getCurrentUserId(), newName);

        return ResponseEntity.ok().build();
    }

    /***
     * 移动文件/文件夹控制器
     * 
     * @param sourceId       源文件ID
     * @param targetFolderId 目标文件夹ID，默认为 null
     * @return 成功移动 返回200，不成功则返回异常
     */
    @PutMapping("/file/move")
    public ResponseEntity<Void> moveFile(@RequestParam("fileId") Long sourceId,
            @RequestParam(required = false, value = "targetFolderId") Long targetFolderId) {

        fileService.moveFile(sourceId, this.getCurrentUserId(), targetFolderId);

        return ResponseEntity.ok().build();
    }

    /**
     * 获取文件元数据 控制器
     *
     * @param fileId 目标文件ID
     * @return 返回 FileResponse 对象
     */
    @GetMapping("/file/{fileId}")
    public ResponseEntity<FileResponse> getFileDetail(@PathVariable("fileId") Long fileId) {

        FileResponse fileResponse = this.toFileResponse(fileService.getFileDetail(fileId, this.getCurrentUserId()));

        return ResponseEntity.ok(fileResponse);
    }

    /***
     * 处理搜索文件控制器
     * 
     * @param keyword 搜索的键值
     * @return 返回搜索结果
     */
    @GetMapping("/file/search")
    public ResponseEntity<List<FileResponse>> searchFiles(@RequestParam("keyword") String keyword) {

        List<UserFile> userFiles = fileService.searchFiles(getCurrentUserId(), keyword);

        List<FileResponse> fileResponses = new ArrayList<>();
        for (UserFile uf : userFiles) {
            fileResponses.add(this.toFileResponse(uf));
        }

        return ResponseEntity.ok(fileResponses);
    }

    /**
     * 处理批量删除控制器
     * 
     * @param request 获取请求体
     * @return 成功 返回状态码
     */
    @PostMapping("/file/batch-delete")
    public ResponseEntity<Void> batchDelete(@Valid @RequestBody BatchDeleteRequest request) {
        fileService.batchDelete(request.getIds(), getCurrentUserId());

        return ResponseEntity.noContent().build();
    }

    /**
     * 处理压缩文件控制器
     * 
     * @param fileIds     文件ID列表
     * @param archiveName 压缩文件名
     * @return 成功 返回一个下载链接
     */
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
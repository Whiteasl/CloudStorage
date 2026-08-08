package com.cloudstorage.model.dto.Response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter

public class FileResponse {
    /**
     * 文件/文件夹元数据响应
     * 
     * @Param id
     * @Param filename
     * @Param filePath
     * @Param fileSize
     * @Param contentType
     * @Param isFolder
     * @Param parentFolderId,
     * @Param createAt
     * @Param updateAt
     */

    private Long id;
    private String filename;
    private String filePath;
    private long fileSize;
    private String contentType;
    @JsonProperty("isFolder")
    private boolean isFolder;
    private Long parentFolderId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

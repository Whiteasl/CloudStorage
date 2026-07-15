package com.cloudstorage.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CreateFolderRequest {
    /**
     * 创建目录数据请求
     * 
     * @Param folderName 目录名字
     * @Param parentFolderId? 父目录ID
     * 
     */
    private String folderName;
    private Long parentFolderId;

}

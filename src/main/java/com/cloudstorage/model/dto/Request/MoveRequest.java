package com.cloudstorage.model.dto.Request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class MoveRequest {
    /**
     * 移动文件请求
     * 
     * @Param targetFolderId 新目录 ID
     */

    private Long targetFolderId;

}

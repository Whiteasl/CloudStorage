package com.cloudstorage.model.dto.Request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RenameRequest {
    /**
     * 重命名请求处理
     * 
     * @Param newName 新名字
     */

    private String newName;
}

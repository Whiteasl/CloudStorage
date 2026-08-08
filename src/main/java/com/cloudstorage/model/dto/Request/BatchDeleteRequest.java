package com.cloudstorage.model.dto.Request;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class BatchDeleteRequest {
    /**
     * 批量删除响应
     * 
     * @Param ids 选中的文件ID
     */

    private Set<Long> ids;
}

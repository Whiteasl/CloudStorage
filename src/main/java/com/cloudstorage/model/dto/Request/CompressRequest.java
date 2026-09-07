package com.cloudstorage.model.dto.Request;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CompressRequest {
    // 压缩功能请求体
    private Set<Long> ids;
    private Long folderId;
    private String archiveName;
}

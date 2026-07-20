package com.cloudstorage.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateShareRequest {
    private Long fileId;
    private int downloadLimit;
    private int expiredHours;
}

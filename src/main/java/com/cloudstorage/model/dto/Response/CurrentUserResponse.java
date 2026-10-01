package com.cloudstorage.model.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CurrentUserResponse {
    private Long id;
    private String username;
    private String role;
}

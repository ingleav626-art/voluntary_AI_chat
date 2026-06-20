package com.voluntary.chat.server.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long userId;
    private String phone;
    private String username;
    private String avatar;
    private String bio;
    private LocalDateTime createTime;
}

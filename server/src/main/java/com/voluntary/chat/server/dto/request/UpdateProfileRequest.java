package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 2, max = 50, message = "用户名长度2-50个字符")
    private String username;

    @Size(max = 500, message = "头像URL过长")
    private String avatar;

    @Size(max = 500, message = "签名过长")
    private String bio;
}

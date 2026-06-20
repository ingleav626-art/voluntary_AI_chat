package com.voluntary.chat.server.dto.request;

import lombok.Data;

@Data
public class SmsSendRequest {

    @jakarta.validation.constraints.NotBlank(message = "手机号不能为空")
    @jakarta.validation.constraints.Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}

package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh Token 不能为空")
    private String refreshToken;
}

package com.voluntary.chat.server.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 图片上传响应
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@Builder
public class ImageUploadResponse {

    private String fileId;
    private String url;
    private String thumbnailUrl;
    private Integer width;
    private Integer height;
    private Long size;
    private String fileType;
}

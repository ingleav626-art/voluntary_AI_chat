package com.voluntary.chat.server.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 文件上传响应
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@Builder
public class FileUploadResponse {

    /** 文件ID */
    private String fileId;

    /** 文件访问URL（相对路径） */
    private String url;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long size;

    /** 文件MIME类型 */
    private String fileType;
}

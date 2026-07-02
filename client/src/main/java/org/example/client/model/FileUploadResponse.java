package org.example.client.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传响应模型
 *
 * <p>对应后端 POST /api/file/upload/file 接口返回数据。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
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

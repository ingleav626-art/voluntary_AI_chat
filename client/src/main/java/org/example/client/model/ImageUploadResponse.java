package org.example.client.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片上传响应模型
 *
 * <p>对应后端 POST /message/upload/image 接口返回数据。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class ImageUploadResponse {

    /** 文件ID */
    private String fileId;

    /** 图片URL */
    private String url;

    /** 缩略图URL */
    private String thumbnailUrl;

    /** 图片宽度 */
    private Integer width;

    /** 图片高度 */
    private Integer height;

    /** 文件大小（字节） */
    private Long size;

    /** 文件类型 */
    private String fileType;
}

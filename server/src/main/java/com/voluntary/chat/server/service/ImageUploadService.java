package com.voluntary.chat.server.service;

import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.dto.response.ImageUploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传服务
 *
 * <p>支持 JPEG/PNG/GIF/WebP 格式，最大 10MB。
 * 上传后自动压缩（最大宽度 1080px）并生成缩略图。
 * 当前使用本地文件存储，后续可替换为 MinIO。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Slf4j
@Service
public class ImageUploadService {

    /** 支持的图片格式 */
    private static final Set<String> SUPPORTED_FORMATS = new HashSet<>(
            Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp"));

    /** 最大图片大小：10MB */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;

    /** 压缩后的最大宽度 */
    private static final int MAX_WIDTH = 1080;

    /** 缩略图最大宽度 */
    private static final int THUMBNAIL_WIDTH = 200;

    private final String uploadDir;
    private final String baseUrl;

    public ImageUploadService(
            @Value("${upload.image.dir:uploads/chat/images}") String uploadDir,
            @Value("${upload.image.base-url:http://localhost:8080/files}") String baseUrl) {
        this.uploadDir = uploadDir;
        this.baseUrl = baseUrl;
    }

    /**
     * 上传图片
     *
     * @param file 上传的图片文件
     * @return 上传结果
     */
    public ImageUploadResponse uploadImage(MultipartFile file) {
        // 1. 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_FORMATS.contains(contentType)) {
            throw new BusinessException(ErrorCode.IMAGE_FORMAT_NOT_SUPPORTED);
        }

        // 2. 校验文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.IMAGE_SIZE_EXCEEDED);
        }

        // 3. 生成文件路径
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileId = UUID.randomUUID().toString().replace("-", "");
        String extension = getExtension(contentType);
        String fileName = fileId + extension;
        String thumbnailName = fileId + "_thumb" + extension;

        // 4. 读取图片并获取尺寸
        BufferedImage originalImage;
        try (InputStream is = file.getInputStream()) {
            originalImage = ImageIO.read(is);
            if (originalImage == null) {
                throw new BusinessException(ErrorCode.IMAGE_FORMAT_NOT_SUPPORTED, "无法解析图片文件");
            }
        } catch (IOException e) {
            log.error("读取上传图片失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片读取失败");
        }

        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        try {
            // 5. 保存原始/压缩图片
            BufferedImage imageToSave = originalImage;
            int savedWidth = originalWidth;
            int savedHeight = originalHeight;

            if (originalWidth > MAX_WIDTH) {
                // 需要压缩
                double ratio = (double) MAX_WIDTH / originalWidth;
                savedWidth = MAX_WIDTH;
                savedHeight = (int) (originalHeight * ratio);
                imageToSave = resizeImage(originalImage, savedWidth, savedHeight);
            }

            // 6. 保存原图（或压缩后的图片）
            Path uploadPath = Paths.get(uploadDir, dateStr);
            Files.createDirectories(uploadPath);

            Path filePath = uploadPath.resolve(fileName);
            ImageIO.write(imageToSave, getFormatName(contentType), filePath.toFile());

            // 7. 生成并保存缩略图
            int thumbHeight = (int) ((double) savedHeight / savedWidth * THUMBNAIL_WIDTH);
            BufferedImage thumbnail = resizeImage(imageToSave, THUMBNAIL_WIDTH, thumbHeight);
            Path thumbnailPath = uploadPath.resolve(thumbnailName);
            ImageIO.write(thumbnail, getFormatName(contentType), thumbnailPath.toFile());

            // 8. 构建响应
            String url = baseUrl + "/" + dateStr + "/" + fileName;
            String thumbnailUrl = baseUrl + "/" + dateStr + "/" + thumbnailName;

            log.info("图片上传成功: fileId={}, url={}, size={}, width={}, height={}",
                    fileId, url, file.getSize(), originalWidth, originalHeight);

            return ImageUploadResponse.builder()
                    .fileId(fileId)
                    .url(url)
                    .thumbnailUrl(thumbnailUrl)
                    .width(originalWidth)
                    .height(originalHeight)
                    .size(file.getSize())
                    .fileType(contentType)
                    .build();

        } catch (IOException e) {
            log.error("保存图片失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片保存失败");
        }
    }

    /**
     * 缩放图片
     */
    private BufferedImage resizeImage(BufferedImage original, int targetWidth, int targetHeight) {
        // GIF 动画不宜压缩帧，直接返回原图
        if (original.getType() == BufferedImage.TYPE_BYTE_INDEXED) {
            Image scaled = original.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
            BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = result.createGraphics();
            g2d.drawImage(scaled, 0, 0, null);
            g2d.dispose();
            return result;
        }

        BufferedImage result = new BufferedImage(targetWidth, targetHeight, original.getType());
        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        return result;
    }

    /**
     * 根据 content type 获取文件扩展名
     */
    private String getExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    /**
     * 根据 content type 获取 ImageIO 格式名
     */
    private String getFormatName(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}

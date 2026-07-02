package com.voluntary.chat.server.service;

import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.dto.response.FileUploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件上传服务
 *
 * <p>
 * 支持通用文件上传，最大 100MB。
 * 返回相对路径 URL，前端根据服务器地址拼接完整 URL。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Slf4j
@Service
public class FileUploadService {

    /** 最大文件大小：100MB */
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024L;

    private final String fileDir;

    public FileUploadService(
            @Value("${upload.file.dir:${app.data.dir:./data}/uploads/chat/files}") String fileDir) {
        this.fileDir = fileDir;
    }

    /**
     * 上传文件
     *
     * @param file 上传的文件
     * @return 上传结果
     */
    public FileUploadResponse uploadFile(MultipartFile file) {
        // 1. 校验文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件大小不能超过100MB");
        }

        // 2. 获取原始文件名
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isEmpty()) {
            originalFileName = "unknown";
        }

        // 3. 生成文件存储路径
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileId = UUID.randomUUID().toString().replace("-", "");
        String extension = getExtension(originalFileName);
        String storedFileName = fileId + extension;

        try {
            // 4. 保存文件
            Path uploadPath = Paths.get(fileDir, dateStr);
            Files.createDirectories(uploadPath);

            Path filePath = uploadPath.resolve(storedFileName);
            file.transferTo(filePath.toFile());

            // 5. 构建响应（返回相对路径）
            String url = "/chat-files/" + dateStr + "/" + storedFileName;

            // 探测MIME类型
            String contentType = file.getContentType();
            if (contentType == null || contentType.isEmpty()) {
                contentType = Files.probeContentType(filePath);
            }

            log.info("文件上传成功: fileId={}, fileName={}, url={}, size={}",
                    fileId, originalFileName, url, file.getSize());

            return FileUploadResponse.builder()
                    .fileId(fileId)
                    .url(url)
                    .fileName(originalFileName)
                    .size(file.getSize())
                    .fileType(contentType)
                    .build();

        } catch (IOException e) {
            log.error("保存文件失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
    }

    /**
     * 从文件名获取扩展名
     */
    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex);
        }
        return "";
    }
}

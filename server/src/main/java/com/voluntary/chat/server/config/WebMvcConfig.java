package com.voluntary.chat.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 配置
 *
 * <p>配置上传文件的静态资源访问路径。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(WebMvcConfig.class);

    @Value("${upload.image.dir:uploads/chat/images}")
    private String uploadDir;

    @Value("${upload.avatar.dir:uploads/avatars}")
    private String avatarDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将相对路径转为绝对路径，确保资源映射可靠
        final Path absolutePath = Paths.get(uploadDir).toAbsolutePath();
        LOG.info("文件资源映射: /files/** -> file:{}/", absolutePath);

        // 映射 /files/** 到上传目录，使上传的图片可通过 URL 访问
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + absolutePath + "/");

        // 映射 /avatars/** 到头像目录
        final Path avatarPath = Paths.get(avatarDir).toAbsolutePath();
        LOG.info("头像资源映射: /avatars/** -> file:{}/", avatarPath);
        registry.addResourceHandler("/avatars/**")
                .addResourceLocations("file:" + avatarPath + "/");
    }
}

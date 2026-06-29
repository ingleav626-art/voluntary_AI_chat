package com.voluntary.chat.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voluntary.chat.server.entity.NotificationSettings;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知设置 Mapper
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Mapper
public interface NotificationSettingsMapper extends BaseMapper<NotificationSettings> {
}

package com.voluntary.chat.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voluntary.chat.server.entity.GroupEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 群组 Mapper
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Mapper
public interface GroupMapper extends BaseMapper<GroupEntity> {
}
package com.voluntary.chat.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voluntary.chat.server.entity.Friend;
import org.apache.ibatis.annotations.Mapper;

/**
 * 好友关系 Mapper
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Mapper
public interface FriendMapper extends BaseMapper<Friend> {
}

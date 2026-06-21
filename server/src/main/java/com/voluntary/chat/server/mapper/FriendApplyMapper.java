package com.voluntary.chat.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voluntary.chat.server.entity.FriendApply;
import org.apache.ibatis.annotations.Mapper;

/**
 * 好友申请 Mapper
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Mapper
public interface FriendApplyMapper extends BaseMapper<FriendApply> {
}

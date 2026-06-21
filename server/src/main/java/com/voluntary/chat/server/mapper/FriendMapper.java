package com.voluntary.chat.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voluntary.chat.server.entity.Friend;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 好友关系 Mapper
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Mapper
public interface FriendMapper extends BaseMapper<Friend> {

    /**
     * 恢复已删除的好友关系（绕过逻辑删除）
     *
     * <p>好友表使用逻辑删除 + 唯一索引 (user_id, friend_id)，
     * 当好友被删除后重新添加时，需先恢复已删除记录，否则唯一索引冲突。</p>
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     * @return 恢复的行数（0 表示无已删除记录）
     */
    @Update("UPDATE friend SET is_deleted = 0 WHERE user_id = #{userId} AND friend_id = #{friendId} AND is_deleted = 1")
    int restoreFriend(@Param("userId") Long userId, @Param("friendId") Long friendId);
}

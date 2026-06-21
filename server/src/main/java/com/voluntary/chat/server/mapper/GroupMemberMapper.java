package com.voluntary.chat.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voluntary.chat.server.entity.GroupMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 群成员 Mapper
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Mapper
public interface GroupMemberMapper extends BaseMapper<GroupMember> {

    /**
     * 查询用户加入的所有群组ID列表
     *
     * @param userId 用户ID
     * @return 群组ID列表
     */
    @Select("SELECT group_id FROM group_member WHERE user_id = #{userId} AND is_deleted = 0")
    List<Long> selectGroupIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询群组的成员数量
     *
     * @param groupId 群组ID
     * @return 成员数量
     */
    @Select("SELECT COUNT(*) FROM group_member WHERE group_id = #{groupId} AND is_deleted = 0")
    int countMembers(@Param("groupId") Long groupId);

    /**
     * 查询用户在群组中的角色
     *
     * @param groupId 群组ID
     * @param userId  用户ID
     * @return 角色（0-普通成员，1-管理员，2-群主），不在群中返回null
     */
    @Select("SELECT role FROM group_member WHERE group_id = #{groupId} AND user_id = #{userId} AND is_deleted = 0")
    Integer selectRoleByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /**
     * 查询群组所有成员的userId列表
     *
     * @param groupId 群组ID
     * @return 成员userId列表
     */
    @Select("SELECT user_id FROM group_member WHERE group_id = #{groupId} AND is_deleted = 0")
    List<Long> selectGroupMemberUserIds(@Param("groupId") Long groupId);
}